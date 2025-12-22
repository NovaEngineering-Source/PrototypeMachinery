package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.ui

import crafttweaker.annotations.ZenRegister
import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI
import github.kasuminova.prototypemachinery.impl.ui.runtime.MachineUiRuntimeJson
import net.minecraft.util.ResourceLocation
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

/**
 * ZenScript entry for registering machine UIs independent from MachineTypeBuilder.
 */
@ZenClass("mods.prototypemachinery.ui.UIRegistry")
@ZenRegister
public object UIRegistry {

    @ZenMethod
    @JvmStatic
    public fun register(machineId: String, panel: PanelBuilder) {
        PrototypeMachineryAPI.machineUIRegistry.register(
            ResourceLocation(machineId),
            panel.build(),
            0,
            "crafttweaker"
        )
    }

    @ZenMethod
    @JvmStatic
    public fun registerWithPriority(machineId: String, panel: PanelBuilder, priority: Int) {
        PrototypeMachineryAPI.machineUIRegistry.register(
            ResourceLocation(machineId),
            panel.build(),
            priority,
            "crafttweaker"
        )
    }

    /**
     * Register a Machine UI exported from the Web Editor (runtime JSON).
     *
     * Notes:
     * - This is intentionally tolerant: unknown widgets are ignored.
     * - Conditions (visibleIf/enabledIf) are supported via the conditional wrapper.
     * - Tabs are supported via TabContainerDefinition (minimal runtime implementation).
     */
    @ZenMethod
    @JvmStatic
    public fun registerRuntimeJson(machineId: String, runtimeJson: String) {
        registerRuntimeJsonWithPriority(machineId, runtimeJson, 0)
    }

    @ZenMethod
    @JvmStatic
    public fun registerRuntimeJsonWithPriority(machineId: String, runtimeJson: String, priority: Int) {
        val panel = runCatching { MachineUiRuntimeJson.parsePanelDefinition(runtimeJson) }.getOrElse {
            PrototypeMachinery.logger.warn("Failed to parse runtime JSON for machineId='{}'", machineId, it)
            null
        }
        if (panel == null) {
            PrototypeMachinery.logger.warn("Runtime JSON produced no panel definition for machineId='{}'", machineId)
            return
        }

        PrototypeMachineryAPI.machineUIRegistry.register(
            ResourceLocation(machineId),
            panel,
            priority,
            "web-editor-runtime-json"
        )
    }

    @ZenMethod
    @JvmStatic
    public fun clear(machineId: String) {
        PrototypeMachineryAPI.machineUIRegistry.clear(ResourceLocation(machineId))
    }

    @ZenMethod
    @JvmStatic
    public fun clearAll() {
        PrototypeMachineryAPI.machineUIRegistry.clearAll()
    }
}
