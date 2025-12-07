package github.kasuminova.prototypemachinery.common

import github.kasuminova.prototypemachinery.common.registry.BlockRegisterer
import github.kasuminova.prototypemachinery.common.registry.ItemRegisterer
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.world.World
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.network.IGuiHandler

internal open class CommonProxy : IGuiHandler {

    init {
        MinecraftForge.EVENT_BUS.register(BlockRegisterer)
        MinecraftForge.EVENT_BUS.register(ItemRegisterer)
    }

    open fun preInit() {}

    open fun init() {}

    open fun postInit() {}

    override fun getServerGuiElement(id: Int, player: EntityPlayer?, world: World?, x: Int, y: Int, z: Int): Any? {
        TODO("Not yet implemented")
    }

    override fun getClientGuiElement(id: Int, player: EntityPlayer?, world: World?, x: Int, y: Int, z: Int): Any? = null

}