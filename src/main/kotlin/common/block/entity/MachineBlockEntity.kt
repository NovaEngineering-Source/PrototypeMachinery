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
import github.kasuminova.prototypemachinery.api.ui.definition.PanelDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import github.kasuminova.prototypemachinery.client.gui.DefaultMachineUI
import github.kasuminova.prototypemachinery.client.gui.UIBuilderHelper
import github.kasuminova.prototypemachinery.common.util.warnWithBlockEntity
import github.kasuminova.prototypemachinery.impl.MachineInstanceImpl
import github.kasuminova.prototypemachinery.impl.scheduler.TaskSchedulerImpl
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.network.NetworkManager
import net.minecraft.network.play.server.SPacketUpdateTileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.ITickable
import net.minecraft.util.ResourceLocation

public class MachineBlockEntity() : BlockEntity(), ITickable, IGuiHolder<PosGuiData> {

    public lateinit var machine: MachineInstanceImpl
        private set

    public var tickElapsed: Long = 0
        private set

    public var currentTotalTick: Long = 0
        private set

    public var twist: EnumFacing = EnumFacing.NORTH
        private set

    @Volatile
    private var pendingSync: Boolean = false

    public constructor(machineType: MachineType) : this() {
        initialize(machineType)
    }

    public fun initialize(machineType: MachineType) {
        machine = MachineInstanceImpl(this, machineType)
    }

    public fun setTwist(newTwist: EnumFacing) {
        if (this.twist != newTwist) {
            this.twist = newTwist
            markDirty()
            sync()
        }
    }

    /**
     * Schedule a full data sync to client.
     * 计划向客户端进行全量数据同步。
     *
     * This method is debounced to ensure at most one sync packet is sent per tick.
     * 此方法经过防抖处理，确保每个 tick 最多发送一个同步包。
     */
    public fun sync() {
        pendingSync = true
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

        // Structure refresh / formation should run on the main thread.
        // 结构刷新/形成应当在主线程执行（避免并发 tick 线程访问 world）。
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
            twist = EnumFacing.byIndex(compound.getInteger("Twist"))
        }
    }

    override fun writeToNBT(compound: NBTTagCompound): NBTTagCompound {
        val nbt = super.writeToNBT(compound)
        nbt.setString("MachineID", machine.type.id.toString())
        val machineNBT = NBTTagCompound()
        machine.writeNBT(machineNBT)
        nbt.setTag("MachineData", machineNBT)
        nbt.setInteger("Twist", twist.index)
        return nbt
    }

    override fun getUpdateTag(): NBTTagCompound {
        val tag = super.getUpdateTag()
        tag.setString("MachineID", machine.type.id.toString())
        tag.setInteger("Twist", twist.index)

        // Write initial sync data for components
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
        // Read basic data
        val machineId = tag.getString("MachineID")
        if (!::machine.isInitialized) {
            val machineType: MachineType? = PrototypeMachineryAPI.machineTypeRegistry[ResourceLocation(machineId)]
            if (machineType != null) {
                initialize(machineType)
            }
        }

        if (tag.hasKey("Twist")) {
            twist = EnumFacing.byIndex(tag.getInteger("Twist"))
        }

        // Read component sync data
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
    }

    override fun getUpdatePacket(): SPacketUpdateTileEntity = SPacketUpdateTileEntity(pos, 1, updateTag)

    override fun onDataPacket(net: NetworkManager, pkt: SPacketUpdateTileEntity) {
        handleUpdateTag(pkt.nbtCompound)
    }

    override fun invalidate() {
        super.invalidate()

        // Unregister from scheduler
        // 从调度器取消注册
        if (!world.isRemote) {
            if (::machine.isInitialized) {
                TaskSchedulerImpl.unregister(machine)
                PrototypeMachinery.logger.debug("Unregistered machine instance from scheduler: {}", machine.type.id)
            } else {
                PrototypeMachinery.logger.warnWithBlockEntity("MachineBlockEntity invalidated but machine instance is not initialized.", this)
            }
        }
    }

    override fun validate() {
        super.validate()

        // Re-register to scheduler if needed
        // 如果需要，重新注册到调度器
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
        // First, try to get UIProviderComponent from the machine's components
        // 首先，尝试从机器的组件中获取 UIProviderComponent
        val uiProvider = machine.componentMap.getFirstComponentOfType<UIProviderComponent>()

        if (uiProvider != null) {
            return uiProvider.buildPanel(syncManager)
        }

        // Then: script/mod overrides (UIRegistry)
        val regDef: WidgetDefinition? = PrototypeMachineryAPI.machineUIRegistry.resolve(machine.type.id)
        if (regDef is PanelDefinition) {
            return UIBuilderHelper.buildPanel(regDef, syncManager, this)
        }

        // Default fallback UI if no definition is provided
        // 如果没有定义 UI，则显示默认备用界面
        return DefaultMachineUI.build(this, syncManager)
    }

}