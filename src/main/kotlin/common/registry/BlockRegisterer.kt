package github.kasuminova.prototypemachinery.common.registry

import net.minecraft.block.Block
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

internal object BlockRegisterer {

    @SubscribeEvent
    fun onRegisterEvent(event: RegistryEvent.Register<Block>) {
    }

}