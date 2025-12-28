package github.kasuminova.prototypemachinery.client.impl.world

import com.cleanroommc.multiblocked.persistence.MultiblockWorldSavedData
import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.client.impl.render.binding.ClientRenderBindingRegistryImpl
import github.kasuminova.prototypemachinery.common.block.MachineBlock
import github.kasuminova.prototypemachinery.common.block.entity.MachineBlockEntity
import github.kasuminova.prototypemachinery.impl.machine.structure.StructureBlockPositions
import github.kasuminova.prototypemachinery.impl.machine.structure.StructureRegistryImpl
import github.kasuminova.prototypemachinery.impl.machine.structure.match.StructureMatchContextImpl
import net.minecraft.client.Minecraft
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent

/**
 * Client-side integration for hiding world block models when a structure is formed.
 *
 * Client-side integration for hiding world block models when a structure is formed.
 *
 * Backed by the `component-model-hider` / `multiblocked` saved-data API.
 *
 * 说明：这里使用“直接引用类”的方式调用 API（无反射）。
 */
internal object BlockModelHideManager {

     private const val MODID_COMPONENT_MODEL_HIDER = "component_model_hider"
     private const val MODID_MULTIBLOCKED = "multiblocked"

    /**
     * How often we scan loaded controllers (ticks).
     * Keep this reasonably low; matching is non-trivial.
     */
    private const val SCAN_INTERVAL_TICKS: Long = 10L

    /**
     * How often we recompute already-hidden controllers to track slice counts/orientation changes.
     */
    private const val REFRESH_INTERVAL_TICKS: Long = 40L

    private data class HiddenEntry(
        val orientationFront: EnumFacing,
        val orientationTop: EnumFacing,
        val lastRefreshAt: Long,
    )

    private val hiddenControllers: MutableMap<BlockPos, HiddenEntry> = HashMap()

