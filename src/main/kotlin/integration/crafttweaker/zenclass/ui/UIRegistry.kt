package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.ui

import crafttweaker.annotations.ZenRegister
import github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI
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
