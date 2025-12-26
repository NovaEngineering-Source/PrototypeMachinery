package github.kasuminova.prototypemachinery.common.registry

import github.kasuminova.prototypemachinery.PrototypeMachinery
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.init.Items
import net.minecraft.item.Item
import net.minecraft.item.ItemStack

internal object PMCreativeTabs {

    /**
     * Main creative tab for PrototypeMachinery.
     *
     * Note: icon item is resolved lazily via registry to avoid init-order cycles.
     */
    val MAIN: CreativeTabs = object : CreativeTabs(PrototypeMachinery.MOD_ID) {
        override fun createIcon(): ItemStack {
            val iconItem = Item.getByNameOrId("${PrototypeMachinery.MOD_ID}:orientation_tool")
                ?: Items.IRON_PICKAXE
            return ItemStack(iconItem)
        }
    }
}
