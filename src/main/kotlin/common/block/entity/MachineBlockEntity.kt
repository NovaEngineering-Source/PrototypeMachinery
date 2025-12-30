package github.kasuminova.prototypemachinery.common.block.entity

import com.cleanroommc.modularui.api.IGuiHolder
import com.cleanroommc.modularui.factory.PosGuiData
import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.screen.UISettings
import com.cleanroommc.modularui.value.sync.PanelSyncManager
import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI
import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent
import github.kasuminova.prototypemachinery.api.machine.component.getFirstComponentOfType
import github.kasuminova.prototypemachinery.api.machine.component.ui.UIProviderComponent
import github.kasuminova.prototypemachinery.api.machine.structure.StructureOrientation
import github.kasuminova.prototypemachinery.api.ui.definition.PanelDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import github.kasuminova.prototypemachinery.client.gui.DefaultMachineUI
import github.kasuminova.prototypemachinery.client.gui.UIBuilderHelper
import github.kasuminova.prototypemachinery.common.util.TwistMath
import github.kasuminova.prototypemachinery.common.util.warnWithBlockEntity
import github.kasuminova.prototypemachinery.impl.MachineInstanceImpl
import github.kasuminova.prototypemachinery.impl.scheduler.TaskSchedulerImpl
import net.minecraft.block.state.IBlockState
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.network.NetworkManager
import net.minecraft.network.play.server.SPacketUpdateTileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.ITickable
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * Machine controller TileEntity.
 *
 * ## Orientation Model
 * - **facing** (from blockstate): The direction the controller's front face points
 * - **twist** (stored here): Clockwise rotation steps around facing (0-3)
 *
 * Together, (facing, twist) uniquely identifies one of 24 cube orientations.
 */
public class MachineBlockEntity() : BlockEntity(), ITickable, IGuiHolder<PosGuiData> {

    public companion object {
        private const val TESR_RENDER_BOUNDS_GROW_BLOCKS: Double = 4.0
    }

    public lateinit var machine: MachineInstanceImpl
        private set

    public var tickElapsed: Long = 0
        private set

    public var currentTotalTick: Long = 0
        private set

    /**
     * Clockwise rotation steps around the FACING axis (0-3).
     * 0 = 0°, 1 = 90°, 2 = 180°, 3 = 270°
     */
    public var twist: Int = 0
        private set

    @Volatile
    private var pendingSync: Boolean = false

    public constructor(machineType: MachineType) : this() {
        initialize(machineType)
    }

    public fun initialize(machineType: MachineType) {
        machine = MachineInstanceImpl(this, machineType)
    }

    /**
     * Sets the twist value (0-3) and schedules a sync to clients.
     */
    public fun setTwist(newTwist: Int) {
        val clamped = newTwist and 3
        if (this.twist == clamped) return

        this.twist = clamped
        markDirty()
        sync()
    }

    /**
     * Increments twist by 1 (clockwise 90°), wrapping 3→0.
     */
    public fun rotateTwistCW() {
        setTwist(TwistMath.nextTwist(twist))
    }

    /**
     * Decrements twist by 1 (counter-clockwise 90°), wrapping 0→3.
     */
    public fun rotateTwistCCW() {
        setTwist(TwistMath.prevTwist(twist))
    }

    /**
     * Returns the current orientation as a StructureOrientation.
     * Requires the facing from blockstate.
     */
    public fun getOrientation(facing: EnumFacing): StructureOrientation {
        return TwistMath.toStructureOrientation(facing, twist)
    }

    /**
     * Returns the "top" direction for the current (facing, twist).
     */
    public fun getTopFacing(facing: EnumFacing): EnumFacing {
        return TwistMath.getTopFromTwist(facing, twist)
    }

    /**
     * Schedule a full data sync to client.
     */
    public fun sync() {
        pendingSync = true
    }

    override fun getRenderBoundingBox(): AxisAlignedBB {
        return AxisAlignedBB(pos).grow(TESR_RENDER_BOUNDS_GROW_BLOCKS)
    }

    override fun update() {
        if (currentTotalTick == world.totalWorldTime) {
            return
        }
        currentTotalTick = world.totalWorldTime

        if (pendingSync && !world.isRemote) {
            val state = world.getBlockState(pos)
            world.notifyBlockUpdate(pos, state, state, 3)
            pendingSync = false
        }

        if (!world.isRemote && ::machine.isInitialized) {
            machine.onControllerTick()
        }

        tickElapsed++
    }

    override fun readFromNBT(compound: NBTTagCompound) {
        super.readFromNBT(compound)
        val machineId = compound.getString("MachineID")
        val machineType: MachineType? = PrototypeMachineryAPI.machineTypeRegistry[ResourceLocation(machineId)]
        if (machineType == null) {
            PrototypeMachinery.logger.error("Failed to load machine with ID: $machineId (not registered)")
            return
        }
        initialize(machineType)
        val machineNBT = compound.getCompoundTag("MachineData")
        machine.readNBT(machineNBT)

        if (compound.hasKey("Twist")) {
            twist = compound.getInteger("Twist") and 3
        }
    }

