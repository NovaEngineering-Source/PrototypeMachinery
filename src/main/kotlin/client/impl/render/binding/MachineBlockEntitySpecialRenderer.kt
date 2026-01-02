package github.kasuminova.prototypemachinery.client.impl.render.binding

import github.kasuminova.prototypemachinery.api.machine.component.type.GeckoModelStateComponent
import github.kasuminova.prototypemachinery.api.machine.component.type.GeckoModelStateComponentType
import github.kasuminova.prototypemachinery.api.machine.component.type.StructureRenderDataComponentType
import github.kasuminova.prototypemachinery.api.machine.component.type.ZSDataComponent
import github.kasuminova.prototypemachinery.api.machine.component.type.ZSDataComponentType
import github.kasuminova.prototypemachinery.client.api.render.RenderKey
import github.kasuminova.prototypemachinery.client.api.render.Renderable
import github.kasuminova.prototypemachinery.client.api.render.binding.GeckoModelBinding
import github.kasuminova.prototypemachinery.client.api.render.binding.GeckoStructureBinding
import github.kasuminova.prototypemachinery.client.api.render.binding.SliceRenderMode
import github.kasuminova.prototypemachinery.client.impl.render.MachineRenderDispatcher
import github.kasuminova.prototypemachinery.client.impl.render.RenderFrameClock
import github.kasuminova.prototypemachinery.client.impl.render.gecko.GeckoModelBaker
import github.kasuminova.prototypemachinery.client.impl.render.gecko.GeckoModelRenderBuildTask
import github.kasuminova.prototypemachinery.client.impl.render.gecko.GeckoRenderSnapshot
import github.kasuminova.prototypemachinery.client.impl.render.task.BuiltBuffers
import github.kasuminova.prototypemachinery.client.impl.render.task.RenderTaskCache
import github.kasuminova.prototypemachinery.client.impl.render.task.RenderTaskOwnerKeys
import github.kasuminova.prototypemachinery.common.block.MachineBlock
import github.kasuminova.prototypemachinery.common.block.entity.MachineBlockEntity
import github.kasuminova.prototypemachinery.impl.machine.structure.StructureRegistryImpl
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
import net.minecraft.util.EnumFacing
import net.minecraft.util.ResourceLocation

/**
 * TESR for MachineBlockEntity.
 *
 * This TESR only collects render data and submits it to MachineRenderDispatcher.
 * The actual rendering is performed centrally by MachineRenderDispatcher after all TESRs complete,
 * ensuring correct render order across all machines (all opaque first, then all transparent).
 *
 * Why TESR?
 * GregTech's BloomEffectUtil callback is injected inside EntityRenderer.renderWorldPass
 * *before* Forge's RenderWorldLastEvent (dispatchRenderLast). If we render the base model
 * from RenderWorldLastEvent, the bloom pass ends up running earlier than the base render,
 * causing incorrect ordering and washed-out bloom.
 */
internal class MachineBlockEntitySpecialRenderer : TileEntitySpecialRenderer<MachineBlockEntity>() {

    override fun isGlobalRenderer(te: MachineBlockEntity): Boolean {
        // Called by the dispatcher during culling checks.
        val machine = try {
            te.machine
        } catch (_: UninitializedPropertyAccessException) {
            return false
        }

        // 1) structure bindings
        val structureBindings = ClientRenderBindingRegistryImpl.getStructureBindings(machine.type.id)
        if (structureBindings.isNotEmpty()) {
            if (structureBindings.values.any { it.model.forceGlobalRenderer }) return true
        }

        // 2) legacy bindings (fallback)
        val machineBinding = ClientRenderBindingRegistryImpl.getMachineBinding(machine.type.id)
        if (machineBinding?.forceGlobalRenderer == true) return true

        val componentBindings = ClientRenderBindingRegistryImpl.getComponentBindings()
        if (componentBindings.isNotEmpty()) {
            for ((componentTypeId, binding) in componentBindings) {
                if (!binding.forceGlobalRenderer) continue
                if (!machine.componentMap.containsComponentTypeId(componentTypeId)) continue
                return true
            }
        }

        return false
    }

