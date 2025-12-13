package github.kasuminova.prototypemachinery.common.handler

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import youyihj.zenutils.api.reload.ScriptReloadEvent

internal object CraftTweakerReloadHandler {

    @SubscribeEvent
    fun onCraftTweakerReloadPre(event: ScriptReloadEvent.Pre) {
        runCatching {
            PrototypeMachineryAPI.machineUIRegistry.clearAll()
            PrototypeMachineryAPI.uiBindingRegistry.clearAll()
            PrototypeMachineryAPI.uiActionRegistry.clearAll()
        }.onFailure {
            PrototypeMachinery.logger.warn("Failed to clear registries on CraftTweaker reload (Pre).", it)
        }
    }

    @SubscribeEvent
    fun onCraftTweakerReloadPost(event: ScriptReloadEvent.Post) {
    }

}