package github.kasuminova.prototypemachinery.common.registry

import net.minecraft.item.Item
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

internal object ItemRegisterer {

    @SubscribeEvent
    fun onRegisterEvent(event: RegistryEvent.Register<Item>) {
    }

}