    override fun render(
        te: MachineBlockEntity,
        x: Double,
        y: Double,
        z: Double,
        partialTicks: Float,
        destroyStage: Int,
        alpha: Float,
    ) {
        // We intentionally ignore (x,y,z) and render in world coordinates with a camera-origin translation,
        // matching RenderManager's behavior.
        // Kotlin 1.2 on 1.12.2 does not support bound property references like te::machine.isInitialized.
        // Accessing an uninitialized lateinit will throw, so use a cheap guard.
        try {
            te.machine
        } catch (_: UninitializedPropertyAccessException) {
            return
        }

        val mc = Minecraft.getMinecraft()
        val world = mc.world ?: return
        val resourcesRoot = mc.gameDir.toPath().resolve("resources")

        // Chunk coordinates for spatial bucketing (controller position).
        val chunkX = te.pos.x shr 4
        val chunkZ = te.pos.z shr 4

        // --- Structure-level bindings (new preferred path) ---
        val structureBindings = ClientRenderBindingRegistryImpl.getStructureBindings(te.machine.type.id)
        if (structureBindings.isNotEmpty()) {
            // IMPORTANT:
            // Never run structure matching on the render thread.
            // Server already matches on a schedule and syncs formed + slice counts.
            if (te.machine.isFormed()) {
                val sliceCounts = (te.machine.componentMap.get(StructureRenderDataComponentType)
                    as? github.kasuminova.prototypemachinery.api.machine.component.type.StructureRenderDataComponent)
                    ?.sliceCounts
                    ?: emptyMap()

                // Determine orientation in the same way as server-side structure checking.
                val state = world.getBlockState(te.pos)
                val facing = runCatching { state.getValue(MachineBlock.FACING) }.getOrDefault(EnumFacing.NORTH)
                val top = te.getTopFacing(facing)
                val orientation = github.kasuminova.prototypemachinery.api.machine.structure.StructureOrientation(front = facing, top = top)

                val rootStructure = StructureRegistryImpl.get(te.machine.type.structure.id, orientation, facing)
                    ?: te.machine.type.structure.transform { it }

                val anchors = ClientStructureRenderAnchors.collectAnchorsFromSliceCounts(
                    rootStructure,
                    controllerPos = te.pos,
                    sliceCountsById = sliceCounts,
                    resolveSliceMode = { s ->
                        // mode is binding-driven; default STRUCTURE_ONLY
                        structureBindings[s.id]?.sliceRenderMode ?: SliceRenderMode.STRUCTURE_ONLY
                    },
                )

                for (anchor in anchors) {
                    val binding = structureBindings[anchor.structure.id] ?: continue
                    val list = collectStructureBound(te, binding, anchor, resourcesRoot)
                    if (list.isNotEmpty()) {
                        MachineRenderDispatcher.submitAll(list.map { it.toPendingRenderData() })
                    }
                }
                return
            }

            // Determine orientation in the same way as server-side structure checking.
            val state = world.getBlockState(te.pos)
            val facing = runCatching { state.getValue(MachineBlock.FACING) }.getOrDefault(EnumFacing.NORTH)
            val top = te.getTopFacing(facing)
            val orientation = github.kasuminova.prototypemachinery.api.machine.structure.StructureOrientation(front = facing, top = top)

            val rootStructure = StructureRegistryImpl.get(te.machine.type.structure.id, orientation, facing)
                ?: te.machine.type.structure.transform { it }

            // If not formed, fall back to legacy bindings below.
        }

        // --- Legacy bindings (fallback) ---
        val componentBindings = ClientRenderBindingRegistryImpl.getComponentBindings()

        // 1) machine-type binding
        val machineBinding = ClientRenderBindingRegistryImpl.getMachineBinding(te.machine.type.id)
        if (machineBinding != null) {
            val list = collectLegacy(te, machineBinding, resourcesRoot, bindingKey = te.machine.type.id)
            if (list.isNotEmpty()) {
                MachineRenderDispatcher.submitAll(list.map { it.toPendingRenderData() })
            }
        }

        // 2) component-type bindings
        if (componentBindings.isNotEmpty()) {
            for ((componentTypeId, binding) in componentBindings) {
                if (!te.machine.componentMap.containsComponentTypeId(componentTypeId)) continue
                val list = collectLegacy(te, binding, resourcesRoot, bindingKey = componentTypeId)
                if (list.isNotEmpty()) {
                    MachineRenderDispatcher.submitAll(list.map { it.toPendingRenderData() })
                }
            }
        }
    }

