package github.kasuminova.prototypemachinery.common

import github.kasuminova.prototypemachinery.common.registry.BlockRegisterer
import github.kasuminova.prototypemachinery.common.registry.HatchRegisterer
import github.kasuminova.prototypemachinery.common.registry.ItemRegisterer
import net.minecraftforge.common.MinecraftForge

internal open class CommonProxy {

    init {
        MinecraftForge.EVENT_BUS.register(BlockRegisterer)
        MinecraftForge.EVENT_BUS.register(ItemRegisterer)
        MinecraftForge.EVENT_BUS.register(HatchRegisterer)
    }

    open fun preInit() {}

    open fun init() {}

    open fun postInit() {}

}