package github.kasuminova.prototypemachinery.client.registry

import github.kasuminova.prototypemachinery.common.registry.BlockRegisterer
import github.kasuminova.prototypemachinery.common.registry.HatchRegisterer
import github.kasuminova.prototypemachinery.common.registry.PMItems
import net.minecraft.block.state.IBlockState
import net.minecraft.client.renderer.block.model.ModelResourceLocation
import net.minecraft.client.renderer.block.statemap.StateMapperBase
import net.minecraft.item.Item
import net.minecraft.util.ResourceLocation
import net.minecraftforge.client.event.ModelRegistryEvent
import net.minecraftforge.client.model.ModelLoader
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

@SideOnly(Side.CLIENT)
internal object ModelRegisterer {

    @SubscribeEvent
    fun onModelRegistry(event: ModelRegistryEvent) {
        registerBlockModels()
        registerItemModels()
        registerExtraItemModels()
        registerHatchItemModels()
    }

    private fun registerBlockModels() {
        val machineBlocks = BlockRegisterer.getAllMachineBlocks()

        machineBlocks.forEach { block ->
            ModelLoader.setCustomStateMapper(block, object : StateMapperBase() {
                override fun getModelResourceLocation(state: IBlockState): ModelResourceLocation {
                    val machineType = block.machineType
                    val baseLocation = machineType.controllerModelLocation
                        ?: ResourceLocation("prototypemachinery", "default_controller")

                    val propertyString = this.getPropertyString(state.properties)
                    return ModelResourceLocation(baseLocation, propertyString)
                }
            })
        }
    }

    private fun registerItemModels() {
        val machineBlocks = BlockRegisterer.getAllMachineBlocks()

        machineBlocks.forEach { block ->
            val item = Item.getItemFromBlock(block)
            if (item != null) {
                val machineType = block.machineType
                val baseLocation = machineType.controllerModelLocation
                    ?: ResourceLocation("prototypemachinery", "default_controller")

                ModelLoader.setCustomModelResourceLocation(
                    item,
                    0,
                    ModelResourceLocation(baseLocation, "inventory")
                )
            }
        }
    }

    private fun registerExtraItemModels() {
        val items = listOf(
            PMItems.controllerOrientationTool,
            PMItems.scannerInstrument,
            PMItems.buildInstrument,
        )
        items.forEach { item ->
            val name = item.registryName ?: return@forEach
            ModelLoader.setCustomModelResourceLocation(item, 0, ModelResourceLocation(name, "inventory"))
        }
    }

    /**
     * Explicitly register inventory models for hatch ItemBlocks.
     *
     * In some client setups, relying on implicit/default item model resolution can cause
     * the model geometry to appear while textures end up as missing (purple/black), without
     * producing a clear missing-texture log.
     */
    private fun registerHatchItemModels() {
        val hatchBlocks = HatchRegisterer.getAllHatchBlocks()

        hatchBlocks.forEach { block ->
            val item = Item.getItemFromBlock(block) ?: return@forEach
            val registryName = block.registryName ?: return@forEach

            // Standard ItemBlock model location: models/item/<registry_path>.json (variant: inventory)
            ModelLoader.setCustomModelResourceLocation(
                item,
                0,
                ModelResourceLocation(registryName, "inventory")
            )
        }
    }
}