    /**
     * Holds collected render data for deferred pass-ordered rendering.
     */
    private data class CollectedRenderData(
        val texture: ResourceLocation,
        val combinedLight: Int,
        val built: BuiltBuffers
    ) {
        fun toPendingRenderData(): MachineRenderDispatcher.PendingRenderData {
            return MachineRenderDispatcher.PendingRenderData(
                texture = texture,
                combinedLight = combinedLight,
                built = built,
            )
        }
    }

    private fun collectLegacy(
        te: MachineBlockEntity,
        binding: GeckoModelBinding,
        resourcesRoot: java.nio.file.Path,
        bindingKey: ResourceLocation,
    ): List<CollectedRenderData> {
        val state = te.world.getBlockState(te.pos)
        val front = runCatching { state.getValue(MachineBlock.FACING) }.getOrDefault(EnumFacing.NORTH)
        val top = te.getTopFacing(front)

        val geckoState = te.machine.componentMap[GeckoModelStateComponentType] as? GeckoModelStateComponent
        val animationNames = resolveAnimationNames(te, binding, geckoState)

        val animTick = binding.animation?.let {
            // Use tick-rate sampling for caching (avoid per-frame churn).
            (te.world.totalWorldTime % Int.MAX_VALUE).toInt()
        } ?: 0

        // Optional render-synced time key for smoother animation.
        // Uses a per-frame snapshot to avoid pass-to-pass time skew.
        val animTimeKey = if (binding.animation != null) RenderFrameClock.currentAnimationTimeKey() else 0

        val variantBase = run {
            var v = bindingKey.hashCode()
            v = 31 * v + (binding.animation?.hashCode() ?: 0)
            v
        }

        val variantForAnim = run {
            var v = variantBase
            v = 31 * v + hashStringList(animationNames)
            v = 31 * v + (geckoState?.stateVersion ?: 0)
            v
        }

        fun baseKey(animationStateHash: Int, animationTimeKey: Int, variant: Int): RenderKey = RenderKey(
            modelId = binding.geo,
            textureId = binding.texture,
            variant = variant,
            animationStateHash = animationStateHash,
            animationTimeKey = animationTimeKey,
            secureVersion = 0,
            flags = 0,
            orientationHash = front.ordinal * 24 + top.ordinal * 4 + te.twist,
        )

        fun baseRenderable(ownerKey: Any, renderKey: RenderKey): Renderable {
            return object : Renderable {
                override val ownerKey: Any = ownerKey
                override val renderKey: RenderKey = renderKey
                override val x: Double = te.pos.x.toDouble()
                override val y: Double = te.pos.y.toDouble()
                override val z: Double = te.pos.z.toDouble()
                override val facing = front
                override val combinedLight: Int = te.world.getCombinedLight(te.pos, 0)
            }
        }

        fun baseSnapshot(ownerKey: Any, renderKey: RenderKey, bakeMode: GeckoModelBaker.BakeMode): GeckoRenderSnapshot {
            return GeckoRenderSnapshot(
                ownerKey = ownerKey,
                renderKey = renderKey,
                pass = binding.pass,
                geoLocation = binding.geo,
                textureLocation = binding.texture,
                animationLocation = binding.animation,
                animationNames = animationNames,
                bakeMode = bakeMode,
                x = te.pos.x.toDouble(),
                y = te.pos.y.toDouble(),
                z = te.pos.z.toDouble(),
                modelOffsetX = binding.modelOffsetX,
                modelOffsetY = binding.modelOffsetY,
                modelOffsetZ = binding.modelOffsetZ,
                front = front,
                top = top,
                resourcesRoot = resourcesRoot,
                yOffset = binding.yOffset,
            )
        }

        val out = ArrayList<CollectedRenderData>(2)

        if (binding.animation == null) {
            val ownerKey = RenderTaskOwnerKeys.legacyOwnerKey(te, bindingKey, RenderPart.ALL.ordinal, RenderPart.values().size)
            val rk = baseKey(animationStateHash = animTick, animationTimeKey = 0, variant = variantBase)
            val renderable = baseRenderable(ownerKey, rk)
            val snapshot = baseSnapshot(ownerKey, rk, GeckoModelBaker.BakeMode.ALL)

            val task = RenderTaskCache.getOrSubmit(renderable) { GeckoModelRenderBuildTask(snapshot) }
            val built = task.takeBuilt()
            if (built == null) {
                // Task still building, skip this frame but don't abort.
                return emptyList()
            }
            if (!built.isEmpty()) {
                out.add(
                    CollectedRenderData(
                        texture = rk.textureId,
                        combinedLight = renderable.combinedLight,
                        built = built,
                    )
                )
            }
            return out
        }

        // Permanent static task: cache across animation switches.
        run {
            val ownerKey = RenderTaskOwnerKeys.legacyOwnerKey(te, bindingKey, RenderPart.PERMANENT_STATIC.ordinal, RenderPart.values().size)
            val rk = baseKey(animationStateHash = 0, animationTimeKey = 0, variant = variantBase)
            val renderable = baseRenderable(ownerKey, rk)
            val snapshot = baseSnapshot(ownerKey, rk, GeckoModelBaker.BakeMode.PERMANENT_STATIC_ONLY)

            val task = RenderTaskCache.getOrSubmit(renderable) { GeckoModelRenderBuildTask(snapshot) }
            val built = task.takeBuilt() ?: return@run
            if (!built.isEmpty()) {
                out.add(
                    CollectedRenderData(
                        texture = rk.textureId,
                        combinedLight = renderable.combinedLight,
                        built = built,
                    )
                )
            }
        }

        // Temporary static task: cache until animation selection changes.
        run {
            val ownerKey = RenderTaskOwnerKeys.legacyOwnerKey(te, bindingKey, RenderPart.TEMP_STATIC.ordinal, RenderPart.values().size)
            val rk = baseKey(animationStateHash = 0, animationTimeKey = 0, variant = variantForAnim)
            val renderable = baseRenderable(ownerKey, rk)
            val snapshot = baseSnapshot(ownerKey, rk, GeckoModelBaker.BakeMode.TEMP_STATIC_ONLY)

            val task = RenderTaskCache.getOrSubmit(renderable) { GeckoModelRenderBuildTask(snapshot) }
            val built = task.takeBuilt() ?: return@run
            if (!built.isEmpty()) {
                out.add(
                    CollectedRenderData(
                        texture = rk.textureId,
                        combinedLight = renderable.combinedLight,
                        built = built,
                    )
                )
            }
        }

        // Dynamic task: rebuild at tick-rate.
        run {
            val ownerKey = RenderTaskOwnerKeys.legacyOwnerKey(te, bindingKey, RenderPart.DYNAMIC.ordinal, RenderPart.values().size)
            val rk = baseKey(animationStateHash = animTick, animationTimeKey = animTimeKey, variant = variantForAnim)
            val renderable = baseRenderable(ownerKey, rk)
            val snapshot = baseSnapshot(ownerKey, rk, GeckoModelBaker.BakeMode.ANIMATED_ONLY)

            val task = RenderTaskCache.getOrSubmit(renderable) { GeckoModelRenderBuildTask(snapshot) }
            val built = task.takeBuilt() ?: return@run
            if (!built.isEmpty()) {
                out.add(
                    CollectedRenderData(
                        texture = rk.textureId,
                        combinedLight = renderable.combinedLight,
                        built = built,
                    )
                )
            }
        }

        return out
    }

