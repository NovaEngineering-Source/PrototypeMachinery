package github.kasuminova.prototypemachinery.common.registry

import github.kasuminova.prototypemachinery.PrototypeMachinery
import net.minecraft.item.Item
import net.minecraft.item.ItemBlock
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

/**
 * Item registerer for machine blocks.
 * 机械方块的物品注册器。
 * 
 * This registerer creates and registers ItemBlock instances for all registered machine blocks.
 * It runs during the Item registry event, which occurs after Block registration, ensuring all
 * machine blocks are available.
 * 
 * 此注册器为所有已注册的机械方块创建并注册 ItemBlock 实例。
 * 它在物品注册事件期间运行，该事件在方块注册之后发生，确保所有机械方块都可用。
 */
internal object ItemRegisterer {

    @SubscribeEvent
    fun onRegisterEvent(event: RegistryEvent.Register<Item>) {
        val allMachineBlocks = BlockRegisterer.getAllMachineBlocks()

        if (allMachineBlocks.isEmpty()) {
            PrototypeMachinery.logger.info("No machine blocks registered, skipping machine ItemBlock registration")
        } else {
            PrototypeMachinery.logger.info("Registering ${allMachineBlocks.size} machine item(s)...")

            for (machineBlock in allMachineBlocks) {
                try {
                    val itemBlock = ItemBlock(machineBlock).apply {
                        // Use the same registry name as the block
                        // 使用与方块相同的注册名称
                        registryName = machineBlock.registryName

                        // Set unlocalized name for i18n
                        // 设置本地化名称用于国际化
                        translationKey = machineBlock.translationKey

                        // Put all mod blocks into the mod creative tab.
                        setCreativeTab(PMCreativeTabs.MAIN)
                    }

                    event.registry.register(itemBlock)
                    PrototypeMachinery.logger.info("Registered machine item: ${itemBlock.registryName}")
                } catch (e: Throwable) {
                    PrototypeMachinery.logger.error(
                        "Failed to register machine item for block: ${machineBlock.registryName}",
                        e
                    )
                }
            }

            PrototypeMachinery.logger.info("Machine ItemBlock registration completed")
        }

        // Extra non-block items
        PMItems.controllerOrientationTool.setCreativeTab(PMCreativeTabs.MAIN)
        event.registry.register(PMItems.controllerOrientationTool)
        PrototypeMachinery.logger.info("Registered item: ${PMItems.controllerOrientationTool.registryName}")

        PMItems.scannerInstrument.setCreativeTab(PMCreativeTabs.MAIN)
        event.registry.register(PMItems.scannerInstrument)
        PrototypeMachinery.logger.info("Registered item: ${PMItems.scannerInstrument.registryName}")

        PMItems.buildInstrument.setCreativeTab(PMCreativeTabs.MAIN)
        event.registry.register(PMItems.buildInstrument)
        PrototypeMachinery.logger.info("Registered item: ${PMItems.buildInstrument.registryName}")
    }

}