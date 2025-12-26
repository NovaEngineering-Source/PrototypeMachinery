package github.kasuminova.prototypemachinery.common.handler

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI
import github.kasuminova.prototypemachinery.client.api.render.binding.ClientRenderBindingApi
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import youyihj.zenutils.api.reload.ScriptReloadEvent

internal object CraftTweakerReloadHandler {

    @SubscribeEvent
    fun onCraftTweakerReloadPre(event: ScriptReloadEvent.Pre) {
        runCatching {
            PrototypeMachineryAPI.machineUIRegistry.clearAll()
            PrototypeMachineryAPI.uiBindingRegistry.clearAll()
            PrototypeMachineryAPI.uiActionRegistry.clearAll()

            // Client-side render bindings are declarative and safe to clear here.
            // This ensures reloadable scripts do not leave stale bindings behind.
            ClientRenderBindingApi.clearAll()
        }.onFailure {
            PrototypeMachinery.logger.warn("Failed to clear registries on CraftTweaker reload (Pre).", it)
        }
    }

    @SubscribeEvent
    fun onCraftTweakerReloadPost(event: ScriptReloadEvent.Post) {
    }

}