    private fun collectStructureBound(
        te: MachineBlockEntity,
        binding: GeckoStructureBinding,
        anchor: ClientStructureRenderAnchors.Anchor,
        resourcesRoot: java.nio.file.Path,
    ): List<CollectedRenderData> {
        val model = binding.model

        val state = te.world.getBlockState(te.pos)
        val front = runCatching { state.getValue(MachineBlock.FACING) }.getOrDefault(EnumFacing.NORTH)
        val top = te.getTopFacing(front)

        // Binding identity seed (same as data-class hashCode, but without allocating a key object).
        val bindingVariantSeed = run {
            var h = te.machine.type.id.hashCode()
            h = 31 * h + anchor.structure.id.hashCode()
            h = 31 * h + anchor.sliceIndex
            h
        }

        val geckoState = te.machine.componentMap[GeckoModelStateComponentType] as? GeckoModelStateComponent
        val animationNames = resolveAnimationNames(te, model, geckoState)

        val animTick = model.animation?.let {
            (te.world.totalWorldTime % Int.MAX_VALUE).toInt()
        } ?: 0

        val animTimeKey = if (model.animation != null) RenderFrameClock.currentAnimationTimeKey() else 0

        val variantBase = run {
            var v = bindingVariantSeed
            v = 31 * v + (model.animation?.hashCode() ?: 0)
            v
        }

        val variantForAnim = run {
            var v = variantBase
            v = 31 * v + hashStringList(animationNames)
            v = 31 * v + (geckoState?.stateVersion ?: 0)
            v
        }

        fun baseKey(animationStateHash: Int, animationTimeKey: Int, variant: Int): RenderKey = RenderKey(
            modelId = model.geo,
            textureId = model.texture,
            variant = variant,
            animationStateHash = animationStateHash,
            animationTimeKey = animationTimeKey,
            secureVersion = 0,
            flags = 0,
            orientationHash = front.ordinal * 24 + top.ordinal * 4 + te.twist,
        )

        fun baseRenderable(ownerKey: Any, renderKey: RenderKey): Renderable {
            return object : Renderable {
                override val ownerKey: Any = ownerKey
                override val renderKey: RenderKey = renderKey
                // Keep renderable positioned at the controller for stable semantics; actual placement uses snapshot.modelOffset.
                override val x: Double = te.pos.x.toDouble()
                override val y: Double = te.pos.y.toDouble()
                override val z: Double = te.pos.z.toDouble()
                override val facing = front
                // MMCE uses getCombinedLight(controllerPos, 0). Using anchor.worldOrigin can land in an unlit interior block
                // (e.g. air inside structure), making the whole buffer sample as black.
                // We intentionally sample lighting at the controller position for stable, MMCE-consistent results.
                override val combinedLight: Int = te.world.getCombinedLight(te.pos, 0)
            }
        }

        val dx = anchor.worldOrigin.x - te.pos.x
        val dy = anchor.worldOrigin.y - te.pos.y
        val dz = anchor.worldOrigin.z - te.pos.z

        // Convert the world-space anchor delta back into the base structure coordinate system (NORTH/UP),
        // but without allocating lambdas / HashMaps like rotatePos(rotationFn).
        val targetFront = front
        val targetTop = top
        val targetRight = github.kasuminova.prototypemachinery.api.machine.structure.StructureOrientation(targetFront, targetTop).right

        fun invFacing(f: EnumFacing): EnumFacing = when (f) {
            targetFront -> EnumFacing.NORTH
            targetFront.opposite -> EnumFacing.SOUTH
            targetTop -> EnumFacing.UP
            targetTop.opposite -> EnumFacing.DOWN
            targetRight -> EnumFacing.EAST
            targetRight.opposite -> EnumFacing.WEST
            else -> f
        }

        val invEast = invFacing(EnumFacing.EAST).directionVec
        val invUp = invFacing(EnumFacing.UP).directionVec
        val invSouth = invFacing(EnumFacing.SOUTH).directionVec

        val localDx = dx * invEast.x + dy * invUp.x + dz * invSouth.x
        val localDy = dx * invEast.y + dy * invUp.y + dz * invSouth.y
        val localDz = dx * invEast.z + dy * invUp.z + dz * invSouth.z


        fun baseSnapshot(ownerKey: Any, renderKey: RenderKey, bakeMode: GeckoModelBaker.BakeMode): GeckoRenderSnapshot {
            return GeckoRenderSnapshot(
                ownerKey = ownerKey,
                renderKey = renderKey,
                pass = model.pass,
                geoLocation = model.geo,
                textureLocation = model.texture,
                animationLocation = model.animation,
                animationNames = animationNames,
                bakeMode = bakeMode,
                x = te.pos.x.toDouble(),
                y = te.pos.y.toDouble(),
                z = te.pos.z.toDouble(),
                // Structure anchor delta + user modelOffset, both expressed in base/local structure coordinates.
                // They will be rotated by (front/top) in GeckoModelRenderBuildTask.
                modelOffsetX = localDx.toDouble() + model.modelOffsetX,
                modelOffsetY = localDy.toDouble() + model.modelOffsetY,
                modelOffsetZ = localDz.toDouble() + model.modelOffsetZ,
                front = front,
                top = top,
                resourcesRoot = resourcesRoot,
                yOffset = model.yOffset,
            )
        }

        val out = ArrayList<CollectedRenderData>(2)

        if (model.animation == null) {
            val ownerKey = RenderTaskOwnerKeys.structureOwnerKey(
                te = te,
                machineTypeId = te.machine.type.id,
                structureId = anchor.structure.id,
                sliceIndex = anchor.sliceIndex,
                partOrdinal = RenderPart.ALL.ordinal,
                partCount = RenderPart.values().size,
            )
            val rk = baseKey(animationStateHash = animTick, animationTimeKey = 0, variant = variantBase)
            val renderable = baseRenderable(ownerKey, rk)
            val snapshot = baseSnapshot(ownerKey, rk, GeckoModelBaker.BakeMode.ALL)

            val task = RenderTaskCache.getOrSubmit(renderable) { GeckoModelRenderBuildTask(snapshot) }
            val built = task.takeBuilt() ?: return emptyList()
            if (!built.isEmpty()) {
                out.add(
                    CollectedRenderData(
                        texture = rk.textureId,
                        combinedLight = renderable.combinedLight,
                        built = built,
                    )
                )
            }
            return out
        }

        run {
            val ownerKey = RenderTaskOwnerKeys.structureOwnerKey(
                te = te,
                machineTypeId = te.machine.type.id,
                structureId = anchor.structure.id,
                sliceIndex = anchor.sliceIndex,
                partOrdinal = RenderPart.PERMANENT_STATIC.ordinal,
                partCount = RenderPart.values().size,
            )
            val rk = baseKey(animationStateHash = 0, animationTimeKey = 0, variant = variantBase)
            val renderable = baseRenderable(ownerKey, rk)
            val snapshot = baseSnapshot(ownerKey, rk, GeckoModelBaker.BakeMode.PERMANENT_STATIC_ONLY)

            val task = RenderTaskCache.getOrSubmit(renderable) { GeckoModelRenderBuildTask(snapshot) }
            val built = task.takeBuilt() ?: return@run
            if (!built.isEmpty()) {
                out.add(
                    CollectedRenderData(
                        texture = rk.textureId,
                        combinedLight = renderable.combinedLight,
                        built = built,
                    )
                )
            }
        }

        run {
            val ownerKey = RenderTaskOwnerKeys.structureOwnerKey(
                te = te,
                machineTypeId = te.machine.type.id,
                structureId = anchor.structure.id,
                sliceIndex = anchor.sliceIndex,
                partOrdinal = RenderPart.TEMP_STATIC.ordinal,
                partCount = RenderPart.values().size,
            )
            val rk = baseKey(animationStateHash = 0, animationTimeKey = 0, variant = variantForAnim)
            val renderable = baseRenderable(ownerKey, rk)
            val snapshot = baseSnapshot(ownerKey, rk, GeckoModelBaker.BakeMode.TEMP_STATIC_ONLY)

            val task = RenderTaskCache.getOrSubmit(renderable) { GeckoModelRenderBuildTask(snapshot) }
            val built = task.takeBuilt() ?: return@run
            if (!built.isEmpty()) {
                out.add(
                    CollectedRenderData(
                        texture = rk.textureId,
                        combinedLight = renderable.combinedLight,
                        built = built,
                    )
                )
            }
        }

        run {
            val ownerKey = RenderTaskOwnerKeys.structureOwnerKey(
                te = te,
                machineTypeId = te.machine.type.id,
                structureId = anchor.structure.id,
                sliceIndex = anchor.sliceIndex,
                partOrdinal = RenderPart.DYNAMIC.ordinal,
                partCount = RenderPart.values().size,
            )
            val rk = baseKey(animationStateHash = animTick, animationTimeKey = animTimeKey, variant = variantForAnim)
            val renderable = baseRenderable(ownerKey, rk)
            val snapshot = baseSnapshot(ownerKey, rk, GeckoModelBaker.BakeMode.ANIMATED_ONLY)

            val task = RenderTaskCache.getOrSubmit(renderable) { GeckoModelRenderBuildTask(snapshot) }
            val built = task.takeBuilt() ?: return@run
            if (!built.isEmpty()) {
                out.add(
                    CollectedRenderData(
                        texture = rk.textureId,
                        combinedLight = renderable.combinedLight,
                        built = built,
                    )
                )
            }
        }

        return out
    }

