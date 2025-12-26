package github.kasuminova.prototypemachinery.common.item

import github.kasuminova.prototypemachinery.PrototypeMachinery
import net.minecraft.item.Item
import net.minecraft.util.ResourceLocation

/**
 * Range structure selector (placeholder).
 *
 * 范围结构选择器（占位实现）：目前仅提供物品注册与模型/贴图。
 */
internal class ScannerInstrumentItem : Item() {

    init {
        registryName = ResourceLocation(PrototypeMachinery.MOD_ID, "scanner_instrument")
        translationKey = "${PrototypeMachinery.MOD_ID}.scanner_instrument"
        maxStackSize = 1

        setCreativeTab(github.kasuminova.prototypemachinery.common.registry.PMCreativeTabs.MAIN)
    }
}
