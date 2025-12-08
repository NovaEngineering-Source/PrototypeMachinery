package github.kasuminova.prototypemachinery.impl.machine.recipe.requirement

import github.kasuminova.prototypemachinery.api.machine.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.machine.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.common.registry.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.impl.machine.component.container.FluidContainerComponent

public class FluidRequirementComponent(
    public val container: FluidContainerComponent
) : RecipeRequirementComponent {

    override val type: RecipeRequirementType<*>
        get() = RecipeRequirementTypes.FLUID

}