    private fun resolveAnimationNames(
        te: MachineBlockEntity,
        binding: GeckoModelBinding,
        geckoState: GeckoModelStateComponent?,
    ): List<String> {
        // No animation file -> no names.
        if (binding.animation == null) return emptyList()

        // Dedicated typed state component has highest priority.
        // 专用模型状态组件优先级最高。
        if (geckoState != null) {
            val fromComp = geckoState.animationLayers
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (fromComp.isNotEmpty()) return fromComp
        }

        val zsData = (te.machine.componentMap[ZSDataComponentType] as? ZSDataComponent)?.data

        binding.animationLayersStateKey?.let { key ->
            val raw = zsData?.getString(key, "")?.trim().orEmpty()
            if (raw.isNotEmpty()) {
                val parsed = splitCommaList(raw)
                if (parsed.isNotEmpty()) return parsed
            }
        }

        binding.animationStateKey?.let { key ->
            val raw = zsData?.getString(key, "")?.trim().orEmpty()
            if (raw.isNotEmpty()) return listOf(raw)
        }

        if (binding.animationLayers.isNotEmpty()) {
            return binding.animationLayers
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }

        return listOfNotNull(binding.defaultAnimationName?.trim()?.takeIf { it.isNotEmpty() })
    }

    private fun splitCommaList(raw: String): List<String> {
        return raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private enum class RenderPart {
        ALL,
        PERMANENT_STATIC,
        TEMP_STATIC,
        DYNAMIC,
    }

    private fun hashStringList(values: List<String>): Int {
        // A stable, allocation-free alternative to values.joinToString("\u0000").hashCode().
        // We mix the size and each element hash with a separator marker to reduce accidental collisions.
        var h = values.size
        for (s in values) {
            h = 31 * h + s.hashCode()
            h = 31 * h + 0x9E3779B9.toInt()
        }
        return h
    }

    private fun github.kasuminova.prototypemachinery.api.machine.component.MachineComponentMap.containsComponentTypeId(id: ResourceLocation): Boolean {
        // MVP: linear scan. Can be optimized later by keeping an id->type index.
        return this.components.keys.any { it.id == id }
    }
}
