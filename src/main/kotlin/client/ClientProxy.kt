package github.kasuminova.prototypemachinery.client

import github.kasuminova.prototypemachinery.client.registry.ModelRegisterer
import github.kasuminova.prototypemachinery.common.CommonProxy
import net.minecraftforge.common.MinecraftForge

internal class ClientProxy : CommonProxy() {

    init {
        MinecraftForge.EVENT_BUS.register(ModelRegisterer)
    }

    override fun preInit() {
        super.preInit()
    }

    override fun init() {
        super.init()
    }

    override fun postInit() {
        super.postInit()
    }

}