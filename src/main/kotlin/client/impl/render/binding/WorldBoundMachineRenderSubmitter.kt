package github.kasuminova.prototypemachinery.client.impl.render.binding

import github.kasuminova.prototypemachinery.client.api.render.RenderKey
import github.kasuminova.prototypemachinery.client.api.render.Renderable
import github.kasuminova.prototypemachinery.client.impl.render.BatchedRenderer
import github.kasuminova.prototypemachinery.client.impl.render.gecko.GeckoModelRenderBuildTask
import github.kasuminova.prototypemachinery.client.impl.render.gecko.GeckoRenderSnapshot
import github.kasuminova.prototypemachinery.common.block.MachineBlock
import github.kasuminova.prototypemachinery.common.block.entity.MachineBlockEntity
import net.minecraft.client.Minecraft
import net.minecraft.util.EnumFacing
import net.minecraft.util.ResourceLocation

/**
 * Per-frame submitter that scans nearby MachineBlockEntities and enqueues bound renders.
 *
 * MVP implementation: iterate loaded TEs and filter by distance. This is intentionally simple
 * and will be optimized (chunk tracking, frustum culling, etc.) later.
 */
internal object WorldBoundMachineRenderSubmitter {

    private const val MAX_DISTANCE_BLOCKS = 64.0
    private const val MAX_PER_FRAME = 64

    internal fun submitAll(partialTicks: Float) {
        val mc = Minecraft.getMinecraft()
        val world = mc.world ?: return
        val player = mc.player ?: return

        val resourcesRoot = mc.gameDir.toPath().resolve("resources")

        val maxDistSq = MAX_DISTANCE_BLOCKS * MAX_DISTANCE_BLOCKS
        var submitted = 0

        val componentBindings = ClientRenderBindingRegistryImpl.getComponentBindings()

        for (te in world.loadedTileEntityList) {
            if (submitted >= MAX_PER_FRAME) break
            val machineTe = te as? MachineBlockEntity ?: continue

            val dx = machineTe.pos.x + 0.5 - player.posX
            val dy = machineTe.pos.y + 0.5 - player.posY
            val dz = machineTe.pos.z + 0.5 - player.posZ
            val distSq = dx * dx + dy * dy + dz * dz
            if (distSq > maxDistSq) continue

            // 1) machine-type binding
            val machineBinding = ClientRenderBindingRegistryImpl.getMachineBinding(machineTe.machine.type.id)
            if (machineBinding != null) {
                submitted += submitOne(machineTe, machineBinding, resourcesRoot, bindingKey = machineTe.machine.type.id)
                if (submitted >= MAX_PER_FRAME) break
            }

            // 2) component-type bindings (render if component exists)
            if (componentBindings.isNotEmpty()) {
                for ((componentTypeId, binding) in componentBindings) {
                    if (submitted >= MAX_PER_FRAME) break
                    if (!machineTe.machine.componentMap.containsComponentTypeId(componentTypeId)) continue

                    submitted += submitOne(machineTe, binding, resourcesRoot, bindingKey = componentTypeId)
                }
            }
        }
    }

    private fun submitOne(
        te: MachineBlockEntity,
        binding: github.kasuminova.prototypemachinery.client.api.render.binding.GeckoModelBinding,
        resourcesRoot: java.nio.file.Path,
        bindingKey: ResourceLocation,
    ): Int {
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

        BatchedRenderer.render(renderable) {
            GeckoModelRenderBuildTask(snapshot)
        }

        return 1
    }

    private data class RenderOwnerKey(
        private val te: MachineBlockEntity,
        private val bindingKey: ResourceLocation,
    )

    private fun github.kasuminova.prototypemachinery.api.machine.component.MachineComponentMap.containsComponentTypeId(id: ResourceLocation): Boolean {
        // MVP: linear scan. Can be optimized later by keeping an id->type index.
        return this.components.keys.any { it.id == id }
    }
}
