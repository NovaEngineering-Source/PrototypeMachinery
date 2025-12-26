package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.render

import crafttweaker.annotations.ZenRegister
import github.kasuminova.prototypemachinery.client.api.render.binding.ClientRenderBindingApi
import net.minecraft.util.ResourceLocation
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

/**
 * ZenScript entry point for client-side render bindings.
 *
 * This is safe to call on a dedicated server: it only registers declarative bindings
 * (resource locations and simple flags). Actual rendering happens client-side.
 */
@ZenClass("mods.prototypemachinery.render.RenderBindings")
@ZenRegister
public object ZenRenderBindings {

    /** Create a Gecko binding builder. */
    @ZenMethod
    @JvmStatic
    public fun gecko(): ZenGeckoBindingBuilder = ZenGeckoBindingBuilder()

    /**
     * Bind a Gecko model to a specific structure id inside a machine type.
     *
     * @param machineTypeId e.g. "prototypemachinery:my_machine"
     * @param structureId the MachineStructure id (string), e.g. "example_structure_render_top_mid_tail_top"
     */
    @ZenMethod
    @JvmStatic
    public fun bindGeckoToStructure(machineTypeId: String, structureId: String, binding: ZenGeckoBindingBuilder) {
        ClientRenderBindingApi.bindGeckoToStructure(
            ResourceLocation(machineTypeId),
            structureId,
            binding.buildStructureBinding(),
        )
    }

    /** Bind a Gecko model to the whole machine type (legacy / fallback). */
    @ZenMethod
    @JvmStatic
    public fun bindGeckoToMachineType(machineTypeId: String, binding: ZenGeckoBindingBuilder) {
        ClientRenderBindingApi.bindGeckoToMachineType(
            ResourceLocation(machineTypeId),
            binding.buildModelBinding(),
        )
    }
}
