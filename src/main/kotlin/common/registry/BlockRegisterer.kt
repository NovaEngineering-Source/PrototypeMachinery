package github.kasuminova.prototypemachinery.common.registry

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.common.block.MachineBlock
import github.kasuminova.prototypemachinery.impl.machine.MachineTypeRegistryImpl
import net.minecraft.block.Block
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

/**
 * Block registerer for machine types.
 * 机械类型的方块注册器。
 * 
 * This registerer creates and registers MachineBlock instances for all registered machine types.
 * It runs during the Block registry event, which occurs after PreInit, ensuring all machine types
 * have been registered via MachineTypeRegisterer.
 * 
 * 此注册器为所有已注册的机械类型创建并注册 MachineBlock 实例。
 * 它在方块注册事件期间运行，该事件在 PreInit 之后发生，确保所有机械类型
 * 已通过 MachineTypeRegisterer 注册。
 */
internal object BlockRegisterer {

    /**
     * Map to store created machine blocks for later item registration.
     * 存储已创建的机械方块，用于后续的物品注册。
     */
    private val machineBlocks = mutableMapOf<String, MachineBlock>()

    @SubscribeEvent
    fun onRegisterEvent(event: RegistryEvent.Register<Block>) {
        val allMachineTypes = MachineTypeRegistryImpl.all()

        if (allMachineTypes.isEmpty()) {
            PrototypeMachinery.logger.info("No machine types registered, skipping block registration")
            return
        }

        PrototypeMachinery.logger.info("Registering ${allMachineTypes.size} machine block(s)...")

        for (machineType in allMachineTypes) {
            try {
                val machineBlock = MachineBlock(machineType)
                event.registry.register(machineBlock)
                
                // Store for item registration
                machineBlocks[machineType.id.toString()] = machineBlock
                
                PrototypeMachinery.logger.info("Registered machine block: ${machineBlock.registryName}")
            } catch (e: Exception) {
                PrototypeMachinery.logger.error("Failed to register machine block for type: ${machineType.id}", e)
            }
        }

        PrototypeMachinery.logger.info("Machine block registration completed")
    }

    /**
     * Get a registered machine block by machine type ID.
     * 通过机械类型 ID 获取已注册的机械方块。
     * 
     * @param machineTypeId The machine type ID as string (e.g., "modid:machine_name")
     * @return The MachineBlock instance, or null if not found
     */
    fun getMachineBlock(machineTypeId: String): MachineBlock? = machineBlocks[machineTypeId]

    /**
     * Get all registered machine blocks.
     * 获取所有已注册的机械方块。
     */
    fun getAllMachineBlocks(): Collection<MachineBlock> = machineBlocks.values

}