    private var lastScanAt: Long = -1L

    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.END) return

        val mc = Minecraft.getMinecraft()
        val world = mc.world ?: return

        if (!isHiderAvailable()) {
            // If the mod disappeared (dev env) or is not installed, ensure we don't keep stale state.
            if (hiddenControllers.isNotEmpty()) {
                clearAllHidden()
            }
            PrototypeMachinery.logger.debug("[BlockModelHideManager] Hider not available")
            return
        }

        val now = world.totalWorldTime
        if (lastScanAt != -1L && now - lastScanAt < SCAN_INTERVAL_TICKS) return
        lastScanAt = now

        // Track which controllers are currently loaded. Any previously-hidden controller that is no longer
        // loaded should be removed to avoid leaving global disabled state behind.
        val seen = HashSet<BlockPos>()

        @Suppress("UNCHECKED_CAST")
        val tes = world.loadedTileEntityList as? List<Any> ?: return
        for (teAny in tes) {
            val te = teAny as? MachineBlockEntity ?: continue
            if (te.isInvalid) continue

            val controllerPos = te.pos
            seen.add(controllerPos)

            val decision = decideShouldHide(te)
            if (!decision.shouldHide) {
                if (hiddenControllers.containsKey(controllerPos)) {
                    removeHidden(controllerPos)
                    PrototypeMachinery.logger.debug("[BlockModelHideManager] Unhiding controller at {} - shouldHide=false", controllerPos)
                }
                continue
            }

            val existing = hiddenControllers[controllerPos]
            val needsRefresh = existing == null ||
                existing.orientationFront != decision.front ||
                existing.orientationTop != decision.top ||
                now - existing.lastRefreshAt >= REFRESH_INTERVAL_TICKS

            if (!needsRefresh) continue

            // Recompute actual matched positions on demand.
            val rootStructure = decision.rootStructure
            val ctx = StructureMatchContextImpl(te.machine)
            val matched = runCatching { rootStructure.matches(ctx, controllerPos) }.getOrDefault(false)
            val rootInstance = if (matched) ctx.getRootInstance() else null

            if (rootInstance == null) {
                // If we can't match even though the block claims formed, be conservative and unhide.
                PrototypeMachinery.logger.debug("[BlockModelHideManager] Controller at {} - structure match failed (matched={}, rootInstance=null)", controllerPos, matched)
                if (hiddenControllers.containsKey(controllerPos)) {
                    removeHidden(controllerPos)
                }
                continue
            }

            val positions = StructureBlockPositions.collect(rootStructure, rootInstance, controllerPos).toMutableList()
            // Add controller position to the hide list - this is required for the hider to work properly.
            if (!positions.contains(controllerPos)) {
                positions.add(0, controllerPos)
            }

            PrototypeMachinery.logger.debug("[BlockModelHideManager] Hiding {} positions for controller at {}", positions.size, controllerPos)

            // Replace existing entry by removing first (ensures updated list takes effect).
            if (hiddenControllers.containsKey(controllerPos)) {
                removeHidden(controllerPos)
            }

            if (ComponentModelHiderBridge.addDisableModel(controllerPos, positions)) {
                hiddenControllers[controllerPos] = HiddenEntry(decision.front, decision.top, now)
            }
        }

        // Cleanup: controllers that were hidden but are no longer loaded.
        if (hiddenControllers.isNotEmpty()) {
            val it = hiddenControllers.keys.iterator()
            val toRemove = ArrayList<BlockPos>()
            while (it.hasNext()) {
                val pos = it.next()
                if (!seen.contains(pos)) {
                    toRemove.add(pos)
                }
            }
            for (pos in toRemove) {
                removeHidden(pos)
            }
        }
    }

    @SubscribeEvent
    fun onWorldUnload(event: WorldEvent.Unload) {
        val mcWorld = Minecraft.getMinecraft().world
        if (mcWorld != null && event.world === mcWorld) {
            clearAllHidden()
        }
    }

    private data class HideDecision(
        val shouldHide: Boolean,
        val rootStructure: github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure,
        val front: EnumFacing,
        val top: EnumFacing,
    )

    private fun decideShouldHide(te: MachineBlockEntity): HideDecision {
        val world = te.world

        // Read formed state directly from TileEntity, not from block state.
        // Block state's FORMED property is set in getActualState(), which is not
        // returned by world.getBlockState() - it only returns the base state.
        val formed = te.machine.isFormed()
        if (!formed) {
            PrototypeMachinery.logger.debug("[BlockModelHideManager] Controller at {} - not formed", te.pos)
            return HideDecision(false, te.machine.type.structure, EnumFacing.NORTH, EnumFacing.UP)
        }

        // Only hide if we have at least one custom render binding; otherwise we could make the machine invisible.
        if (!hasAnyCustomModelBinding(te)) {
            PrototypeMachinery.logger.debug("[BlockModelHideManager] Controller at {} - no custom model binding", te.pos)
            return HideDecision(false, te.machine.type.structure, EnumFacing.NORTH, EnumFacing.UP)
        }

        // For FACING, we still need to read from block state as it's stored there.
        val state = world.getBlockState(te.pos)
        val front = runCatching { state.getValue(MachineBlock.FACING) }.getOrDefault(EnumFacing.NORTH)
        val top = te.getTopFacing(front)
        val orientation = github.kasuminova.prototypemachinery.api.machine.structure.StructureOrientation(front = front, top = top)

        val rootStructure = StructureRegistryImpl.get(te.machine.type.structure.id, orientation, front)
            ?: te.machine.type.structure.transform { it }

        if (!rootStructure.hideWorldBlocks) {
            PrototypeMachinery.logger.debug("[BlockModelHideManager] Controller at {} - hideWorldBlocks=false for structure {}", te.pos, rootStructure.id)
            return HideDecision(false, rootStructure, front, top)
        }

        PrototypeMachinery.logger.debug("[BlockModelHideManager] Controller at {} - should hide (structure={})", te.pos, rootStructure.id)
        return HideDecision(true, rootStructure, front, top)
    }

    private fun hasAnyCustomModelBinding(te: MachineBlockEntity): Boolean {
        val machineTypeId = te.machine.type.id

        val structureBindings = ClientRenderBindingRegistryImpl.getStructureBindings(machineTypeId)
        if (structureBindings.isNotEmpty()) return true

        val machineBinding = ClientRenderBindingRegistryImpl.getMachineBinding(machineTypeId)
        if (machineBinding != null) return true

        val componentBindings = ClientRenderBindingRegistryImpl.getComponentBindings()
        if (componentBindings.isEmpty()) return false

        // MVP: linear scan; consistent with TESR fallback path.
        val machineComponentTypeIds = te.machine.componentMap.components.keys.map { it.id }
        for (componentTypeId in componentBindings.keys) {
            if (machineComponentTypeIds.any { it == componentTypeId }) {
                return true
            }
        }

        return false
    }

    private fun removeHidden(controllerPos: BlockPos) {
        ComponentModelHiderBridge.removeDisableModel(controllerPos)
        hiddenControllers.remove(controllerPos)
    }

    private fun clearAllHidden() {
        ComponentModelHiderBridge.clearDisabled()
        hiddenControllers.clear()
        lastScanAt = -1L
    }

    private fun isHiderAvailable(): Boolean {
        // Only attempt to call into the API when the providing mod is loaded.
        return Loader.isModLoaded(MODID_COMPONENT_MODEL_HIDER) || Loader.isModLoaded(MODID_MULTIBLOCKED)
    }

    private object ComponentModelHiderBridge {
        fun addDisableModel(controllerPos: BlockPos, positions: List<BlockPos>): Boolean {
            return runCatching {
                MultiblockWorldSavedData.addDisableModel(controllerPos, positions)
                true
            }.onFailure {
                PrototypeMachinery.logger.warn("Failed to call component-model-hider addDisableModel.", it)
            }.getOrDefault(false)
        }

        fun removeDisableModel(controllerPos: BlockPos) {
            runCatching {
                MultiblockWorldSavedData.removeDisableModel(controllerPos)
            }.onFailure {
                PrototypeMachinery.logger.warn("Failed to call component-model-hider removeDisableModel.", it)
            }
        }

        fun clearDisabled() {
            runCatching {
                // Note: global state. This may affect other mods using the same API.
                MultiblockWorldSavedData.clearDisabled()
            }.onFailure {
                PrototypeMachinery.logger.warn("Failed to call component-model-hider clearDisabled.", it)
            }
        }
    }
}
