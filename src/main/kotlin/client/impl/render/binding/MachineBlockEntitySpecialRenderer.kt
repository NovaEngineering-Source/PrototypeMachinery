package github.kasuminova.prototypemachinery.client.impl.render.binding

import github.kasuminova.prototypemachinery.client.api.render.RenderKey
import github.kasuminova.prototypemachinery.client.api.render.Renderable
import github.kasuminova.prototypemachinery.client.api.render.binding.GeckoModelBinding
import github.kasuminova.prototypemachinery.client.api.render.binding.GeckoStructureBinding
import github.kasuminova.prototypemachinery.client.api.render.binding.SliceRenderMode
import github.kasuminova.prototypemachinery.client.impl.render.MachineRenderDispatcher
import github.kasuminova.prototypemachinery.client.impl.render.gecko.GeckoModelRenderBuildTask
import github.kasuminova.prototypemachinery.client.impl.render.gecko.GeckoRenderSnapshot
import github.kasuminova.prototypemachinery.client.impl.render.task.BuiltBuffers
import github.kasuminova.prototypemachinery.client.impl.render.task.RenderTaskCache
import github.kasuminova.prototypemachinery.common.block.MachineBlock
import github.kasuminova.prototypemachinery.common.block.entity.MachineBlockEntity
import github.kasuminova.prototypemachinery.impl.machine.structure.StructureRegistryImpl
import github.kasuminova.prototypemachinery.impl.machine.structure.StructureUtils
import github.kasuminova.prototypemachinery.impl.machine.structure.match.StructureMatchContextImpl
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
import net.minecraft.util.EnumFacing
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos

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

        // --- Structure-level bindings (new preferred path) ---
        val structureBindings = ClientRenderBindingRegistryImpl.getStructureBindings(te.machine.type.id)
        if (structureBindings.isNotEmpty()) {
            // Determine orientation in the same way as server-side structure checking.
            val state = world.getBlockState(te.pos)
            val facing = runCatching { state.getValue(MachineBlock.FACING) }.getOrDefault(EnumFacing.NORTH)
            val top = te.getTopFacing(facing)
            val orientation = github.kasuminova.prototypemachinery.api.machine.structure.StructureOrientation(front = facing, top = top)

            val rootStructure = StructureRegistryImpl.get(te.machine.type.structure.id, orientation, facing)
                ?: te.machine.type.structure.transform { it }

            val context = StructureMatchContextImpl(te.machine)
            val matched = runCatching { rootStructure.matches(context, te.pos) }.getOrDefault(false)
            val rootInstance = if (matched) context.getRootInstance() else null

            if (rootInstance != null) {
                val anchors = ClientStructureRenderAnchors.collectAnchors(
                    rootStructure,
                    rootInstance,
                    controllerPos = te.pos,
                    resolveSliceMode = { s ->
                        // mode is binding-driven; default STRUCTURE_ONLY
                        structureBindings[s.id]?.sliceRenderMode ?: SliceRenderMode.STRUCTURE_ONLY
                    },
                )

                // Collect all render data and submit to dispatcher for centralized batch rendering.
                // The dispatcher will render all machines' opaque content first, then all transparent.
                for (anchor in anchors) {
                    val binding = structureBindings[anchor.structure.id] ?: continue
                    collectStructureBound(te, binding, anchor, resourcesRoot)?.let { data ->
                        MachineRenderDispatcher.submit(data.toPendingRenderData())
                    }
                }
                return
            }
        }

        // --- Legacy bindings (fallback) ---
        val componentBindings = ClientRenderBindingRegistryImpl.getComponentBindings()

        // 1) machine-type binding
        val machineBinding = ClientRenderBindingRegistryImpl.getMachineBinding(te.machine.type.id)
        if (machineBinding != null) {
            collectLegacy(te, machineBinding, resourcesRoot, bindingKey = te.machine.type.id)?.let { data ->
                MachineRenderDispatcher.submit(data.toPendingRenderData())
            }
        }

        // 2) component-type bindings
        if (componentBindings.isNotEmpty()) {
            for ((componentTypeId, binding) in componentBindings) {
                if (!te.machine.componentMap.containsComponentTypeId(componentTypeId)) continue
                collectLegacy(te, binding, resourcesRoot, bindingKey = componentTypeId)?.let { data ->
                    MachineRenderDispatcher.submit(data.toPendingRenderData())
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
        val built: BuiltBuffers,
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
    ): CollectedRenderData? {
        val state = te.world.getBlockState(te.pos)
        val front = runCatching { state.getValue(MachineBlock.FACING) }.getOrDefault(EnumFacing.NORTH)
        val top = te.getTopFacing(front)

        val ownerKey = RenderOwnerKey(te, bindingKey)

        val renderKey = RenderKey(
            modelId = binding.geo,
            textureId = binding.texture,
            variant = bindingKey.hashCode(),
            animationStateHash = 0,
            secureVersion = 0,
            flags = 0,
            orientationHash = front.ordinal * 24 + top.ordinal * 4 + te.twist,
        )

        val renderable = object : Renderable {
            override val ownerKey: Any = ownerKey
            override val renderKey: RenderKey = renderKey
            override val x: Double = te.pos.x.toDouble()
            override val y: Double = te.pos.y.toDouble()
            override val z: Double = te.pos.z.toDouble()
            override val facing = front
            override val combinedLight: Int = te.world.getCombinedLight(te.pos, 0)
        }

        val snapshot = GeckoRenderSnapshot(
            ownerKey = ownerKey,
            renderKey = renderKey,
            pass = binding.pass,
            geoLocation = binding.geo,
            textureLocation = binding.texture,
            x = renderable.x,
            y = renderable.y,
            z = renderable.z,
            modelOffsetX = binding.modelOffsetX,
            modelOffsetY = binding.modelOffsetY,
            modelOffsetZ = binding.modelOffsetZ,
            front = front,
            top = top,
            resourcesRoot = resourcesRoot,
            yOffset = binding.yOffset,
        )

        val task = RenderTaskCache.getOrSubmit(renderable) {
            GeckoModelRenderBuildTask(snapshot)
        }

        val built = task.takeBuilt() ?: return null
        if (built.isEmpty()) return null

        return CollectedRenderData(
            texture = renderable.renderKey.textureId,
            combinedLight = renderable.combinedLight,
            built = built,
        )
    }

    private fun collectStructureBound(
        te: MachineBlockEntity,
        binding: GeckoStructureBinding,
        anchor: ClientStructureRenderAnchors.Anchor,
        resourcesRoot: java.nio.file.Path,
    ): CollectedRenderData? {
        val model = binding.model

        val state = te.world.getBlockState(te.pos)
        val front = runCatching { state.getValue(MachineBlock.FACING) }.getOrDefault(EnumFacing.NORTH)
        val top = te.getTopFacing(front)

        val bindingKey = StructureBindingKey(
            machineTypeId = te.machine.type.id,
            structureId = anchor.structure.id,
            sliceIndex = anchor.sliceIndex,
        )

        val ownerKey = RenderOwnerKey(te, bindingKey)

        val renderKey = RenderKey(
            modelId = model.geo,
            textureId = model.texture,
            variant = bindingKey.hashCode(),
            animationStateHash = 0,
            secureVersion = 0,
            flags = 0,
            orientationHash = front.ordinal * 24 + top.ordinal * 4 + te.twist,
        )

        val renderable = object : Renderable {
            override val ownerKey: Any = ownerKey
            override val renderKey: RenderKey = renderKey
            // Keep renderable positioned at the controller for stable semantics; actual placement uses snapshot.modelOffset.
            override val x: Double = te.pos.x.toDouble()
            override val y: Double = te.pos.y.toDouble()
            override val z: Double = te.pos.z.toDouble()
            override val facing = front
            override val combinedLight: Int = te.world.getCombinedLight(anchor.worldOrigin, te.world.getLight(te.pos))
        }

        val deltaX = (anchor.worldOrigin.x - te.pos.x).toDouble()
        val deltaY = (anchor.worldOrigin.y - te.pos.y).toDouble()
        val deltaZ = (anchor.worldOrigin.z - te.pos.z).toDouble()

        // Convert the world-space anchor delta back into the base structure coordinate system (NORTH/UP).
        // We'll apply the (front/top) orientation in GeckoModelRenderBuildTask so all offsets rotate together.
        val rotation: (EnumFacing) -> EnumFacing = { facing ->
            val baseFront = EnumFacing.NORTH
            val baseTop = EnumFacing.UP
            val baseRight = EnumFacing.EAST

            val targetFront = front
            val targetTop = top
            val targetRight = github.kasuminova.prototypemachinery.api.machine.structure.StructureOrientation(targetFront, targetTop).right

            when (facing) {
                baseFront -> targetFront
                baseFront.opposite -> targetFront.opposite
                baseTop -> targetTop
                baseTop.opposite -> targetTop.opposite
                baseRight -> targetRight
                baseRight.opposite -> targetRight.opposite
                else -> facing
            }
        }

        val inverseRotation: (EnumFacing) -> EnumFacing = run {
            val inverseMap = HashMap<EnumFacing, EnumFacing>(EnumFacing.values().size)
            for (f in EnumFacing.values()) {
                inverseMap[rotation(f)] = f
            }
            return@run { f: EnumFacing -> inverseMap[f] ?: f }
        }

        val localDelta = StructureUtils.rotatePos(
            BlockPos(deltaX.toInt(), deltaY.toInt(), deltaZ.toInt()),
            inverseRotation
        )

        val snapshot = GeckoRenderSnapshot(
            ownerKey = ownerKey,
            renderKey = renderKey,
            pass = model.pass,
            geoLocation = model.geo,
            textureLocation = model.texture,
            x = renderable.x,
            y = renderable.y,
            z = renderable.z,
            // Structure anchor delta + user modelOffset, both expressed in base/local structure coordinates.
            // They will be rotated by (front/top) in GeckoModelRenderBuildTask.
            modelOffsetX = localDelta.x.toDouble() + model.modelOffsetX,
            modelOffsetY = localDelta.y.toDouble() + model.modelOffsetY,
            modelOffsetZ = localDelta.z.toDouble() + model.modelOffsetZ,
            front = front,
            top = top,
            resourcesRoot = resourcesRoot,
            yOffset = model.yOffset,
        )

        val task = RenderTaskCache.getOrSubmit(renderable) {
            GeckoModelRenderBuildTask(snapshot)
        }

        val built = task.takeBuilt() ?: return null
        if (built.isEmpty()) return null

        return CollectedRenderData(
            texture = renderable.renderKey.textureId,
            combinedLight = renderable.combinedLight,
            built = built,
        )
    }

    private data class RenderOwnerKey(
        private val te: MachineBlockEntity,
        private val bindingKey: Any,
    )

    private data class StructureBindingKey(
        private val machineTypeId: ResourceLocation,
        private val structureId: String,
        private val sliceIndex: Int,
    ) {
        override fun toString(): String = "$machineTypeId/$structureId#$sliceIndex"
    }

    private fun github.kasuminova.prototypemachinery.api.machine.component.MachineComponentMap.containsComponentTypeId(id: ResourceLocation): Boolean {
        // MVP: linear scan. Can be optimized later by keeping an id->type index.
        return this.components.keys.any { it.id == id }
    }
}
