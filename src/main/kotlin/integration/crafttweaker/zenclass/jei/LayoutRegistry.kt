package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.jei

import crafttweaker.annotations.ZenRegister
import github.kasuminova.prototypemachinery.integration.jei.registry.JeiMachineLayoutRegistry
import net.minecraft.util.ResourceLocation
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

@ZenClass("mods.prototypemachinery.jei.LayoutRegistry")
@ZenRegister
public object LayoutRegistry {

    @ZenMethod
    @JvmStatic
    public fun register(machineId: String, layout: LayoutBuilder) {
        JeiMachineLayoutRegistry.register(ResourceLocation(machineId), layout.build(), replace = true)
    }

    @ZenMethod
    @JvmStatic
    public fun registerReplace(machineId: String, layout: LayoutBuilder, replace: Boolean) {
        JeiMachineLayoutRegistry.register(ResourceLocation(machineId), layout.build(), replace = replace)
    }

    @ZenMethod
    @JvmStatic
    public fun setDefault(layout: LayoutBuilder) {
        JeiMachineLayoutRegistry.setDefault(layout.build())
    }

    @ZenMethod
    @JvmStatic
    public fun clear(machineId: String) {
        // No dedicated clear(machineId) in registry yet; emulate by replacing with default (remove).
        // We keep this as a no-op if not present.
        val id = ResourceLocation(machineId)
        val snapshot = JeiMachineLayoutRegistry.snapshot()
        if (!snapshot.containsKey(id)) return

        // Rebuild map without the entry (registry internal map is concurrent; clear+re-add is OK for script phase).
        JeiMachineLayoutRegistry.clear()
        for ((k, v) in snapshot) {
            if (k != id) {
                JeiMachineLayoutRegistry.register(k, v, replace = true)
            }
        }
    }

    @ZenMethod
    @JvmStatic
    public fun clearAll() {
        JeiMachineLayoutRegistry.clear()
    }
}
