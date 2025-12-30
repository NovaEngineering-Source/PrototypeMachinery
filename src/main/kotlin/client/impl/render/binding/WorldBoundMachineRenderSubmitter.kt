package github.kasuminova.prototypemachinery.client.impl.render.binding

import github.kasuminova.prototypemachinery.api.machine.component.type.GeckoModelStateComponent
import github.kasuminova.prototypemachinery.api.machine.component.type.GeckoModelStateComponentType
import github.kasuminova.prototypemachinery.api.machine.component.type.ZSDataComponent
import github.kasuminova.prototypemachinery.api.machine.component.type.ZSDataComponentType
import github.kasuminova.prototypemachinery.client.api.render.RenderKey
import github.kasuminova.prototypemachinery.client.api.render.Renderable
import github.kasuminova.prototypemachinery.client.impl.render.BatchedRenderer
import github.kasuminova.prototypemachinery.client.impl.render.gecko.GeckoModelBaker
import github.kasuminova.prototypemachinery.client.impl.render.gecko.GeckoModelRenderBuildTask
import github.kasuminova.prototypemachinery.client.impl.render.gecko.GeckoRenderSnapshot
import github.kasuminova.prototypemachinery.client.impl.render.task.RenderTaskOwnerKeys
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

        val geckoState = te.machine.componentMap[GeckoModelStateComponentType] as? GeckoModelStateComponent
        val animationNames = resolveAnimationNames(te, binding, geckoState)

        val animTick = binding.animation?.let {
            (te.world.totalWorldTime % Int.MAX_VALUE).toInt()
        } ?: 0

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

        fun baseKey(animationStateHash: Int, variant: Int): RenderKey = RenderKey(
            modelId = binding.geo,
            textureId = binding.texture,
            variant = variant,
            animationStateHash = animationStateHash,
            secureVersion = 0,
            flags = 0,
            orientationHash = front.ordinal * 24 + top.ordinal * 4 + te.twist,
        )

        fun baseRenderable(ownerKey: Any, rk: RenderKey): Renderable = object : Renderable {
            override val ownerKey: Any = ownerKey
            override val renderKey: RenderKey = rk
            override val x: Double = te.pos.x.toDouble()
            override val y: Double = te.pos.y.toDouble()
            override val z: Double = te.pos.z.toDouble()
            override val facing = front
            override val combinedLight: Int = te.world.getCombinedLight(te.pos, 0)
        }

        fun baseSnapshot(ownerKey: Any, rk: RenderKey, bakeMode: GeckoModelBaker.BakeMode): GeckoRenderSnapshot = GeckoRenderSnapshot(
            ownerKey = ownerKey,
            renderKey = rk,
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

        if (binding.animation == null) {
            val ownerKey = RenderTaskOwnerKeys.legacyOwnerKey(te, bindingKey, RenderPart.ALL.ordinal, RenderPart.values().size)
            val rk = baseKey(animationStateHash = animTick, variant = variantBase)
            val renderable = baseRenderable(ownerKey, rk)
            val snapshot = baseSnapshot(ownerKey, rk, GeckoModelBaker.BakeMode.ALL)
            BatchedRenderer.render(renderable) { GeckoModelRenderBuildTask(snapshot) }
            return 1
        }

        run {
            val ownerKey = RenderTaskOwnerKeys.legacyOwnerKey(te, bindingKey, RenderPart.PERMANENT_STATIC.ordinal, RenderPart.values().size)
            val rk = baseKey(animationStateHash = 0, variant = variantBase)
            val renderable = baseRenderable(ownerKey, rk)
            val snapshot = baseSnapshot(ownerKey, rk, GeckoModelBaker.BakeMode.PERMANENT_STATIC_ONLY)
            BatchedRenderer.render(renderable) { GeckoModelRenderBuildTask(snapshot) }
        }

        run {
            val ownerKey = RenderTaskOwnerKeys.legacyOwnerKey(te, bindingKey, RenderPart.TEMP_STATIC.ordinal, RenderPart.values().size)
            val rk = baseKey(animationStateHash = 0, variant = variantForAnim)
            val renderable = baseRenderable(ownerKey, rk)
            val snapshot = baseSnapshot(ownerKey, rk, GeckoModelBaker.BakeMode.TEMP_STATIC_ONLY)
            BatchedRenderer.render(renderable) { GeckoModelRenderBuildTask(snapshot) }
        }

        run {
            val ownerKey = RenderTaskOwnerKeys.legacyOwnerKey(te, bindingKey, RenderPart.DYNAMIC.ordinal, RenderPart.values().size)
            val rk = baseKey(animationStateHash = animTick, variant = variantForAnim)
            val renderable = baseRenderable(ownerKey, rk)
            val snapshot = baseSnapshot(ownerKey, rk, GeckoModelBaker.BakeMode.ANIMATED_ONLY)
            BatchedRenderer.render(renderable) { GeckoModelRenderBuildTask(snapshot) }
        }

        return 1
    }

    private enum class RenderPart {
        ALL,
        PERMANENT_STATIC,
        TEMP_STATIC,
        DYNAMIC,
    }

    private fun hashStringList(values: List<String>): Int {
        var h = values.size
        for (s in values) {
            h = 31 * h + s.hashCode()
            h = 31 * h + 0x9E3779B9.toInt()
        }
        return h
    }

    private fun resolveAnimationNames(
        te: MachineBlockEntity,
        binding: github.kasuminova.prototypemachinery.client.api.render.binding.GeckoModelBinding,
        geckoState: GeckoModelStateComponent?,
    ): List<String> {
        if (binding.animation == null) return emptyList()

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

    private fun github.kasuminova.prototypemachinery.api.machine.component.MachineComponentMap.containsComponentTypeId(id: ResourceLocation): Boolean {
        // MVP: linear scan. Can be optimized later by keeping an id->type index.
        return this.components.keys.any { it.id == id }
    }
}
