package github.kasuminova.prototypemachinery.client.impl.render.gecko

import github.kasuminova.prototypemachinery.client.impl.render.RenderStats
import github.kasuminova.prototypemachinery.client.impl.render.gecko.GeckoModelBaker.bakeRoutedFiltered
import github.kasuminova.prototypemachinery.client.util.MatrixStack
import net.minecraft.client.renderer.BufferBuilder
import software.bernie.geckolib3.geo.render.built.GeoBone
import software.bernie.geckolib3.geo.render.built.GeoCube
import software.bernie.geckolib3.geo.render.built.GeoModel
import software.bernie.geckolib3.geo.render.built.GeoQuad
import java.util.WeakHashMap

/**
 * CPU-side baker that converts GeckoLib built models into a [BufferBuilder].
 *
 * This is adapted from GeckoLib's `IGeoRenderer` but:
 * - avoids Tessellator/GlStateManager
 * - uses a per-bake [MatrixStack] (thread-safe)
 */
internal object GeckoModelBaker {

    internal fun interface PackedVertexDataWriter {
        fun write(src: IntArray, offsetInts: Int, lengthInts: Int)
    }

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
                        // GeckoLib GeoQuad is expected to be a quad.
                        // Treat non-4 vertex counts as a bug (fail fast so we don't silently produce corrupt buffers).
                        val vn = q.vertices.size
                        check(vn == 4) { "GeoQuad.vertices must have size 4, got $vn (bone=$name)" }
                        out[idx] += 4
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
        matrixStack: MatrixStack,
        red: Float = 1.0f,
        green: Float = 1.0f,
        blue: Float = 1.0f,
        alpha: Float = 1.0f,
    ) {
        val tint = BakeTint(red, green, blue, alpha)
        val scratch = Scratch()
        val top = model.topLevelBones
        var i = 0
        val n = top.size
        while (i < n) {
            renderRecursively(builder, matrixStack, top[i], tint, scratch)
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
        matrixStack: MatrixStack,
        bufferSelector: (bloom: Boolean, transparent: Boolean) -> BufferBuilder,
        red: Float = 1.0f,
        green: Float = 1.0f,
        blue: Float = 1.0f,
        alpha: Float = 1.0f,
    ) {
        val tint = BakeTint(red, green, blue, alpha)
        val scratch = Scratch()
        val top = model.topLevelBones
        var i = 0
        val n = top.size
        while (i < n) {
            renderRecursivelyRouted(
                ms = matrixStack,
                bone = top[i],
                bufferSelector = bufferSelector,
                tint = tint,
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
        matrixStack: MatrixStack,
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

        val tint = BakeTint(red, green, blue, alpha)
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
                tint = tint,
                bloom = false,
                transparent = false,
                parentPotential = false,
                parentDynamic = false,
                scratch = scratch,
            )
            i++
        }
    }

    /**
     * Packed variant of [bakeRoutedFiltered]: instead of emitting into [BufferBuilder], it writes packed
     * POSITION_TEX_COLOR_NORMAL int data into an external sink.
     *
     * NOTE: This intentionally uses a conservative/compatible path (per-quad packing) to keep the prototype small.
     * It does NOT use cube pre-bake / modern-backend pipeline.
     */
    internal fun bakeRoutedFilteredPacked(
        model: GeoModel,
        matrixStack: MatrixStack,
        writerSelector: (bloom: Boolean, transparent: Boolean) -> PackedVertexDataWriter,
        activeAnimatedBones: Set<String>,
        mode: BakeMode,
        potentialAnimatedBones: Set<String> = activeAnimatedBones,
        red: Float = 1.0f,
        green: Float = 1.0f,
        blue: Float = 1.0f,
        alpha: Float = 1.0f,
    ) {
        val tint = BakeTint(red, green, blue, alpha)
        val scratch = Scratch()
        val top = model.topLevelBones
        var i = 0
        val n = top.size
        while (i < n) {
            renderRecursivelyRoutedFilteredPacked(
                ms = matrixStack,
                bone = top[i],
                writerSelector = writerSelector,
                potentialAnimatedBones = potentialAnimatedBones,
                activeAnimatedBones = activeAnimatedBones,
                mode = mode,
                tint = tint,
                bloom = false,
                transparent = false,
                parentPotential = false,
                parentDynamic = false,
                scratch = scratch,
            )
            i++
        }
    }

    private fun renderRecursivelyRoutedFilteredPacked(
        ms: MatrixStack,
        bone: GeoBone,
        writerSelector: (bloom: Boolean, transparent: Boolean) -> PackedVertexDataWriter,
        potentialAnimatedBones: Set<String>,
        activeAnimatedBones: Set<String>,
        mode: BakeMode,
        tint: BakeTint,
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
                if (inPotentialSubtree) return
            }

            BakeMode.TEMP_STATIC_ONLY -> {
                if (!inPotentialSubtree) return
                if (inDynamicSubtree) return
            }

            BakeMode.ANIMATED_ONLY -> {
                // traverse
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
            val cn = cubes.size
            if (cn > 0) {
                val writer = writerSelector(bloomNow, transparentNow)
                val boneMats = scratch.boneMatrices
                boneMats.loadFrom(ms)
                var ci = 0
                while (ci < cn) {
                    val cube = cubes[ci]
                    val cubeLocal = GeckoCubeLocalMatrixCache.get(cube)
                    scratch.matrices.setMul(boneMats, cubeLocal)
                    renderCubePackedWithMatrices(
                        writer = writer,
                        cube = cube,
                        tint = tint,
                        scratch = scratch,
                    )
                    ci++
                }
            }
        }

        if (!bone.childBonesAreHiddenToo()) {
            val children = bone.childBones
            var bi = 0
            val bn = children.size
            while (bi < bn) {
                renderRecursivelyRoutedFilteredPacked(
                    ms = ms,
                    bone = children[bi],
                    writerSelector = writerSelector,
                    potentialAnimatedBones = potentialAnimatedBones,
                    activeAnimatedBones = activeAnimatedBones,
                    mode = mode,
                    tint = tint,
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

    private fun renderCubePackedWithMatrices(
        writer: PackedVertexDataWriter,
        cube: GeoCube,
        tint: BakeTint,
        scratch: Scratch,
    ) {
        val mats = scratch.matrices
        val quads = cube.quads
        val cubeFlatFlags = GeckoBakerMath.flatFlagsForCube(cube)

        // Cache vertex attributes per-cube (cheaper than per-quad cache lookups).
        val baked = GeckoCubeQuadVertexCache.getOrBake(cube).xyzuv
        var bakedBase = 0

        var qi = 0
        val qn = quads.size
        while (qi < qn) {
            val quad = quads[qi]
            if (quad != null) {
                renderQuadPacked(
                    writer = writer,
                    quad = quad,
                    tint = tint,
                    mats = mats,
                    scratch = scratch,
                    flatFlags = cubeFlatFlags,
                    baked = baked,
                    bakedBase = bakedBase,
                )
                bakedBase += 20
            }
            qi++
        }
    }

    private fun renderQuadPacked(
        writer: PackedVertexDataWriter,
        quad: GeoQuad,
        tint: BakeTint,
        mats: GeckoAffineMatrices,
        scratch: Scratch,
        flatFlags: Int,
        baked: FloatArray,
        bakedBase: Int,
    ) {
        val timing = RenderStats.enabled
        val t0 = if (timing) System.nanoTime() else 0L

        val qnx = quad.normal.x.toFloat()
        val qny = quad.normal.y.toFloat()
        val qnz = quad.normal.z.toFloat()

        var nx = mats.n00 * qnx + mats.n01 * qny + mats.n02 * qnz
        var ny = mats.n10 * qnx + mats.n11 * qny + mats.n12 * qnz
        var nz = mats.n20 * qnx + mats.n21 * qny + mats.n22 * qnz

        if ((flatFlags and 1) != 0 && nx < 0) nx = -nx
        if ((flatFlags and 2) != 0 && ny < 0) ny = -ny
        if ((flatFlags and 4) != 0 && nz < 0) nz = -nz

        val t1 = if (timing) System.nanoTime() else 0L

        val packedColor = tint.packedColor
        val packedNormal = GeckoBakerMath.packNormal(nx, ny, nz)

        val verts = quad.vertices
        val vn = verts.size
        check(vn == 4) { "GeoQuad.vertices must have size 4, got $vn" }

        RenderStats.addGeckoBulkQuad()

        val data = scratch.quadVertexData

        val q = baked
        val b = bakedBase

        // Hot path: keep it allocation-free and JIT-friendly.
        val m00 = mats.m00
        val m01 = mats.m01
        val m02 = mats.m02
        val m03 = mats.m03
        val m10 = mats.m10
        val m11 = mats.m11
        val m12 = mats.m12
        val m13 = mats.m13
        val m20 = mats.m20
        val m21 = mats.m21
        val m22 = mats.m22
        val m23 = mats.m23

        val x0 = q[b + 0]
        val y0 = q[b + 1]
        val z0 = q[b + 2]
        val px0 = m00 * x0 + m01 * y0 + m02 * z0 + m03
        val py0 = m10 * x0 + m11 * y0 + m12 * z0 + m13
        val pz0 = m20 * x0 + m21 * y0 + m22 * z0 + m23
        val u0 = q[b + 3]
        val w0 = q[b + 4]
        data[0] = java.lang.Float.floatToRawIntBits(px0)
        data[1] = java.lang.Float.floatToRawIntBits(py0)
        data[2] = java.lang.Float.floatToRawIntBits(pz0)
        data[3] = java.lang.Float.floatToRawIntBits(u0)
        data[4] = java.lang.Float.floatToRawIntBits(w0)
        data[5] = packedColor
        data[6] = packedNormal

        val x1 = q[b + 5]
        val y1 = q[b + 6]
        val z1 = q[b + 7]
        val px1 = m00 * x1 + m01 * y1 + m02 * z1 + m03
        val py1 = m10 * x1 + m11 * y1 + m12 * z1 + m13
        val pz1 = m20 * x1 + m21 * y1 + m22 * z1 + m23
        val u1 = q[b + 8]
        val w1 = q[b + 9]
        data[7] = java.lang.Float.floatToRawIntBits(px1)
        data[8] = java.lang.Float.floatToRawIntBits(py1)
        data[9] = java.lang.Float.floatToRawIntBits(pz1)
        data[10] = java.lang.Float.floatToRawIntBits(u1)
        data[11] = java.lang.Float.floatToRawIntBits(w1)
        data[12] = packedColor
        data[13] = packedNormal

        val x2 = q[b + 10]
        val y2 = q[b + 11]
        val z2 = q[b + 12]
        val px2 = m00 * x2 + m01 * y2 + m02 * z2 + m03
        val py2 = m10 * x2 + m11 * y2 + m12 * z2 + m13
        val pz2 = m20 * x2 + m21 * y2 + m22 * z2 + m23
        val u2 = q[b + 13]
        val w2 = q[b + 14]
        data[14] = java.lang.Float.floatToRawIntBits(px2)
        data[15] = java.lang.Float.floatToRawIntBits(py2)
        data[16] = java.lang.Float.floatToRawIntBits(pz2)
        data[17] = java.lang.Float.floatToRawIntBits(u2)
        data[18] = java.lang.Float.floatToRawIntBits(w2)
        data[19] = packedColor
        data[20] = packedNormal

        val x3 = q[b + 15]
        val y3 = q[b + 16]
        val z3 = q[b + 17]
        val px3 = m00 * x3 + m01 * y3 + m02 * z3 + m03
        val py3 = m10 * x3 + m11 * y3 + m12 * z3 + m13
        val pz3 = m20 * x3 + m21 * y3 + m22 * z3 + m23
        val u3 = q[b + 18]
        val w3 = q[b + 19]
        data[21] = java.lang.Float.floatToRawIntBits(px3)
        data[22] = java.lang.Float.floatToRawIntBits(py3)
        data[23] = java.lang.Float.floatToRawIntBits(pz3)
        data[24] = java.lang.Float.floatToRawIntBits(u3)
        data[25] = java.lang.Float.floatToRawIntBits(w3)
        data[26] = packedColor
        data[27] = packedNormal

        val t2 = if (timing) System.nanoTime() else 0L

        writer.write(data, 0, 4 * 7)

        val t3 = if (timing) System.nanoTime() else 0L

        if (t0 != 0L) {
            RenderStats.addGeckoQuadBulkNanos(t3 - t0, 4)
            RenderStats.addGeckoQuadBulkStageNanos(
                normalNanos = t1 - t0,
                packNanos = t2 - t1,
                submitNanos = t3 - t2,
            )
        }
    }

    private data class BakeTint(
        val red: Float,
        val green: Float,
        val blue: Float,
        val alpha: Float,
    ) {
        val packedColor: Int = GeckoBakerMath.packColor(red, green, blue, alpha)

        // For BufferBuilder.color(r,g,b,a)
        val rI: Int = packedColor and 0xFF
        val gI: Int = (packedColor ushr 8) and 0xFF
        val bI: Int = (packedColor ushr 16) and 0xFF
        val aI: Int = (packedColor ushr 24) and 0xFF
    }

    private class Scratch(
        val matrices: GeckoAffineMatrices = GeckoAffineMatrices(),
        val boneMatrices: GeckoAffineMatrices = GeckoAffineMatrices(),
        val quadVertexData: IntArray = IntArray(4 * 7),
    ) {
        var packed: IntArray = IntArray(0)

        fun ensurePackedExactIntCount(intCount: Int) {
            if (intCount <= 0) {
                packed = IntArray(0)
                return
            }
            if (packed.size != intCount) {
                packed = IntArray(intCount)
            }
        }
    }

    private fun renderRecursively(
        builder: BufferBuilder,
        ms: MatrixStack,
        bone: GeoBone,
        tint: BakeTint,
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

            val boneMats = scratch.boneMatrices
            boneMats.loadFrom(ms)

            var ci = 0
            while (ci < cn) {
                val cube = cubes[ci]
                val cubeLocal = GeckoCubeLocalMatrixCache.get(cube)
                scratch.matrices.setMul(boneMats, cubeLocal)
                renderCubeWithMatrices(builder, cube, tint, scratch)
                ci++
            }
        }

        if (!bone.childBonesAreHiddenToo()) {
            val children = bone.childBones
            var bi = 0
            val bn = children.size
            while (bi < bn) {
                renderRecursively(builder, ms, children[bi], tint, scratch)
                bi++
            }
        }

        ms.pop()
    }

    private fun renderRecursivelyRouted(
        ms: MatrixStack,
        bone: GeoBone,
        bufferSelector: (bloom: Boolean, transparent: Boolean) -> BufferBuilder,
        tint: BakeTint,
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

            val boneMats = scratch.boneMatrices
            boneMats.loadFrom(ms)

            var ci = 0
            while (ci < cn) {
                val cube = cubes[ci]
                val cubeLocal = GeckoCubeLocalMatrixCache.get(cube)
                scratch.matrices.setMul(boneMats, cubeLocal)
                renderCubeWithMatrices(builder, cube, tint, scratch)
                ci++
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
                    tint = tint,
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
        ms: MatrixStack,
        bone: GeoBone,
        bufferSelector: (bloom: Boolean, transparent: Boolean) -> BufferBuilder,
        potentialAnimatedBones: Set<String>,
        activeAnimatedBones: Set<String>,
        mode: BakeMode,
        tint: BakeTint,
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

            val boneMats = scratch.boneMatrices
            boneMats.loadFrom(ms)

            var ci = 0
            while (ci < cn) {
                val cube = cubes[ci]
                val cubeLocal = GeckoCubeLocalMatrixCache.get(cube)
                scratch.matrices.setMul(boneMats, cubeLocal)
                renderCubeWithMatrices(builder, cube, tint, scratch)
                ci++
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
                    tint = tint,
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
        tint: BakeTint,
        mats: GeckoAffineMatrices,
        scratch: Scratch,
        flatFlags: Int,
        baked: FloatArray,
        bakedBase: Int,
    ) {
        val timing = RenderStats.enabled
        val t0 = if (timing) System.nanoTime() else 0L

        val qnx = quad.normal.x.toFloat()
        val qny = quad.normal.y.toFloat()
        val qnz = quad.normal.z.toFloat()

        var nx = mats.n00 * qnx + mats.n01 * qny + mats.n02 * qnz
        var ny = mats.n10 * qnx + mats.n11 * qny + mats.n12 * qnz
        var nz = mats.n20 * qnx + mats.n21 * qny + mats.n22 * qnz

        // Fix dark shading for flat cubes (mirrors GeckoLib behavior)
        if ((flatFlags and 1) != 0 && nx < 0) nx = -nx
        if ((flatFlags and 2) != 0 && ny < 0) ny = -ny
        if ((flatFlags and 4) != 0 && nz < 0) nz = -nz

        val t1 = if (timing) System.nanoTime() else 0L

        val packedColor = tint.packedColor
        val packedNormal = GeckoBakerMath.packNormal(nx, ny, nz)

        val verts = quad.vertices
        val vn = verts.size
        check(vn == 4) { "GeoQuad.vertices must have size 4, got $vn" }

        RenderStats.addGeckoBulkQuad()

        val data = scratch.quadVertexData

        val q = baked
        val b = bakedBase

        // Hot path: keep it allocation-free and JIT-friendly.
        val m00 = mats.m00
        val m01 = mats.m01
        val m02 = mats.m02
        val m03 = mats.m03
        val m10 = mats.m10
        val m11 = mats.m11
        val m12 = mats.m12
        val m13 = mats.m13
        val m20 = mats.m20
        val m21 = mats.m21
        val m22 = mats.m22
        val m23 = mats.m23

        val x0 = q[b + 0]
        val y0 = q[b + 1]
        val z0 = q[b + 2]
        val px0 = m00 * x0 + m01 * y0 + m02 * z0 + m03
        val py0 = m10 * x0 + m11 * y0 + m12 * z0 + m13
        val pz0 = m20 * x0 + m21 * y0 + m22 * z0 + m23
        val u0 = q[b + 3]
        val w0 = q[b + 4]
        data[0] = java.lang.Float.floatToRawIntBits(px0)
        data[1] = java.lang.Float.floatToRawIntBits(py0)
        data[2] = java.lang.Float.floatToRawIntBits(pz0)
        data[3] = java.lang.Float.floatToRawIntBits(u0)
        data[4] = java.lang.Float.floatToRawIntBits(w0)
        data[5] = packedColor
        data[6] = packedNormal

        val x1 = q[b + 5]
        val y1 = q[b + 6]
        val z1 = q[b + 7]
        val px1 = m00 * x1 + m01 * y1 + m02 * z1 + m03
        val py1 = m10 * x1 + m11 * y1 + m12 * z1 + m13
        val pz1 = m20 * x1 + m21 * y1 + m22 * z1 + m23
        val u1 = q[b + 8]
        val w1 = q[b + 9]
        data[7] = java.lang.Float.floatToRawIntBits(px1)
        data[8] = java.lang.Float.floatToRawIntBits(py1)
        data[9] = java.lang.Float.floatToRawIntBits(pz1)
        data[10] = java.lang.Float.floatToRawIntBits(u1)
        data[11] = java.lang.Float.floatToRawIntBits(w1)
        data[12] = packedColor
        data[13] = packedNormal

        val x2 = q[b + 10]
        val y2 = q[b + 11]
        val z2 = q[b + 12]
        val px2 = m00 * x2 + m01 * y2 + m02 * z2 + m03
        val py2 = m10 * x2 + m11 * y2 + m12 * z2 + m13
        val pz2 = m20 * x2 + m21 * y2 + m22 * z2 + m23
        val u2 = q[b + 13]
        val w2 = q[b + 14]
        data[14] = java.lang.Float.floatToRawIntBits(px2)
        data[15] = java.lang.Float.floatToRawIntBits(py2)
        data[16] = java.lang.Float.floatToRawIntBits(pz2)
        data[17] = java.lang.Float.floatToRawIntBits(u2)
        data[18] = java.lang.Float.floatToRawIntBits(w2)
        data[19] = packedColor
        data[20] = packedNormal

        val x3 = q[b + 15]
        val y3 = q[b + 16]
        val z3 = q[b + 17]
        val px3 = m00 * x3 + m01 * y3 + m02 * z3 + m03
        val py3 = m10 * x3 + m11 * y3 + m12 * z3 + m13
        val pz3 = m20 * x3 + m21 * y3 + m22 * z3 + m23
        val u3 = q[b + 18]
        val w3 = q[b + 19]
        data[21] = java.lang.Float.floatToRawIntBits(px3)
        data[22] = java.lang.Float.floatToRawIntBits(py3)
        data[23] = java.lang.Float.floatToRawIntBits(pz3)
        data[24] = java.lang.Float.floatToRawIntBits(u3)
        data[25] = java.lang.Float.floatToRawIntBits(w3)
        data[26] = packedColor
        data[27] = packedNormal

        val t2 = if (timing) System.nanoTime() else 0L

        builder.addVertexData(data)

        val t3 = if (timing) System.nanoTime() else 0L

        if (t0 != 0L) {
            RenderStats.addGeckoQuadBulkNanos(t3 - t0, 4)
            RenderStats.addGeckoQuadBulkStageNanos(
                normalNanos = t1 - t0,
                packNanos = t2 - t1,
                submitNanos = t3 - t2,
            )
        }
    }

    private fun renderCubeWithMatrices(
        builder: BufferBuilder,
        cube: GeoCube,
        tint: BakeTint,
        scratch: Scratch,
    ) {
        val mats = scratch.matrices
        val quads = cube.quads
        val cubeFlatFlags = GeckoBakerMath.flatFlagsForCube(cube)

        // Cache vertex attributes per-cube (cheaper than per-quad cache lookups).
        val baked = GeckoCubeQuadVertexCache.getOrBake(cube).xyzuv
        var bakedBase = 0

        var qi = 0
        val qn = quads.size
        while (qi < qn) {
            val quad = quads[qi]
            if (quad != null) {
                renderQuad(
                    builder = builder,
                    quad = quad,
                    tint = tint,
                    mats = mats,
                    scratch = scratch,
                    flatFlags = cubeFlatFlags,
                    baked = baked,
                    bakedBase = bakedBase,
                )
                bakedBase += 20
            }
            qi++
        }
    }
}
