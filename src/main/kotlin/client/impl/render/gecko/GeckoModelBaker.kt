package github.kasuminova.prototypemachinery.client.impl.render.gecko

import github.kasuminova.prototypemachinery.api.platform.PMGeckoVertexPipeline
import github.kasuminova.prototypemachinery.api.tuning.RenderTuning
import github.kasuminova.prototypemachinery.client.impl.render.RenderStats
import github.kasuminova.prototypemachinery.client.impl.render.gecko.GeckoModelBaker.bakeRoutedFiltered
import github.kasuminova.prototypemachinery.client.util.MmceMatrixStack
import github.kasuminova.prototypemachinery.impl.platform.PMPlatformManager
import net.minecraft.client.renderer.BufferBuilder
import software.bernie.geckolib3.geo.render.built.GeoBone
import software.bernie.geckolib3.geo.render.built.GeoCube
import software.bernie.geckolib3.geo.render.built.GeoModel
import software.bernie.geckolib3.geo.render.built.GeoQuad
import java.util.WeakHashMap
import javax.vecmath.Vector3f

/**
 * CPU-side baker that converts GeckoLib built models into a [BufferBuilder].
 *
 * This is adapted from GeckoLib's `IGeoRenderer` but:
 * - avoids Tessellator/GlStateManager
 * - uses a per-bake [MmceMatrixStack] (thread-safe)
 */
internal object GeckoModelBaker {

    // ---------------------------------------------------------------------
    // Vertex-count estimation cache (for BufferBuilder pre-sizing)
    // ---------------------------------------------------------------------

    private data class EstimateParams(
        val modeOrdinal: Int,
        val activeKey: Long,
        val potentialKey: Long,
    )

    /**
     * Per-model small LRU cache.
     *
     * IMPORTANT:
     * - Values are only used to pre-size BufferBuilders (performance hint).
     * - Collisions or stale estimates are acceptable (worst case: a growBuffer happens again).
     */
    private class ModelEstimateCache {
        private val lock = Any()

