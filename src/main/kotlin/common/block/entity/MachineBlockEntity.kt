package github.kasuminova.prototypemachinery.common.block.entity

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI
import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.common.util.warnWithBlockEntity
import github.kasuminova.prototypemachinery.impl.MachineInstanceImpl
import github.kasuminova.prototypemachinery.impl.scheduler.TaskSchedulerImpl
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.ITickable
import net.minecraft.util.ResourceLocation

public class MachineBlockEntity() : BlockEntity(), ITickable {

    public lateinit var machine: MachineInstanceImpl
        private set

    public var tickElapsed: Long = 0
        private set

    public var currentTotalTick: Long = 0
        private set

    public constructor(machineType: MachineType) : this() {
        initialize(machineType)
    }

    public fun initialize(machineType: MachineType) {
        machine = MachineInstanceImpl(this, machineType)
    }

    override fun update() {
        if (currentTotalTick == world.totalWorldTime) {
            return
        }
        currentTotalTick = world.totalWorldTime

        runCatching {
            // TODO Ticking Machine Instance
        }.onFailure {
            PrototypeMachinery.logger.warnWithBlockEntity("Error occurred when ticking machine instance.", this, it)
        }

        tickElapsed++
    }

    override fun readFromNBT(compound: NBTTagCompound) {
        super.readFromNBT(compound)
        val machineId = compound.getString("MachineID")
        val machineType: MachineType? = PrototypeMachineryAPI.getMachineType(ResourceLocation(machineId))
        if (machineType == null) {
            PrototypeMachinery.logger.error("Failed to load machine with ID: $machineId (not registered)")
            return
        }
        initialize(machineType)
        val machineNBT = compound.getCompoundTag("MachineData")
        machine.readNBT(machineNBT)
    }

    override fun writeToNBT(compound: NBTTagCompound): NBTTagCompound {
        val nbt = super.writeToNBT(compound)
        nbt.setString("MachineID", machine.type.id.toString())
        val machineNBT = NBTTagCompound()
        machine.writeNBT(machineNBT)
        nbt.setTag("MachineData", machineNBT)
        return nbt
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

}