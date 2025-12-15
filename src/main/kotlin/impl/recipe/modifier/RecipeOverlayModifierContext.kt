package github.kasuminova.prototypemachinery.impl.recipe.modifier

import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.impl.recipe.process.component.RecipeOverlayProcessComponent
import github.kasuminova.prototypemachinery.impl.recipe.requirement.overlay.RecipeRequirementOverlay
import net.minecraft.item.ItemStack
import net.minecraftforge.fluids.FluidStack

/**
 * Context passed to [RecipeOverlayModifier].
 */
public class RecipeOverlayModifierContext(
    public val machine: MachineInstance,
    public val process: RecipeProcess,
) {

    public val overlay: RecipeOverlayProcessComponent = RecipeRequirementOverlay.getOrCreate(process)

    public fun setIgnoreOutputFull(type: RecipeRequirementType<*>, componentId: String, value: Boolean) {
        overlay.setIgnoreOutputFull(type, componentId, value)
    }

    public fun setEnergyPerTick(componentId: String, inputPerTick: Long? = null, outputPerTick: Long? = null) {
        overlay.setEnergyPerTick(componentId, inputPerTick, outputPerTick)
    }

    public fun setItemIO(componentId: String, inputs: List<PMKey<ItemStack>>? = null, outputs: List<PMKey<ItemStack>>? = null) {
        overlay.setItemIO(componentId, inputs, outputs)
    }

    public fun setFluidIO(
        componentId: String,
        inputs: List<PMKey<FluidStack>>? = null,
        outputs: List<PMKey<FluidStack>>? = null,
        inputsPerTick: List<PMKey<FluidStack>>? = null,
        outputsPerTick: List<PMKey<FluidStack>>? = null,
    ) {
        overlay.setFluidIO(componentId, inputs, outputs, inputsPerTick, outputsPerTick)
    }
}