        private val lru: LinkedHashMap<EstimateParams, IntArray> = object : LinkedHashMap<EstimateParams, IntArray>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<EstimateParams, IntArray>?): Boolean {
                return size > 64
            }
        }

        fun getOrCompute(params: EstimateParams, compute: () -> IntArray): IntArray {
            synchronized(lock) {
                val hit = lru[params]
                if (hit != null) return hit
            }

            val computed = compute()
            val stored = computed.copyOf()

            synchronized(lock) {
                val race = lru[params]
                if (race != null) return race
                lru[params] = stored
                return stored
            }
        }
    }

    // Weak keys: on resource reload, old GeoModel instances can be GC'd and their estimate caches will disappear.
    private val estimateCacheLock = Any()
    private val estimateCache: MutableMap<GeoModel, ModelEstimateCache> = WeakHashMap()

    private fun setKey(set: Set<String>): Long {
        // Order-insensitive (commutative) fingerprint; good enough for caching a performance hint.
        var xor = 0L
        var sum = 0L
        for (s in set) {
            // Mix the string hash a bit; keep it stable across runs.
            // Use negative form to stay within signed Long literal range.
            val x = (s.hashCode().toLong() * -0x61C8864680B583EBL)
            xor = xor xor x
            sum += x
        }
        // 0xC2B2AE3D27D4EB4F as signed Long is negative: -0x3D4D51C2D82B14B1.
        return (xor * -0x3D4D51C2D82B14B1L) xor sum xor (set.size.toLong() shl 32)
    }

    /**
     * Estimate vertex counts routed by (bloom, transparent) flags, using the same filtering rules as [bakeRoutedFiltered].
     *
     * Index layout (bitmask):
     * - bit0: bloom
     * - bit1: transparent
     *
     * So:
     * - 0: default
     * - 1: bloom
     * - 2: transparent
     * - 3: bloom+transparent
     */
    internal fun estimateRoutedFilteredVertexCounts(
        model: GeoModel,
        activeAnimatedBones: Set<String>,
        mode: BakeMode,
        potentialAnimatedBones: Set<String> = activeAnimatedBones,
    ): IntArray {
        val params = EstimateParams(
            modeOrdinal = mode.ordinal,
            activeKey = setKey(activeAnimatedBones),
            potentialKey = setKey(potentialAnimatedBones),
        )

        val perModel = synchronized(estimateCacheLock) {
            estimateCache.getOrPut(model) { ModelEstimateCache() }
        }

        return perModel.getOrCompute(params) {
            val out = IntArray(4)
            val top = model.topLevelBones
            var i = 0
            val n = top.size
            while (i < n) {
                estimateRecursively(
                    bone = top[i],
                    potentialAnimatedBones = potentialAnimatedBones,
                    activeAnimatedBones = activeAnimatedBones,
                    mode = mode,
                    bloom = false,
                    transparent = false,
                    parentPotential = false,
                    parentDynamic = false,
                    out = out,
                )
                i++
            }
            out
        }
    }

    private fun estimateRecursively(
        bone: GeoBone,
        potentialAnimatedBones: Set<String>,
        activeAnimatedBones: Set<String>,
        mode: BakeMode,
        bloom: Boolean,
        transparent: Boolean,
        parentPotential: Boolean,
        parentDynamic: Boolean,
        out: IntArray,
    ) {
        val selfPotential = potentialAnimatedBones.contains(bone.name)
        val selfDynamic = activeAnimatedBones.contains(bone.name)

        val inPotentialSubtree = parentPotential || selfPotential
        val inDynamicSubtree = parentDynamic || selfDynamic

        when (mode) {
            BakeMode.PERMANENT_STATIC_ONLY -> {
                if (inPotentialSubtree) return
            }

            BakeMode.TEMP_STATIC_ONLY -> {
                if (!inPotentialSubtree) return
                if (inDynamicSubtree) return
            }

            BakeMode.ANIMATED_ONLY -> {
                // still traverse; rendering gated below
            }

            BakeMode.ALL -> Unit
        }

        // Naming conventions (match renderRecursivelyRoutedFiltered)
        var bloomNow = bloom
        var transparentNow = transparent
        val name = bone.name
        if (name.startsWith("emissive") || name.startsWith("bloom")) {
            bloomNow = true
        }
        if (
            name.startsWith("transparent") ||
            name.startsWith("emissive_transparent") ||
            name.startsWith("bloom_transparent")
        ) {
            transparentNow = true
        }

        val shouldRenderBone = when (mode) {
            BakeMode.PERMANENT_STATIC_ONLY -> true
            BakeMode.TEMP_STATIC_ONLY -> true
            BakeMode.ANIMATED_ONLY -> inDynamicSubtree
            BakeMode.ALL -> true
        }

        if (shouldRenderBone && !bone.isHidden) {
            val idx = (if (bloomNow) 1 else 0) or (if (transparentNow) 2 else 0)
            val cubes = bone.childCubes
            var ci = 0
            val cn = cubes.size
            while (ci < cn) {
                val cube = cubes[ci]
                val quads = cube.quads
                var qi = 0
                val qn = quads.size
                while (qi < qn) {
                    val q = quads[qi]
                    if (q != null) {
                        // Usually 4 (GL_QUADS). Keep it generic to stay correct for odd inputs.
                        out[idx] += q.vertices.size
                    }
                    qi++
                }
                ci++
            }
        }

        if (!bone.childBonesAreHiddenToo()) {
            val children = bone.childBones
            var bi = 0
            val bn = children.size
            while (bi < bn) {
                estimateRecursively(
                    bone = children[bi],
                    potentialAnimatedBones = potentialAnimatedBones,
                    activeAnimatedBones = activeAnimatedBones,
                    mode = mode,
                    bloom = bloomNow,
                    transparent = transparentNow,
                    parentPotential = inPotentialSubtree,
                    parentDynamic = inDynamicSubtree,
                    out = out,
                )
                bi++
            }
        }
    }

    internal enum class BakeMode {
        /** Bake all bones. */
        ALL,

        /**
         * Bake only bones that are *never* affected by any animation.
         *
         * A bone is considered "potentially animated" if it is referenced by *any* animation in the file
         * (or is under such a bone).
         */
        PERMANENT_STATIC_ONLY,

        /**
         * Bake only bones that are potentially animated (appear in some animation in the file),
         * but are not affected by the currently selected animation(s).
         *
         * This is the "temporary static" layer used to reduce animation-switch rebuild cost.
         */
        TEMP_STATIC_ONLY,

        /** Bake only bones that are affected by animation (animated bone or has animated ancestor). */
        ANIMATED_ONLY,
    }

    internal fun bake(
        model: GeoModel,
        builder: BufferBuilder,
        matrixStack: MmceMatrixStack,
        red: Float = 1.0f,
        green: Float = 1.0f,
        blue: Float = 1.0f,
        alpha: Float = 1.0f,
    ) {
        val scratch = Scratch()
        val top = model.topLevelBones
        var i = 0
        val n = top.size
        while (i < n) {
            renderRecursively(builder, matrixStack, top[i], red, green, blue, alpha, scratch)
            i++
        }
    }

    /**
     * Bake a model while routing geometry into different buffers based on bone name.
     *
     * This follows MMCE's convention:
     * - bloom/emissive: bone name starts with "bloom" or "emissive" -> bloom flag
     * - transparent: bone name starts with "transparent" or "emissive_transparent" or "bloom_transparent" -> transparent flag
     *
     * The caller decides how (bloom, transparent) maps to an actual [BufferBuilder].
     */
    internal fun bakeRouted(
        model: GeoModel,
        matrixStack: MmceMatrixStack,
        bufferSelector: (bloom: Boolean, transparent: Boolean) -> BufferBuilder,
        red: Float = 1.0f,
        green: Float = 1.0f,
        blue: Float = 1.0f,
        alpha: Float = 1.0f,
    ) {
        val scratch = Scratch()
        val top = model.topLevelBones
        var i = 0
        val n = top.size
        while (i < n) {
            renderRecursivelyRouted(
                ms = matrixStack,
                bone = top[i],
                bufferSelector = bufferSelector,
                red = red,
                green = green,
                blue = blue,
                alpha = alpha,
                bloom = false,
                transparent = false,
                scratch = scratch,
            )
            i++
        }
    }

    /**
     * Bake a model while filtering bones based on animation influence.
     *
     * This is used to split a model into two independent buffers:
     * - static: geometry not affected by animation
     * - dynamic: geometry affected by animation
     */
    internal fun bakeRoutedFiltered(
        model: GeoModel,
        matrixStack: MmceMatrixStack,
        bufferSelector: (bloom: Boolean, transparent: Boolean) -> BufferBuilder,
        activeAnimatedBones: Set<String>,
        mode: BakeMode,
        potentialAnimatedBones: Set<String> = activeAnimatedBones,
        red: Float = 1.0f,
        green: Float = 1.0f,
        blue: Float = 1.0f,
        alpha: Float = 1.0f,
    ) {
        if (mode == BakeMode.ALL) {
            bakeRouted(
                model = model,
                matrixStack = matrixStack,
                bufferSelector = bufferSelector,
                red = red,
                green = green,
                blue = blue,
                alpha = alpha,
            )
            return
        }

        val scratch = Scratch()
        val top = model.topLevelBones
        var i = 0
        val n = top.size
        while (i < n) {
            renderRecursivelyRoutedFiltered(
                ms = matrixStack,
                bone = top[i],
                bufferSelector = bufferSelector,
                potentialAnimatedBones = potentialAnimatedBones,
                activeAnimatedBones = activeAnimatedBones,
                mode = mode,
                red = red,
                green = green,
                blue = blue,
                alpha = alpha,
                bloom = false,
                transparent = false,
                parentPotential = false,
                parentDynamic = false,
                scratch = scratch,
            )
            i++
        }
    }

    private class Scratch(
        val normal: Vector3f = Vector3f(),
        val quadVertexData: IntArray = IntArray(4 * 7),
    ) {
        // SoA scratch (resized as needed) for cube-level batching.
        var xs: FloatArray = FloatArray(0)
        var ys: FloatArray = FloatArray(0)
        var zs: FloatArray = FloatArray(0)

        // AoS packed output (MUST be exact length when passed to BufferBuilder.addVertexData).
        var packed: IntArray = IntArray(0)

        // Pre-baked cube references (for bone-level batching with cache).
        var bakedCubes: Array<BakedCubeCache.BakedCube?> = emptyArray()

        fun ensureVertexCapacity(vertexCount: Int) {
            if (vertexCount <= 0) return
            if (xs.size >= vertexCount) return

            var cap = xs.size
            if (cap < 16) cap = 16
            while (cap < vertexCount) cap = cap shl 1

            xs = FloatArray(cap)
            ys = FloatArray(cap)
            zs = FloatArray(cap)
        }

        fun ensurePackedExactIntCount(intCount: Int) {
            if (intCount <= 0) {
                packed = IntArray(0)
                return
            }
            if (packed.size != intCount) {
                packed = IntArray(intCount)
            }
        }

        fun ensureBakedCubeCapacity(cubeCount: Int) {
            if (cubeCount <= 0) return
            if (bakedCubes.size >= cubeCount) return

            var cap = bakedCubes.size
            if (cap < 8) cap = 8
            while (cap < cubeCount) cap = cap shl 1

            bakedCubes = arrayOfNulls(cap)
        }
    }

    /**
     * Optional high-perf pipeline, provided by the active PMPlatform (e.g. modern-backend on Java 21+).
     *
     * Legacy platform returns null.
     */
    private val geckoVertexPipeline: PMGeckoVertexPipeline? by lazy(LazyThreadSafetyMode.NONE) {
        PMPlatformManager.get().geckoVertexPipeline()
    }

    /**
     * Check if a cube has identity rotation (no cube-local rotation).
     */
    private fun isCubeRotationIdentity(cube: GeoCube): Boolean {
        // GeoCube.rotation is a public Vector3f in GeckoLib 3.0.31 (platform type, might be null in theory).
        val r = cube.rotation ?: return false
        // Values are typically exactly 0f when authoring indicates no rotation.
        return r.x == 0.0f && r.y == 0.0f && r.z == 0.0f
    }

    /**
     * Check if a cube can be batched without needing pre-bake cache.
     * When pre-bake is enabled, all cubes can be batched.
     * When disabled, only identity-rotation cubes can be batched.
     */
    private fun canBatchWithoutCache(cube: GeoCube): Boolean {
        return isCubeRotationIdentity(cube)
    }

    private fun renderRecursively(
        builder: BufferBuilder,
        ms: MmceMatrixStack,
        bone: GeoBone,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        scratch: Scratch,
    ) {
        ms.push()

        ms.translate(bone)
        ms.moveToPivot(bone)
        ms.rotate(bone)
        ms.scale(bone)
        ms.moveBackFromPivot(bone)

        if (!bone.isHidden) {
            val cubes = bone.childCubes
            val cn = cubes.size

            // Pre-bake cache path: all cubes can be batched because cube-local transforms are pre-applied.
            if (RenderTuning.geckoCubePreBake && RenderTuning.geckoBulkQuadWrite && cn > 0) {
                renderBoneWithPreBakedCubes(builder, ms, cubes, red, green, blue, alpha, scratch)
            } else {
                // Legacy path: only identity-rotation cubes can be batched.
                var ci = 0
                while (ci < cn) {
                    val cube = cubes[ci]

                    // Experimental: bone-level batching for contiguous identity-rotation cubes.
                    if (RenderTuning.geckoBoneBatching && RenderTuning.geckoBulkQuadWrite && isCubeRotationIdentity(cube)) {
                        val minVertices = RenderTuning.geckoBoneBatchingMinVertices

                        // Extend a contiguous run of identity-rotation cubes, but stop on unexpected topology.
                        var runEnd = ci
                        var quadCount = 0
                        var vertexCount = 0
                        run@ while (runEnd < cn) {
                            val c = cubes[runEnd]
                            if (!isCubeRotationIdentity(c)) break

                            val quads = c.quads
                            var localQuads = 0
                            var qi = 0
                            val qn = quads.size
                            while (qi < qn) {
                                val q = quads[qi]
                                if (q != null) {
                                    if (q.vertices.size != 4) {
                                        // Keep strict ordering correctness: stop the run here.
                                        break@run
                                    }
                                    localQuads++
                                }
                                qi++
                            }
                            if (localQuads > 0) {
                                quadCount += localQuads
                                vertexCount += localQuads * 4
                            }
                            runEnd++
                        }

                        if (vertexCount > 0 && vertexCount >= minVertices) {
                            val batched = renderCubeRunSameMatrix(
                                builder = builder,
                                ms = ms,
                                cubes = cubes,
                                startInclusive = ci,
                                endExclusive = runEnd,
                                quadCount = quadCount,
                                vertexCount = vertexCount,
                                red = red,
                                green = green,
                                blue = blue,
                                alpha = alpha,
                                scratch = scratch,
                            )
                            if (batched) {
                                ci = runEnd
                                continue
                            }
                        }

                        // Too small for batching (or batch failed): still take the cheaper identity-cube path.
                        renderCubeNoLocalTransform(builder, ms, cube, red, green, blue, alpha, scratch)
                        ci++
                        continue
                    }

                    ms.push()
                    renderCube(builder, ms, cube, red, green, blue, alpha, scratch)
                    ms.pop()
                    ci++
                }
            }
        }

        if (!bone.childBonesAreHiddenToo()) {
            val children = bone.childBones
            var bi = 0
            val bn = children.size
            while (bi < bn) {
                renderRecursively(builder, ms, children[bi], red, green, blue, alpha, scratch)
                bi++
            }
        }

        ms.pop()
    }

    private fun renderRecursivelyRouted(
        ms: MmceMatrixStack,
        bone: GeoBone,
        bufferSelector: (bloom: Boolean, transparent: Boolean) -> BufferBuilder,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        bloom: Boolean,
        transparent: Boolean,
        scratch: Scratch,
    ) {
        // MMCE-compatible naming conventions
        var bloomNow = bloom
        var transparentNow = transparent
        val name = bone.name
        if (name.startsWith("emissive") || name.startsWith("bloom")) {
            bloomNow = true
        }
        if (
            name.startsWith("transparent") ||
            name.startsWith("emissive_transparent") ||
            name.startsWith("bloom_transparent")
        ) {
            transparentNow = true
        }

        ms.push()

        ms.translate(bone)
        ms.moveToPivot(bone)
        ms.rotate(bone)
        ms.scale(bone)
        ms.moveBackFromPivot(bone)

        if (!bone.isHidden) {
            val cubes = bone.childCubes
            val builder = bufferSelector(bloomNow, transparentNow)
            val cn = cubes.size

            // Pre-bake cache path: all cubes can be batched because cube-local transforms are pre-applied.
            if (RenderTuning.geckoCubePreBake && RenderTuning.geckoBulkQuadWrite && cn > 0) {
                renderBoneWithPreBakedCubes(builder, ms, cubes, red, green, blue, alpha, scratch)
            } else {
                // Legacy path: only identity-rotation cubes can be batched.
                var ci = 0
                while (ci < cn) {
                    val cube = cubes[ci]

                    if (RenderTuning.geckoBoneBatching && RenderTuning.geckoBulkQuadWrite && isCubeRotationIdentity(cube)) {
                        val minVertices = RenderTuning.geckoBoneBatchingMinVertices

                        var runEnd = ci
                        var quadCount = 0
                        var vertexCount = 0
                        run@ while (runEnd < cn) {
                            val c = cubes[runEnd]
                            if (!isCubeRotationIdentity(c)) break

                            val quads = c.quads
                            var localQuads = 0
                            var qi = 0
                            val qn = quads.size
                            while (qi < qn) {
                                val q = quads[qi]
                                if (q != null) {
                                    if (q.vertices.size != 4) break@run
                                    localQuads++
                                }
                                qi++
                            }
                            if (localQuads > 0) {
                                quadCount += localQuads
                                vertexCount += localQuads * 4
                            }
                            runEnd++
                        }

                        if (vertexCount > 0 && vertexCount >= minVertices) {
                            val batched = renderCubeRunSameMatrix(
                                builder = builder,
                                ms = ms,
                                cubes = cubes,
                                startInclusive = ci,
                                endExclusive = runEnd,
                                quadCount = quadCount,
                                vertexCount = vertexCount,
                                red = red,
                                green = green,
                                blue = blue,
                                alpha = alpha,
                                scratch = scratch,
                            )
                            if (batched) {
                                ci = runEnd
                                continue
                            }
                        }

                        renderCubeNoLocalTransform(builder, ms, cube, red, green, blue, alpha, scratch)
                        ci++
                        continue
                    }

                    ms.push()
                    renderCube(builder, ms, cube, red, green, blue, alpha, scratch)
                    ms.pop()
                    ci++
                }
            }
        }

        if (!bone.childBonesAreHiddenToo()) {
            val children = bone.childBones
            var bi = 0
            val bn = children.size
            while (bi < bn) {
                renderRecursivelyRouted(
                    ms = ms,
                    bone = children[bi],
                    bufferSelector = bufferSelector,
                    red = red,
                    green = green,
                    blue = blue,
                    alpha = alpha,
                    bloom = bloomNow,
                    transparent = transparentNow,
                    scratch = scratch,
                )
                bi++
            }
        }

        ms.pop()
    }

    private fun renderRecursivelyRoutedFiltered(
        ms: MmceMatrixStack,
        bone: GeoBone,
        bufferSelector: (bloom: Boolean, transparent: Boolean) -> BufferBuilder,
        potentialAnimatedBones: Set<String>,
        activeAnimatedBones: Set<String>,
        mode: BakeMode,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        bloom: Boolean,
        transparent: Boolean,
        parentPotential: Boolean,
        parentDynamic: Boolean,
        scratch: Scratch,
    ) {
        val selfPotential = potentialAnimatedBones.contains(bone.name)
        val selfDynamic = activeAnimatedBones.contains(bone.name)

        val inPotentialSubtree = parentPotential || selfPotential
        val inDynamicSubtree = parentDynamic || selfDynamic

        when (mode) {
            BakeMode.PERMANENT_STATIC_ONLY -> {
                // Any potentially-animated bone makes the whole subtree non-permanent.
                if (inPotentialSubtree) return
            }

            BakeMode.TEMP_STATIC_ONLY -> {
                // We only care about bones under potentially animated roots.
                if (!inPotentialSubtree) return
                // If a bone is under a currently animated root, the whole subtree is dynamic.
                if (inDynamicSubtree) return
            }

            BakeMode.ANIMATED_ONLY -> {
                // We still need to traverse to reach potentially animated descendants.
                // Rendering is gated further below.
            }

            BakeMode.ALL -> Unit
        }

        // MMCE-compatible naming conventions
        var bloomNow = bloom
        var transparentNow = transparent
        val name = bone.name
        if (name.startsWith("emissive") || name.startsWith("bloom")) {
            bloomNow = true
        }
        if (
            name.startsWith("transparent") ||
            name.startsWith("emissive_transparent") ||
            name.startsWith("bloom_transparent")
        ) {
            transparentNow = true
        }

        ms.push()

        ms.translate(bone)
        ms.moveToPivot(bone)
        ms.rotate(bone)
        ms.scale(bone)
        ms.moveBackFromPivot(bone)

        val shouldRenderBone = when (mode) {
            BakeMode.PERMANENT_STATIC_ONLY -> true
            BakeMode.TEMP_STATIC_ONLY -> true
            BakeMode.ANIMATED_ONLY -> inDynamicSubtree
            BakeMode.ALL -> true
        }

        if (shouldRenderBone && !bone.isHidden) {
            val cubes = bone.childCubes
            val builder = bufferSelector(bloomNow, transparentNow)
            val cn = cubes.size

            // Pre-bake cache path: all cubes can be batched because cube-local transforms are pre-applied.
            if (RenderTuning.geckoCubePreBake && RenderTuning.geckoBulkQuadWrite && cn > 0) {
                renderBoneWithPreBakedCubes(builder, ms, cubes, red, green, blue, alpha, scratch)
            } else {
                // Legacy path: only identity-rotation cubes can be batched.
                var ci = 0
                while (ci < cn) {
                    val cube = cubes[ci]

                    if (RenderTuning.geckoBoneBatching && RenderTuning.geckoBulkQuadWrite && isCubeRotationIdentity(cube)) {
                        val minVertices = RenderTuning.geckoBoneBatchingMinVertices

                        var runEnd = ci
                        var quadCount = 0
                        var vertexCount = 0
                        run@ while (runEnd < cn) {
                            val c = cubes[runEnd]
                            if (!isCubeRotationIdentity(c)) break

                            val quads = c.quads
                            var localQuads = 0
                            var qi = 0
                            val qn = quads.size
                            while (qi < qn) {
                                val q = quads[qi]
                                if (q != null) {
                                    if (q.vertices.size != 4) break@run
                                    localQuads++
                                }
                                qi++
                            }
                            if (localQuads > 0) {
                                quadCount += localQuads
                                vertexCount += localQuads * 4
                            }
                            runEnd++
                        }

                        if (vertexCount > 0 && vertexCount >= minVertices) {
                            val batched = renderCubeRunSameMatrix(
                                builder = builder,
                                ms = ms,
                            cubes = cubes,
                            startInclusive = ci,
                            endExclusive = runEnd,
                            quadCount = quadCount,
                            vertexCount = vertexCount,
                            red = red,
                            green = green,
                            blue = blue,
                            alpha = alpha,
                            scratch = scratch,
                        )
                        if (batched) {
                            ci = runEnd
                            continue
                        }
                    }

                    renderCubeNoLocalTransform(builder, ms, cube, red, green, blue, alpha, scratch)
                    ci++
                    continue
                }

                ms.push()
                renderCube(builder, ms, cube, red, green, blue, alpha, scratch)
                ms.pop()
                ci++
            }
            }
        }

        if (!bone.childBonesAreHiddenToo()) {
            val children = bone.childBones
            var bi = 0
            val bn = children.size
            while (bi < bn) {
                renderRecursivelyRoutedFiltered(
                    ms = ms,
                    bone = children[bi],
                    bufferSelector = bufferSelector,
                    potentialAnimatedBones = potentialAnimatedBones,
                    activeAnimatedBones = activeAnimatedBones,
                    mode = mode,
                    red = red,
                    green = green,
                    blue = blue,
                    alpha = alpha,
                    bloom = bloomNow,
                    transparent = transparentNow,
                    parentPotential = inPotentialSubtree,
                    parentDynamic = inDynamicSubtree,
                    scratch = scratch,
                )
                bi++
            }
        }

        ms.pop()
    }

    private fun renderQuad(
        builder: BufferBuilder,
        quad: GeoQuad,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        scratch: Scratch,
        flatFlags: Int,
        m00: Float,
        m01: Float,
        m02: Float,
        m03: Float,
        m10: Float,
        m11: Float,
        m12: Float,
        m13: Float,
        m20: Float,
        m21: Float,
        m22: Float,
        m23: Float,
        n00: Float,
        n01: Float,
        n02: Float,
        n10: Float,
        n11: Float,
        n12: Float,
        n20: Float,
        n21: Float,
        n22: Float,
    ) {
        val t0 = if (RenderStats.enabled) System.nanoTime() else 0L

        // Apply normal matrix (manual, hoisted matrix reads at cube-level).
        val qnx = quad.normal.x.toFloat()
        val qny = quad.normal.y.toFloat()
        val qnz = quad.normal.z.toFloat()

        var nx = n00 * qnx + n01 * qny + n02 * qnz
        var ny = n10 * qnx + n11 * qny + n12 * qnz
        var nz = n20 * qnx + n21 * qny + n22 * qnz

        // Fix dark shading for flat cubes (mirrors GeckoLib behavior)
        if ((flatFlags and 1) != 0 && nx < 0) nx = -nx
        if ((flatFlags and 2) != 0 && ny < 0) ny = -ny
        if ((flatFlags and 4) != 0 && nz < 0) nz = -nz

        // Convert constant bake-tint once per quad.
        // We'll pack RGBA into the same int layout used by MC's baked quads:
        // bytes in memory are [r,g,b,a] -> int bits become A<<24 | B<<16 | G<<8 | R (little-endian).
        val packedColor = packColor(red, green, blue, alpha)
        // Legacy (non-bulk) path still needs separate RGBA bytes for BufferBuilder.color(r,g,b,a).
        val rI = packedColor and 0xFF
        val gI = (packedColor ushr 8) and 0xFF
        val bI = (packedColor ushr 16) and 0xFF
        val aI = (packedColor ushr 24) and 0xFF

        // Pack normal into 3 signed bytes (x,y,z) + 1 pad byte.
        val packedNormal = packNormal(nx, ny, nz)

        if (!RenderTuning.geckoBulkQuadWrite) {
            // Explicit fallback for A/B profiling.
            val verts = quad.vertices
            RenderStats.addGeckoLegacyQuad(verts.size)

            var vi = 0
            val vn = verts.size
            while (vi < vn) {
                val vertex = verts[vi]

                val pos = vertex.position
                val x = pos.x
                val y = pos.y
                val z = pos.z

                val px = m00 * x + m01 * y + m02 * z + m03
                val py = m10 * x + m11 * y + m12 * z + m13
                val pz = m20 * x + m21 * y + m22 * z + m23

                val u = vertex.textureU.toDouble()
                val v = vertex.textureV.toDouble()

                builder
                    .pos(px.toDouble(), py.toDouble(), pz.toDouble())
                    .tex(u, v)
                    .color(rI, gI, bI, aI)
                    .normal(nx, ny, nz)
                    .endVertex()
                vi++
            }
            if (t0 != 0L) {
                RenderStats.addGeckoQuadLegacyNanos(System.nanoTime() - t0, vn)
            }
            return
        }

        // Hot-path reduction: build a 4-vertex (28-int) quad and submit once.
        // This avoids per-vertex BufferBuilder.color()/endVertex() overhead.
        val data = scratch.quadVertexData

        val verts = quad.vertices
        val vn = verts.size

        // GeoQuad is expected to be 4-vertex quads. Guard just in case.
        if (vn != 4) {
            RenderStats.addGeckoFallbackQuad(vn)
            var vi = 0
            while (vi < vn) {
                val vertex = verts[vi]

                val pos = vertex.position
                val x = pos.x
                val y = pos.y
                val z = pos.z

                val px = m00 * x + m01 * y + m02 * z + m03
                val py = m10 * x + m11 * y + m12 * z + m13
                val pz = m20 * x + m21 * y + m22 * z + m23

                val u = vertex.textureU.toDouble()
                val v = vertex.textureV.toDouble()

                builder
                    .pos(px.toDouble(), py.toDouble(), pz.toDouble())
                    .tex(u, v)
                    .color(rI, gI, bI, aI)
                    .normal(nx, ny, nz)
                    .endVertex()
                vi++
            }
            if (t0 != 0L) {
                RenderStats.addGeckoQuadLegacyNanos(System.nanoTime() - t0, vn)
            }
            return
        }

        RenderStats.addGeckoBulkQuad()

        var vi = 0
        while (vi < 4) {
            val vertex = verts[vi]

            val pos = vertex.position
            val x = pos.x
            val y = pos.y
            val z = pos.z

            val px = m00 * x + m01 * y + m02 * z + m03
            val py = m10 * x + m11 * y + m12 * z + m13
            val pz = m20 * x + m21 * y + m22 * z + m23

            val u = vertex.textureU.toFloat()
            val v = vertex.textureV.toFloat()

            val base = vi * 7
            data[base + 0] = java.lang.Float.floatToRawIntBits(px)
            data[base + 1] = java.lang.Float.floatToRawIntBits(py)
            data[base + 2] = java.lang.Float.floatToRawIntBits(pz)
            data[base + 3] = java.lang.Float.floatToRawIntBits(u)
            data[base + 4] = java.lang.Float.floatToRawIntBits(v)
            data[base + 5] = packedColor
            data[base + 6] = packedNormal
            vi++
        }

        builder.addVertexData(data)

        if (t0 != 0L) {
            RenderStats.addGeckoQuadBulkNanos(System.nanoTime() - t0, 4)
        }
    }

    private inline fun floatToColorByte(v: Float): Int {
        // Match vanilla's behavior: clamp [0,1] then scale to [0,255].
        // We deliberately avoid kotlin.math.roundToInt to keep this tiny & allocation-free.
        val clamped = when {
            v <= 0.0f -> 0.0f
            v >= 1.0f -> 1.0f
            else -> v
        }
        return (clamped * 255.0f + 0.5f).toInt()
    }

    private inline fun packColor(red: Float, green: Float, blue: Float, alpha: Float): Int {
        // Match vanilla RGBA packing used by BufferBuilder.
        val rI = floatToColorByte(red)
        val gI = floatToColorByte(green)
        val bI = floatToColorByte(blue)
        val aI = floatToColorByte(alpha)
        return (aI shl 24) or (bI shl 16) or (gI shl 8) or rI
    }

    private inline fun packNormal(nx: Float, ny: Float, nz: Float): Int {
        // Match BufferBuilder.normal: scale [-1,1] to signed byte [-127,127] (clamped), then pack.
        val x = floatToNormalByte(nx)
        val y = floatToNormalByte(ny)
        val z = floatToNormalByte(nz)
        return (x and 0xFF) or ((y and 0xFF) shl 8) or ((z and 0xFF) shl 16)
    }

    private inline fun floatToNormalByte(v: Float): Int {
        val clamped = when {
            v <= -1.0f -> -1.0f
            v >= 1.0f -> 1.0f
            else -> v
        }
        // Using +0.5f / -0.5f for symmetric rounding around 0.
        val scaled = clamped * 127.0f
        return if (scaled >= 0.0f) (scaled + 0.5f).toInt() else (scaled - 0.5f).toInt()
    }

    private inline fun transformAndPackNormalWithFlatFlags(
        n00: Float,
        n01: Float,
        n02: Float,
        n10: Float,
        n11: Float,
        n12: Float,
        n20: Float,
        n21: Float,
        n22: Float,
        nx: Float,
        ny: Float,
        nz: Float,
        flatFlags: Int,
    ): Int {
        var tx = n00 * nx + n01 * ny + n02 * nz
        var ty = n10 * nx + n11 * ny + n12 * nz
        var tz = n20 * nx + n21 * ny + n22 * nz

        // Flat-cube shading fix (mirrors GeckoLib behavior), using baked per-quad flags.
        if ((flatFlags and 1) != 0 && tx < 0) tx = -tx
        if ((flatFlags and 2) != 0 && ty < 0) ty = -ty
        if ((flatFlags and 4) != 0 && tz < 0) tz = -tz

        return packNormal(tx, ty, tz)
    }

    private inline fun flatFlagsForCube(cube: GeoCube): Int {
        // Must match BakedCubeCache's flags to keep shading consistent between pre-baked and non-pre-baked paths.
        val s = cube.size
        var flags = 0
        if (s.y == 0f || s.z == 0f) flags = flags or 1
        if (s.x == 0f || s.z == 0f) flags = flags or 2
        if (s.x == 0f || s.y == 0f) flags = flags or 4
        return flags
    }
    /**
     * Split out for profiling: apply normal matrix to [normal] in-place.
     */
    private fun applyNormalMatrix(ms: MmceMatrixStack, normal: Vector3f) {
        // Manual normal matrix multiplication (avoid allocations / virtual calls).
        val n = ms.normalMatrix
        val n00 = n.m00
        val n01 = n.m01
        val n02 = n.m02
        val n10 = n.m10
        val n11 = n.m11
        val n12 = n.m12
        val n20 = n.m20
        val n21 = n.m21
        val n22 = n.m22

        val nx = normal.x
        val ny = normal.y
        val nz = normal.z

        normal.x = n00 * nx + n01 * ny + n02 * nz
        normal.y = n10 * nx + n11 * ny + n12 * nz
        normal.z = n20 * nx + n21 * ny + n22 * nz
    }

    private fun renderCube(
        builder: BufferBuilder,
        ms: MmceMatrixStack,
        cube: GeoCube,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        scratch: Scratch,
    ) {
        ms.moveToPivot(cube)
        ms.rotate(cube)
        ms.moveBackFromPivot(cube)

        // Hoist matrices once per cube.
        val mm = ms.modelMatrix
        val m00 = mm.m00
        val m01 = mm.m01
        val m02 = mm.m02
        val m03 = mm.m03
        val m10 = mm.m10
        val m11 = mm.m11
        val m12 = mm.m12
        val m13 = mm.m13
        val m20 = mm.m20
        val m21 = mm.m21
        val m22 = mm.m22
        val m23 = mm.m23

        val nn = ms.normalMatrix
        val n00 = nn.m00
        val n01 = nn.m01
        val n02 = nn.m02
        val n10 = nn.m10
        val n11 = nn.m11
        val n12 = nn.m12
        val n20 = nn.m20
        val n21 = nn.m21
        val n22 = nn.m22

        // Java21+ path (modern-backend): batch all cube quads into SoA arrays, transform+pack, then submit once.
        if (
            RenderTuning.geckoBulkQuadWrite &&
            RenderTuning.geckoModernVertexPipeline &&
            geckoVertexPipeline != null
        ) {
            if (renderCubeModernPipeline(
                    builder = builder,
                    cube = cube,
                    red = red,
                    green = green,
                    blue = blue,
                    alpha = alpha,
                    scratch = scratch,
                    m00 = m00,
                    m01 = m01,
                    m02 = m02,
                    m03 = m03,
                    m10 = m10,
                    m11 = m11,
                    m12 = m12,
                    m13 = m13,
                    m20 = m20,
                    m21 = m21,
                    m22 = m22,
                    m23 = m23,
                    n00 = n00,
                    n01 = n01,
                    n02 = n02,
                    n10 = n10,
                    n11 = n11,
                    n12 = n12,
                    n20 = n20,
                    n21 = n21,
                    n22 = n22,
                )
            ) {
                return
            }
        }

        // Pure Kotlin bulk path (no modern-backend required): pack the whole cube to int[] and submit once.
        if (RenderTuning.geckoBulkQuadWrite) {
            if (renderCubeBulkPacked(
                    builder = builder,
                    cube = cube,
                    red = red,
                    green = green,
                    blue = blue,
                    alpha = alpha,
                    scratch = scratch,
                    m00 = m00,
                    m01 = m01,
                    m02 = m02,
                    m03 = m03,
                    m10 = m10,
                    m11 = m11,
                    m12 = m12,
                    m13 = m13,
                    m20 = m20,
                    m21 = m21,
                    m22 = m22,
                    m23 = m23,
                    n00 = n00,
                    n01 = n01,
                    n02 = n02,
                    n10 = n10,
                    n11 = n11,
                    n12 = n12,
                    n20 = n20,
                    n21 = n21,
                    n22 = n22,
                )
            ) {
                return
            }
        }

        val quads = cube.quads
        val cubeFlatFlags = flatFlagsForCube(cube)
        var qi = 0
        val qn = quads.size
        while (qi < qn) {
            val quad = quads[qi]
            if (quad != null) {
                renderQuad(
                    builder = builder,
                    quad = quad,
                    red = red,
                    green = green,
                    blue = blue,
                    alpha = alpha,
                    scratch = scratch,
                    flatFlags = cubeFlatFlags,
                    m00 = m00,
                    m01 = m01,
                    m02 = m02,
                    m03 = m03,
                    m10 = m10,
                    m11 = m11,
                    m12 = m12,
                    m13 = m13,
                    m20 = m20,
                    m21 = m21,
                    m22 = m22,
                    m23 = m23,
                    n00 = n00,
                    n01 = n01,
                    n02 = n02,
                    n10 = n10,
                    n11 = n11,
                    n12 = n12,
                    n20 = n20,
                    n21 = n21,
                    n22 = n22,
                )
            }
            qi++
        }
    }

    /**
     * For identity-rotation cubes under a bone: we can skip cube-local pivot/rotate/back and render directly
     * with the current bone matrix. This preserves draw order and avoids extra matrix-stack work.
     */
    private fun renderCubeNoLocalTransform(
        builder: BufferBuilder,
        ms: MmceMatrixStack,
        cube: GeoCube,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        scratch: Scratch,
    ) {
        // Hoist matrices once per cube.
        val mm = ms.modelMatrix
        val m00 = mm.m00
        val m01 = mm.m01
        val m02 = mm.m02
        val m03 = mm.m03
        val m10 = mm.m10
        val m11 = mm.m11
        val m12 = mm.m12
        val m13 = mm.m13
        val m20 = mm.m20
        val m21 = mm.m21
        val m22 = mm.m22
        val m23 = mm.m23

        val nn = ms.normalMatrix
        val n00 = nn.m00
        val n01 = nn.m01
        val n02 = nn.m02
        val n10 = nn.m10
        val n11 = nn.m11
        val n12 = nn.m12
        val n20 = nn.m20
        val n21 = nn.m21
        val n22 = nn.m22

        if (
            RenderTuning.geckoBulkQuadWrite &&
            RenderTuning.geckoModernVertexPipeline &&
            geckoVertexPipeline != null
        ) {
            if (renderCubeModernPipeline(
                    builder = builder,
                    cube = cube,
                    red = red,
                    green = green,
                    blue = blue,
                    alpha = alpha,
                    scratch = scratch,
                    m00 = m00,
                    m01 = m01,
                    m02 = m02,
                    m03 = m03,
                    m10 = m10,
                    m11 = m11,
                    m12 = m12,
                    m13 = m13,
                    m20 = m20,
                    m21 = m21,
                    m22 = m22,
                    m23 = m23,
                    n00 = n00,
                    n01 = n01,
                    n02 = n02,
                    n10 = n10,
                    n11 = n11,
                    n12 = n12,
                    n20 = n20,
                    n21 = n21,
                    n22 = n22,
                )
            ) {
                return
            }
        }

        if (RenderTuning.geckoBulkQuadWrite) {
            if (renderCubeBulkPacked(
                    builder = builder,
                    cube = cube,
                    red = red,
                    green = green,
                    blue = blue,
                    alpha = alpha,
                    scratch = scratch,
                    m00 = m00,
                    m01 = m01,
                    m02 = m02,
                    m03 = m03,
                    m10 = m10,
                    m11 = m11,
                    m12 = m12,
                    m13 = m13,
                    m20 = m20,
                    m21 = m21,
                    m22 = m22,
                    m23 = m23,
                    n00 = n00,
                    n01 = n01,
                    n02 = n02,
                    n10 = n10,
                    n11 = n11,
                    n12 = n12,
                    n20 = n20,
                    n21 = n21,
                    n22 = n22,
                )
            ) {
                return
            }
        }

        val quads = cube.quads
        val cubeFlatFlags = flatFlagsForCube(cube)
        var qi = 0
        val qn = quads.size
        while (qi < qn) {
            val quad = quads[qi]
            if (quad != null) {
                renderQuad(
                    builder = builder,
                    quad = quad,
                    red = red,
                    green = green,
                    blue = blue,
                    alpha = alpha,
                    scratch = scratch,
                    flatFlags = cubeFlatFlags,
                    m00 = m00,
                    m01 = m01,
                    m02 = m02,
                    m03 = m03,
                    m10 = m10,
                    m11 = m11,
                    m12 = m12,
                    m13 = m13,
                    m20 = m20,
                    m21 = m21,
                    m22 = m22,
                    m23 = m23,
                    n00 = n00,
                    n01 = n01,
                    n02 = n02,
                    n10 = n10,
                    n11 = n11,
                    n12 = n12,
                    n20 = n20,
                    n21 = n21,
                    n22 = n22,
                )
            }
            qi++
        }
    }

    /**
     * Render all cubes under a bone using pre-baked cache.
     *
     * Cube-local transforms are already applied in the cache, so all cubes
     * share the current bone matrix and can be batched together.
     */
    private fun renderBoneWithPreBakedCubes(
        builder: BufferBuilder,
        ms: MmceMatrixStack,
        cubes: List<GeoCube>,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        scratch: Scratch,
    ) {
        // IMPORTANT: preserve draw order.
        // We batch only contiguous runs of cacheable cubes; if a cube can't be baked (unexpected topology)
        // we flush the current run, render that cube via the legacy path, then continue.

        // Hoist bone matrices once.
        val mm = ms.modelMatrix
        val m00 = mm.m00
        val m01 = mm.m01
        val m02 = mm.m02
        val m03 = mm.m03
        val m10 = mm.m10
        val m11 = mm.m11
        val m12 = mm.m12
        val m13 = mm.m13
        val m20 = mm.m20
        val m21 = mm.m21
        val m22 = mm.m22
        val m23 = mm.m23

        val nn = ms.normalMatrix
        val n00 = nn.m00
        val n01 = nn.m01
        val n02 = nn.m02
        val n10 = nn.m10
        val n11 = nn.m11
        val n12 = nn.m12
        val n20 = nn.m20
        val n21 = nn.m21
        val n22 = nn.m22

        val cn = cubes.size

        // Hot-path settings: hoist volatile reads.
        val preferModernPipeline = RenderTuning.geckoModernVertexPipeline
        val minVerticesForPipeline = RenderTuning.geckoBoneBatchingMinVertices

        scratch.ensureBakedCubeCapacity(cn)
        val bakedCubes = scratch.bakedCubes
        var bakedCount = 0
        var runQuads = 0
        var runVertices = 0

        fun flushRun() {
            if (runVertices <= 0 || bakedCount <= 0) return

            // Prefer modern pipeline when available.
            if (
                preferModernPipeline &&
                (minVerticesForPipeline <= 0 || runVertices >= minVerticesForPipeline) &&
                geckoVertexPipeline != null
            ) {
                if (renderPreBakedCubesModernPipeline(
                        builder = builder,
                        bakedCubes = bakedCubes,
                        bakedCount = bakedCount,
                        totalQuads = runQuads,
                        totalVertices = runVertices,
                        red = red,
                        green = green,
                        blue = blue,
                        alpha = alpha,
                        scratch = scratch,
                        m00 = m00,
                        m01 = m01,
                        m02 = m02,
                        m03 = m03,
                        m10 = m10,
                        m11 = m11,
                        m12 = m12,
                        m13 = m13,
                        m20 = m20,
                        m21 = m21,
                        m22 = m22,
                        m23 = m23,
                        n00 = n00,
                        n01 = n01,
                        n02 = n02,
                        n10 = n10,
                        n11 = n11,
                        n12 = n12,
                        n20 = n20,
                        n21 = n21,
                        n22 = n22,
                    )
                ) {
                    bakedCount = 0
                    runQuads = 0
                    runVertices = 0
                    return
                }
            }

            // Fallback: pure Kotlin bulk pack.
            renderPreBakedCubesBulkPacked(
                builder = builder,
                bakedCubes = bakedCubes,
                bakedCount = bakedCount,
                totalQuads = runQuads,
                totalVertices = runVertices,
                red = red,
                green = green,
                blue = blue,
                alpha = alpha,
                scratch = scratch,
                m00 = m00,
                m01 = m01,
                m02 = m02,
                m03 = m03,
                m10 = m10,
                m11 = m11,
                m12 = m12,
                m13 = m13,
                m20 = m20,
                m21 = m21,
                m22 = m22,
                m23 = m23,
                n00 = n00,
                n01 = n01,
                n02 = n02,
                n10 = n10,
                n11 = n11,
                n12 = n12,
                n20 = n20,
                n21 = n21,
                n22 = n22,
            )

            bakedCount = 0
            runQuads = 0
            runVertices = 0
        }

        var ci = 0
        while (ci < cn) {
            val cube = cubes[ci]
            val baked = BakedCubeCache.getOrBake(cube)
            if (baked == null || baked.vertexCount == 0) {
                flushRun()

                // Fallback: render individually with full cube-local transforms.
                ms.push()
                renderCube(builder, ms, cube, red, green, blue, alpha, scratch)
                ms.pop()
            } else {
                bakedCubes[bakedCount++] = baked
                runQuads += baked.quadCount
                runVertices += baked.vertexCount
            }
            ci++
        }

        flushRun()
    }

    /**
     * Modern pipeline path for pre-baked cubes.
     */
    private fun renderPreBakedCubesModernPipeline(
        builder: BufferBuilder,
        bakedCubes: Array<BakedCubeCache.BakedCube?>,
        bakedCount: Int,
        totalQuads: Int,
        totalVertices: Int,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        scratch: Scratch,
        m00: Float,
        m01: Float,
        m02: Float,
        m03: Float,
        m10: Float,
        m11: Float,
        m12: Float,
        m13: Float,
        m20: Float,
        m21: Float,
        m22: Float,
        m23: Float,
        n00: Float,
        n01: Float,
        n02: Float,
        n10: Float,
        n11: Float,
        n12: Float,
        n20: Float,
        n21: Float,
        n22: Float,
    ): Boolean {
        val t0 = if (RenderStats.enabled) System.nanoTime() else 0L

        val pipeline = geckoVertexPipeline ?: return false

        // Constant bake-tint.
        val packedColor = packColor(red, green, blue, alpha)

        scratch.ensureVertexCapacity(totalVertices)
        scratch.ensurePackedExactIntCount(totalVertices * 7)

        val xs = scratch.xs
        val ys = scratch.ys
        val zs = scratch.zs

        // Copy pre-baked positions into scratch arrays.
        // Note: UVs are not transformed; we keep them in baked arrays to avoid extra scratch writes.
        var vBase = 0
        var bi = 0
        while (bi < bakedCount) {
            val baked = bakedCubes[bi]
            if (baked != null) {
                val positions = baked.positions
                val vc = baked.vertexCount
                var p = 0
                val end = vBase + vc
                while (vBase < end) {
                    xs[vBase] = positions[p]
                    ys[vBase] = positions[p + 1]
                    zs[vBase] = positions[p + 2]
                    vBase++
                    p += 3
                }
            }
            bi++
        }

        // Transform positions via modern pipeline.
        val forceScalar = RenderTuning.geckoModernVertexPipelineForceScalar
        val ok = pipeline.transformAffine3x4InPlace(
            forceScalar,
            xs,
            ys,
            zs,
            0,
            totalVertices,
            m00, m01, m02, m03,
            m10, m11, m12, m13,
            m20, m21, m22, m23,
        )
        if (!ok) return false

        // Pack AoS in original order with transformed normals.
        val out = scratch.packed
        var outIntOffset = 0
        var vertexOffset = 0

        bi = 0
        while (bi < bakedCount) {
            val baked = bakedCubes[bi]
            if (baked != null) {
                val normals = baked.normals
                val flatFlags = baked.flatFlags
                val uvs = baked.uvs
                val qc = baked.quadCount

                var localVertexOffset = 0
                var qi = 0
                var ni = 0
                while (qi < qc) {
                    // Transform the pre-baked normal.
                    val bnx = normals[ni]
                    val bny = normals[ni + 1]
                    val bnz = normals[ni + 2]

                    val flags = flatFlags[qi].toInt()
                    val packedNormal = transformAndPackNormalWithFlatFlags(
                        n00,
                        n01,
                        n02,
                        n10,
                        n11,
                        n12,
                        n20,
                        n21,
                        n22,
                        bnx,
                        bny,
                        bnz,
                        flags,
                    )

                    // Pack 4 vertices for this quad (unrolled).
                    val v0 = vertexOffset
                    val uv0 = localVertexOffset shl 1

                    var o = outIntOffset

                    out[o + 0] = java.lang.Float.floatToRawIntBits(xs[v0])
                    out[o + 1] = java.lang.Float.floatToRawIntBits(ys[v0])
                    out[o + 2] = java.lang.Float.floatToRawIntBits(zs[v0])
                    out[o + 3] = java.lang.Float.floatToRawIntBits(uvs[uv0])
                    out[o + 4] = java.lang.Float.floatToRawIntBits(uvs[uv0 + 1])
                    out[o + 5] = packedColor
                    out[o + 6] = packedNormal
                    o += 7

                    out[o + 0] = java.lang.Float.floatToRawIntBits(xs[v0 + 1])
                    out[o + 1] = java.lang.Float.floatToRawIntBits(ys[v0 + 1])
                    out[o + 2] = java.lang.Float.floatToRawIntBits(zs[v0 + 1])
                    out[o + 3] = java.lang.Float.floatToRawIntBits(uvs[uv0 + 2])
                    out[o + 4] = java.lang.Float.floatToRawIntBits(uvs[uv0 + 3])
                    out[o + 5] = packedColor
                    out[o + 6] = packedNormal
                    o += 7

                    out[o + 0] = java.lang.Float.floatToRawIntBits(xs[v0 + 2])
                    out[o + 1] = java.lang.Float.floatToRawIntBits(ys[v0 + 2])
                    out[o + 2] = java.lang.Float.floatToRawIntBits(zs[v0 + 2])
                    out[o + 3] = java.lang.Float.floatToRawIntBits(uvs[uv0 + 4])
                    out[o + 4] = java.lang.Float.floatToRawIntBits(uvs[uv0 + 5])
                    out[o + 5] = packedColor
                    out[o + 6] = packedNormal
                    o += 7

                    out[o + 0] = java.lang.Float.floatToRawIntBits(xs[v0 + 3])
                    out[o + 1] = java.lang.Float.floatToRawIntBits(ys[v0 + 3])
                    out[o + 2] = java.lang.Float.floatToRawIntBits(zs[v0 + 3])
                    out[o + 3] = java.lang.Float.floatToRawIntBits(uvs[uv0 + 6])
                    out[o + 4] = java.lang.Float.floatToRawIntBits(uvs[uv0 + 7])
                    out[o + 5] = packedColor
                    out[o + 6] = packedNormal
                    o += 7

                    outIntOffset = o
                    vertexOffset += 4
                    localVertexOffset += 4
                    qi++
                    ni += 3
                }
            }
            bi++
        }

        builder.addVertexData(out)

        RenderStats.noteGeckoModernPipelineBackend(
            pipeline.backendName(forceScalar),
            pipeline.isVectorized(forceScalar)
        )
        RenderStats.addGeckoPipelineBatch(
            quadCount = totalQuads,
            vertexCount = totalVertices
        )
        if (t0 != 0L) {
            RenderStats.addGeckoPipelineNanos(System.nanoTime() - t0)
        }

        return true
    }

    /**
     * Bulk-pack fallback for pre-baked cubes.
     */
    private fun renderPreBakedCubesBulkPacked(
        builder: BufferBuilder,
        bakedCubes: Array<BakedCubeCache.BakedCube?>,
        bakedCount: Int,
        totalQuads: Int,
        totalVertices: Int,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        scratch: Scratch,
        m00: Float,
        m01: Float,
        m02: Float,
        m03: Float,
        m10: Float,
        m11: Float,
        m12: Float,
        m13: Float,
        m20: Float,
        m21: Float,
        m22: Float,
        m23: Float,
        n00: Float,
        n01: Float,
        n02: Float,
        n10: Float,
        n11: Float,
        n12: Float,
        n20: Float,
        n21: Float,
        n22: Float,
    ) {
        val t0 = if (RenderStats.enabled) System.nanoTime() else 0L

        // Constant bake-tint.
        val packedColor = packColor(red, green, blue, alpha)

        scratch.ensurePackedExactIntCount(totalVertices * 7)
        val out = scratch.packed
        var outIntOffset = 0

        var bi = 0
        while (bi < bakedCount) {
            val baked = bakedCubes[bi]
            if (baked != null) {
                val positions = baked.positions
                val normals = baked.normals
                val uvs = baked.uvs
                val flatFlags = baked.flatFlags
                val qc = baked.quadCount

                var p = 0
                var uv = 0
                var qi = 0
                var ni = 0
                while (qi < qc) {
                    // Transform the pre-baked normal.
                    val bnx = normals[ni]
                    val bny = normals[ni + 1]
                    val bnz = normals[ni + 2]

                    val flags = flatFlags[qi].toInt()
                    val packedNormal = transformAndPackNormalWithFlatFlags(
                        n00,
                        n01,
                        n02,
                        n10,
                        n11,
                        n12,
                        n20,
                        n21,
                        n22,
                        bnx,
                        bny,
                        bnz,
                        flags,
                    )

                    // Pack 4 vertices for this quad (unrolled).
                    var o = outIntOffset

                    var px = positions[p]
                    var py = positions[p + 1]
                    var pz = positions[p + 2]
                    p += 3
                    var tx = m00 * px + m01 * py + m02 * pz + m03
                    var ty = m10 * px + m11 * py + m12 * pz + m13
                    var tz = m20 * px + m21 * py + m22 * pz + m23
                    out[o + 0] = java.lang.Float.floatToRawIntBits(tx)
                    out[o + 1] = java.lang.Float.floatToRawIntBits(ty)
                    out[o + 2] = java.lang.Float.floatToRawIntBits(tz)
                    out[o + 3] = java.lang.Float.floatToRawIntBits(uvs[uv])
                    out[o + 4] = java.lang.Float.floatToRawIntBits(uvs[uv + 1])
                    out[o + 5] = packedColor
                    out[o + 6] = packedNormal
                    o += 7
                    uv += 2

                    px = positions[p]
                    py = positions[p + 1]
                    pz = positions[p + 2]
                    p += 3
                    tx = m00 * px + m01 * py + m02 * pz + m03
                    ty = m10 * px + m11 * py + m12 * pz + m13
                    tz = m20 * px + m21 * py + m22 * pz + m23
                    out[o + 0] = java.lang.Float.floatToRawIntBits(tx)
                    out[o + 1] = java.lang.Float.floatToRawIntBits(ty)
                    out[o + 2] = java.lang.Float.floatToRawIntBits(tz)
                    out[o + 3] = java.lang.Float.floatToRawIntBits(uvs[uv])
                    out[o + 4] = java.lang.Float.floatToRawIntBits(uvs[uv + 1])
                    out[o + 5] = packedColor
                    out[o + 6] = packedNormal
                    o += 7
                    uv += 2

                    px = positions[p]
                    py = positions[p + 1]
                    pz = positions[p + 2]
                    p += 3
                    tx = m00 * px + m01 * py + m02 * pz + m03
                    ty = m10 * px + m11 * py + m12 * pz + m13
                    tz = m20 * px + m21 * py + m22 * pz + m23
                    out[o + 0] = java.lang.Float.floatToRawIntBits(tx)
                    out[o + 1] = java.lang.Float.floatToRawIntBits(ty)
                    out[o + 2] = java.lang.Float.floatToRawIntBits(tz)
                    out[o + 3] = java.lang.Float.floatToRawIntBits(uvs[uv])
                    out[o + 4] = java.lang.Float.floatToRawIntBits(uvs[uv + 1])
                    out[o + 5] = packedColor
                    out[o + 6] = packedNormal
                    o += 7
                    uv += 2

                    px = positions[p]
                    py = positions[p + 1]
                    pz = positions[p + 2]
                    p += 3
                    tx = m00 * px + m01 * py + m02 * pz + m03
                    ty = m10 * px + m11 * py + m12 * pz + m13
                    tz = m20 * px + m21 * py + m22 * pz + m23
                    out[o + 0] = java.lang.Float.floatToRawIntBits(tx)
                    out[o + 1] = java.lang.Float.floatToRawIntBits(ty)
                    out[o + 2] = java.lang.Float.floatToRawIntBits(tz)
                    out[o + 3] = java.lang.Float.floatToRawIntBits(uvs[uv])
                    out[o + 4] = java.lang.Float.floatToRawIntBits(uvs[uv + 1])
                    out[o + 5] = packedColor
                    out[o + 6] = packedNormal
                    o += 7
                    uv += 2

                    outIntOffset = o
                    qi++
                    ni += 3
                }
            }
            bi++
        }

        builder.addVertexData(out)

        RenderStats.addGeckoBulkQuads(totalQuads)
        if (t0 != 0L) {
            RenderStats.addGeckoQuadBulkNanos(System.nanoTime() - t0, totalVertices)
        }
    }

    /**
     * Render a contiguous run of identity-rotation cubes that all share the current [ms] matrix.
     *
     * Must preserve draw order: cubes/quads/verts are emitted in their original sequence.
     */
    private fun renderCubeRunSameMatrix(
        builder: BufferBuilder,
        ms: MmceMatrixStack,
        cubes: List<GeoCube>,
        startInclusive: Int,
        endExclusive: Int,
        quadCount: Int,
        vertexCount: Int,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        scratch: Scratch,
    ): Boolean {
        if (quadCount <= 0 || vertexCount <= 0) return true

        // Hoist matrices once for the whole run.
        val mm = ms.modelMatrix
        val m00 = mm.m00
        val m01 = mm.m01
        val m02 = mm.m02
        val m03 = mm.m03
        val m10 = mm.m10
        val m11 = mm.m11
        val m12 = mm.m12
        val m13 = mm.m13
        val m20 = mm.m20
        val m21 = mm.m21
        val m22 = mm.m22
        val m23 = mm.m23

        val nn = ms.normalMatrix
        val n00 = nn.m00
        val n01 = nn.m01
        val n02 = nn.m02
        val n10 = nn.m10
        val n11 = nn.m11
        val n12 = nn.m12
        val n20 = nn.m20
        val n21 = nn.m21
        val n22 = nn.m22

        // Prefer modern pipeline when available.
        if (
            RenderTuning.geckoModernVertexPipeline &&
            geckoVertexPipeline != null
        ) {
            return renderCubeRunModernPipeline(
                builder = builder,
                cubes = cubes,
                startInclusive = startInclusive,
                endExclusive = endExclusive,
                quadCount = quadCount,
                vertexCount = vertexCount,
                red = red,
                green = green,
                blue = blue,
                alpha = alpha,
                scratch = scratch,
                m00 = m00,
                m01 = m01,
                m02 = m02,
                m03 = m03,
                m10 = m10,
                m11 = m11,
                m12 = m12,
                m13 = m13,
                m20 = m20,
                m21 = m21,
                m22 = m22,
                m23 = m23,
                n00 = n00,
                n01 = n01,
                n02 = n02,
                n10 = n10,
                n11 = n11,
                n12 = n12,
                n20 = n20,
                n21 = n21,
                n22 = n22,
            )
        }

        // Fallback: pure Kotlin bulk pack (still benefits from one submit per run).
        return renderCubeRunBulkPacked(
            builder = builder,
            cubes = cubes,
            startInclusive = startInclusive,
            endExclusive = endExclusive,
            quadCount = quadCount,
            vertexCount = vertexCount,
            red = red,
            green = green,
            blue = blue,
            alpha = alpha,
            scratch = scratch,
            m00 = m00,
            m01 = m01,
            m02 = m02,
            m03 = m03,
            m10 = m10,
            m11 = m11,
            m12 = m12,
            m13 = m13,
            m20 = m20,
            m21 = m21,
            m22 = m22,
            m23 = m23,
            n00 = n00,
            n01 = n01,
            n02 = n02,
            n10 = n10,
            n11 = n11,
            n12 = n12,
            n20 = n20,
            n21 = n21,
            n22 = n22,
        )
    }

    private fun renderCubeRunModernPipeline(
        builder: BufferBuilder,
        cubes: List<GeoCube>,
        startInclusive: Int,
        endExclusive: Int,
        quadCount: Int,
        vertexCount: Int,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        scratch: Scratch,
        m00: Float,
        m01: Float,
        m02: Float,
        m03: Float,
        m10: Float,
        m11: Float,
        m12: Float,
        m13: Float,
        m20: Float,
        m21: Float,
        m22: Float,
        m23: Float,
        n00: Float,
        n01: Float,
        n02: Float,
        n10: Float,
        n11: Float,
        n12: Float,
        n20: Float,
        n21: Float,
        n22: Float,
    ): Boolean {
        val t0 = if (RenderStats.enabled) System.nanoTime() else 0L

        val pipeline = geckoVertexPipeline ?: return false

        // Constant bake-tint per run.
        val packedColor = packColor(red, green, blue, alpha)

        scratch.ensureVertexCapacity(vertexCount)
        scratch.ensurePackedExactIntCount(vertexCount * 7)

        // Fill SoA arrays in original order.
        var vBase = 0
        var ci = startInclusive
        while (ci < endExclusive) {
            val cube = cubes[ci]
            val quads = cube.quads
            var qi = 0
            val qn = quads.size
            while (qi < qn) {
                val quad = quads[qi]
                if (quad != null) {
                    val verts = quad.vertices
                    var vi = 0
                    while (vi < 4) {
                        val vertex = verts[vi]
                        val pos = vertex.position

                        scratch.xs[vBase] = pos.x
                        scratch.ys[vBase] = pos.y
                        scratch.zs[vBase] = pos.z

                        vBase++
                        vi++
                    }
                }
                qi++
            }
            ci++
        }

        val forceScalar = RenderTuning.geckoModernVertexPipelineForceScalar
        val ok = pipeline.transformAffine3x4InPlace(
            forceScalar,
            scratch.xs,
            scratch.ys,
            scratch.zs,
            0,
            vertexCount,
            m00, m01, m02, m03,
            m10, m11, m12, m13,
            m20, m21, m22, m23,
        )
        if (!ok) return false

        // Pack AoS in original order.
        val out = scratch.packed
        var outIntOffset = 0
        var vertexOffset = 0
        ci = startInclusive
        while (ci < endExclusive) {
            val cube = cubes[ci]
            val cubeFlatFlags = flatFlagsForCube(cube)
            val quads = cube.quads
            var qi = 0
            val qn = quads.size
            while (qi < qn) {
                val quad = quads[qi]
                if (quad != null) {
                    val verts = quad.vertices
                    val qnx = quad.normal.x.toFloat()
                    val qny = quad.normal.y.toFloat()
                    val qnz = quad.normal.z.toFloat()

                    val packedNormal = transformAndPackNormalWithFlatFlags(
                        n00,
                        n01,
                        n02,
                        n10,
                        n11,
                        n12,
                        n20,
                        n21,
                        n22,
                        qnx,
                        qny,
                        qnz,
                        cubeFlatFlags,
                    )

                    var vi = 0
                    while (vi < 4) {
                        val idx = vertexOffset + vi
                        val vertex = verts[vi]
                        out[outIntOffset + 0] = java.lang.Float.floatToRawIntBits(scratch.xs[idx])
                        out[outIntOffset + 1] = java.lang.Float.floatToRawIntBits(scratch.ys[idx])
                        out[outIntOffset + 2] = java.lang.Float.floatToRawIntBits(scratch.zs[idx])
                        out[outIntOffset + 3] = java.lang.Float.floatToRawIntBits(vertex.textureU.toFloat())
                        out[outIntOffset + 4] = java.lang.Float.floatToRawIntBits(vertex.textureV.toFloat())
                        out[outIntOffset + 5] = packedColor
                        out[outIntOffset + 6] = packedNormal
                        outIntOffset += 7
                        vi++
                    }
                    vertexOffset += 4
                }
                qi++
            }
            ci++
        }

        builder.addVertexData(out)

        RenderStats.noteGeckoModernPipelineBackend(
            pipeline.backendName(forceScalar),
            pipeline.isVectorized(forceScalar)
        )
        RenderStats.addGeckoPipelineBatch(
            quadCount = quadCount,
            vertexCount = vertexCount
        )
        if (t0 != 0L) {
            RenderStats.addGeckoPipelineNanos(System.nanoTime() - t0)
        }

        return true
    }

    private fun renderCubeRunBulkPacked(
        builder: BufferBuilder,
        cubes: List<GeoCube>,
        startInclusive: Int,
        endExclusive: Int,
        quadCount: Int,
        vertexCount: Int,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        scratch: Scratch,
        m00: Float,
        m01: Float,
        m02: Float,
        m03: Float,
        m10: Float,
        m11: Float,
        m12: Float,
        m13: Float,
        m20: Float,
        m21: Float,
        m22: Float,
        m23: Float,
        n00: Float,
        n01: Float,
        n02: Float,
        n10: Float,
        n11: Float,
        n12: Float,
        n20: Float,
        n21: Float,
        n22: Float,
    ): Boolean {
        val t0 = if (RenderStats.enabled) System.nanoTime() else 0L

        val packedColor = packColor(red, green, blue, alpha)

        scratch.ensurePackedExactIntCount(vertexCount * 7)
        val out = scratch.packed

        var o = 0
        var ci = startInclusive
        while (ci < endExclusive) {
            val cube = cubes[ci]
            val cubeFlatFlags = flatFlagsForCube(cube)
            val quads = cube.quads
            var qi = 0
            val qn = quads.size
            while (qi < qn) {
                val quad = quads[qi]
                if (quad != null) {
                    val qnx = quad.normal.x.toFloat()
                    val qny = quad.normal.y.toFloat()
                    val qnz = quad.normal.z.toFloat()

                    val packedNormal = transformAndPackNormalWithFlatFlags(
                        n00,
                        n01,
                        n02,
                        n10,
                        n11,
                        n12,
                        n20,
                        n21,
                        n22,
                        qnx,
                        qny,
                        qnz,
                        cubeFlatFlags,
                    )

                    val verts = quad.vertices
                    var vi = 0
                    while (vi < 4) {
                        val vertex = verts[vi]
                        val pos = vertex.position

                        val x = pos.x
                        val y = pos.y
                        val z = pos.z

                        val px = m00 * x + m01 * y + m02 * z + m03
                        val py = m10 * x + m11 * y + m12 * z + m13
                        val pz = m20 * x + m21 * y + m22 * z + m23

                        out[o + 0] = java.lang.Float.floatToRawIntBits(px)
                        out[o + 1] = java.lang.Float.floatToRawIntBits(py)
                        out[o + 2] = java.lang.Float.floatToRawIntBits(pz)
                        out[o + 3] = java.lang.Float.floatToRawIntBits(vertex.textureU.toFloat())
                        out[o + 4] = java.lang.Float.floatToRawIntBits(vertex.textureV.toFloat())
                        out[o + 5] = packedColor
                        out[o + 6] = packedNormal
                        o += 7

                        vi++
                    }
                }
                qi++
            }
            ci++
        }

        builder.addVertexData(out)
        RenderStats.addGeckoBulkQuads(quadCount)
        if (t0 != 0L) {
            RenderStats.addGeckoQuadBulkNanos(System.nanoTime() - t0, vertexCount)
        }

        return true
    }

    private fun renderCubeModernPipeline(
        builder: BufferBuilder,
        cube: GeoCube,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        scratch: Scratch,
        m00: Float,
        m01: Float,
        m02: Float,
        m03: Float,
        m10: Float,
        m11: Float,
        m12: Float,
        m13: Float,
        m20: Float,
        m21: Float,
        m22: Float,
        m23: Float,
        n00: Float,
        n01: Float,
        n02: Float,
        n10: Float,
        n11: Float,
        n12: Float,
        n20: Float,
        n21: Float,
        n22: Float,
    ): Boolean {
        val t0 = if (RenderStats.enabled) System.nanoTime() else 0L

        val pipeline = geckoVertexPipeline ?: return false
        val quads = cube.quads
        if (quads.isEmpty()) return true

        // Count quads and validate vertex count up front.
        var quadCount = 0
        var vertexCount = 0
        var qi = 0
        val qn = quads.size
        while (qi < qn) {
            val q = quads[qi]
            if (q != null) {
                val vn = q.vertices.size
                if (vn != 4) {
                    // Unexpected topology: fall back to robust per-quad path.
                    return false
                }
                quadCount++
                vertexCount += 4
            }
            qi++
        }
        if (vertexCount <= 0) return true

        // Bake-tint is constant per cube.
        val packedColor = packColor(red, green, blue, alpha)

        scratch.ensureVertexCapacity(vertexCount)
        scratch.ensurePackedExactIntCount(vertexCount * 7)

        // Fill SoA arrays.
        var vBase = 0
        qi = 0
        while (qi < qn) {
            val quad = quads[qi]
            if (quad != null) {
                val verts = quad.vertices
                var vi = 0
                while (vi < 4) {
                    val vertex = verts[vi]
                    val pos = vertex.position

                    scratch.xs[vBase] = pos.x
                    scratch.ys[vBase] = pos.y
                    scratch.zs[vBase] = pos.z

                    vBase++
                    vi++
                }
            }
            qi++
        }

        val forceScalar = RenderTuning.geckoModernVertexPipelineForceScalar

        // Transform positions in-place (vector backend can help here for larger batches).
        val ok = pipeline.transformAffine3x4InPlace(
            forceScalar,
            scratch.xs,
            scratch.ys,
            scratch.zs,
            0,
            vertexCount,
            m00, m01, m02, m03,
            m10, m11, m12, m13,
            m20, m21, m22, m23,
        )
        if (!ok) return false

        // Pack AoS directly, avoiding per-vertex color/normal arrays.
        val out = scratch.packed
        var outIntOffset = 0
        var vertexOffset = 0

        val cubeFlatFlags = flatFlagsForCube(cube)
        qi = 0
        while (qi < qn) {
            val quad = quads[qi]
            if (quad != null) {
                val verts = quad.vertices
                val qnx = quad.normal.x.toFloat()
                val qny = quad.normal.y.toFloat()
                val qnz = quad.normal.z.toFloat()

                val packedNormal = transformAndPackNormalWithFlatFlags(
                    n00,
                    n01,
                    n02,
                    n10,
                    n11,
                    n12,
                    n20,
                    n21,
                    n22,
                    qnx,
                    qny,
                    qnz,
                    cubeFlatFlags,
                )

                var vi = 0
                while (vi < 4) {
                    val idx = vertexOffset + vi
                    val vertex = verts[vi]
                    out[outIntOffset + 0] = java.lang.Float.floatToRawIntBits(scratch.xs[idx])
                    out[outIntOffset + 1] = java.lang.Float.floatToRawIntBits(scratch.ys[idx])
                    out[outIntOffset + 2] = java.lang.Float.floatToRawIntBits(scratch.zs[idx])
                    out[outIntOffset + 3] = java.lang.Float.floatToRawIntBits(vertex.textureU.toFloat())
                    out[outIntOffset + 4] = java.lang.Float.floatToRawIntBits(vertex.textureV.toFloat())
                    out[outIntOffset + 5] = packedColor
                    out[outIntOffset + 6] = packedNormal
                    outIntOffset += 7
                    vi++
                }
                vertexOffset += 4
            }
            qi++
        }

        // One submit per cube.
        builder.addVertexData(out)

        // Debug stats (gated inside RenderStats).
        RenderStats.noteGeckoModernPipelineBackend(
            pipeline.backendName(forceScalar),
            pipeline.isVectorized(forceScalar)
        )
        RenderStats.addGeckoPipelineBatch(
            quadCount = quadCount,
            vertexCount = vertexCount
        )

        if (t0 != 0L) {
            RenderStats.addGeckoPipelineNanos(System.nanoTime() - t0)
        }

        return true
    }

    private fun renderCubeBulkPacked(
        builder: BufferBuilder,
        cube: GeoCube,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        scratch: Scratch,
        m00: Float,
        m01: Float,
        m02: Float,
        m03: Float,
        m10: Float,
        m11: Float,
        m12: Float,
        m13: Float,
        m20: Float,
        m21: Float,
        m22: Float,
        m23: Float,
        n00: Float,
        n01: Float,
        n02: Float,
        n10: Float,
        n11: Float,
        n12: Float,
        n20: Float,
        n21: Float,
        n22: Float,
    ): Boolean {
        val t0 = if (RenderStats.enabled) System.nanoTime() else 0L

        val quads = cube.quads
        if (quads.isEmpty()) return true

        // Count quads and validate vertex count up front.
        var quadCount = 0
        var qi = 0
        val qn = quads.size
        while (qi < qn) {
            val q = quads[qi]
            if (q != null) {
                val vn = q.vertices.size
                if (vn != 4) {
                    return false
                }
                quadCount++
            }
            qi++
        }
        if (quadCount <= 0) return true

        val vertexCount = quadCount * 4

        // Bake-tint is constant per cube.
        val packedColor = packColor(red, green, blue, alpha)

        scratch.ensurePackedExactIntCount(vertexCount * 7)
        val out = scratch.packed

        val cubeFlatFlags = flatFlagsForCube(cube)

        var o = 0
        qi = 0
        while (qi < qn) {
            val quad = quads[qi]
            if (quad != null) {
                val qnx = quad.normal.x.toFloat()
                val qny = quad.normal.y.toFloat()
                val qnz = quad.normal.z.toFloat()

                val packedNormal = transformAndPackNormalWithFlatFlags(
                    n00,
                    n01,
                    n02,
                    n10,
                    n11,
                    n12,
                    n20,
                    n21,
                    n22,
                    qnx,
                    qny,
                    qnz,
                    cubeFlatFlags,
                )

                val verts = quad.vertices
                var vi = 0
                while (vi < 4) {
                    val vertex = verts[vi]
                    val pos = vertex.position
                    val x = pos.x
                    val y = pos.y
                    val z = pos.z

                    val px = m00 * x + m01 * y + m02 * z + m03
                    val py = m10 * x + m11 * y + m12 * z + m13
                    val pz = m20 * x + m21 * y + m22 * z + m23

                    out[o + 0] = java.lang.Float.floatToRawIntBits(px)
                    out[o + 1] = java.lang.Float.floatToRawIntBits(py)
                    out[o + 2] = java.lang.Float.floatToRawIntBits(pz)
                    out[o + 3] = java.lang.Float.floatToRawIntBits(vertex.textureU.toFloat())
                    out[o + 4] = java.lang.Float.floatToRawIntBits(vertex.textureV.toFloat())
                    out[o + 5] = packedColor
                    out[o + 6] = packedNormal
                    o += 7

                    vi++
                }
            }
            qi++
        }

        builder.addVertexData(out)

        RenderStats.addGeckoBulkQuads(quadCount)
        if (t0 != 0L) {
            RenderStats.addGeckoQuadBulkNanos(System.nanoTime() - t0, vertexCount)
        }

        return true
    }
}