    override fun writeToNBT(compound: NBTTagCompound): NBTTagCompound {
        val nbt = super.writeToNBT(compound)
        nbt.setString("MachineID", machine.type.id.toString())
        val machineNBT = NBTTagCompound()
        machine.writeNBT(machineNBT)
        nbt.setTag("MachineData", machineNBT)
        nbt.setInteger("Twist", twist)
        return nbt
    }

    override fun getUpdateTag(): NBTTagCompound {
        val tag = super.getUpdateTag()
        tag.setString("MachineID", machine.type.id.toString())
        tag.setInteger("Twist", twist)
        tag.setBoolean("Formed", machine.isFormed())

        val machineNBT = NBTTagCompound()
        machine.componentMap.components.forEach { (type, component) ->
            if (component is MachineComponent.Synchronizable) {
                val componentData = component.writeClientNBT(MachineComponent.Synchronizable.SyncType.FULL)
                if (componentData != null) {
                    machineNBT.setTag(type.id.toString(), componentData)
                }
            }
        }
        tag.setTag("MachineData", machineNBT)

        return tag
    }

    override fun handleUpdateTag(tag: NBTTagCompound) {
        val oldTwist = this.twist
        val oldFormed = if (::machine.isInitialized) machine.isFormed() else false

        val machineId = tag.getString("MachineID")
        if (!::machine.isInitialized) {
            val machineType: MachineType? = PrototypeMachineryAPI.machineTypeRegistry[ResourceLocation(machineId)]
            if (machineType != null) {
                initialize(machineType)
            }
        }

        if (tag.hasKey("Twist")) {
            twist = tag.getInteger("Twist") and 3
        }

        if (tag.hasKey("MachineData")) {
            val machineNBT = tag.getCompoundTag("MachineData")
            machine.componentMap.components.forEach { (type, component) ->
                if (component is MachineComponent.Synchronizable) {
                    if (machineNBT.hasKey(type.id.toString())) {
                        component.readClientNBT(machineNBT.getCompoundTag(type.id.toString()), MachineComponent.Synchronizable.SyncType.FULL)
                    }
                }
            }
        }

        machine.setFormed(tag.getBoolean("Formed"))

        if (world != null && world.isRemote) {
            val newFormed = machine.isFormed()
            if (oldTwist != this.twist || oldFormed != newFormed) {
                PrototypeMachinery.logger.info(
                    "[OrientationSync] client-update pos={} machineId={} twist {}->{} formed {}->{}",
                    pos, machineId, oldTwist, this.twist, oldFormed, newFormed
                )
            }
        }

        val s = world.getBlockState(pos)
        world.markBlockRangeForRenderUpdate(pos, pos)
        world.notifyBlockUpdate(pos, s, s, 3)
    }

    override fun getUpdatePacket(): SPacketUpdateTileEntity = SPacketUpdateTileEntity(pos, 1, updateTag)

    override fun onDataPacket(net: NetworkManager, pkt: SPacketUpdateTileEntity) {
        handleUpdateTag(pkt.nbtCompound)
    }

    override fun shouldRefresh(world: World, pos: BlockPos, oldState: IBlockState, newSate: IBlockState): Boolean =
        oldState.block != newSate.block

    override fun invalidate() {
        super.invalidate()
        if (!world.isRemote) {
            if (::machine.isInitialized) {
                TaskSchedulerImpl.unregister(machine)
                PrototypeMachinery.logger.debug("Unregistered machine instance from scheduler: {}", machine.type.id)
            } else {
                PrototypeMachinery.logger.warnWithBlockEntity("MachineBlockEntity invalidated but machine instance is not initialized.", this)
            }
        } else {
            // Client side: clear render task caches for this TE to avoid memory leaks.
            github.kasuminova.prototypemachinery.client.impl.render.task.RenderTaskCache.removeByTe(this)
        }
    }

    override fun validate() {
        super.validate()
        if (!world.isRemote) {
            if (::machine.isInitialized) {
                TaskSchedulerImpl.register(machine)
                PrototypeMachinery.logger.debug("Re-registered machine instance to scheduler: {}", machine.type.id)
            } else {
                PrototypeMachinery.logger.warnWithBlockEntity("MachineBlockEntity validated but machine instance is not initialized.", this)
            }
        }
    }

    // ======================== IGuiHolder Implementation ========================

    override fun buildUI(data: PosGuiData, syncManager: PanelSyncManager, settings: UISettings): ModularPanel {
        val uiProvider = machine.componentMap.getFirstComponentOfType<UIProviderComponent>()

        if (uiProvider != null) {
            return uiProvider.buildPanel(syncManager)
        }

        val regDef: WidgetDefinition? = PrototypeMachineryAPI.machineUIRegistry.resolve(machine.type.id)
        if (regDef is PanelDefinition) {
            return UIBuilderHelper.buildPanel(regDef, syncManager, this)
        }

        return DefaultMachineUI.build(this, syncManager)
    }

}
