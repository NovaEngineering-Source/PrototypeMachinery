package github.kasuminova.prototypemachinery.impl.machine.recipe.requirement

import github.kasuminova.prototypemachinery.api.machine.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.machine.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.common.registry.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.impl.machine.component.container.EnergyContainerComponent

public class EnergyRequirementComponent(
    public val container: EnergyContainerComponent
) : RecipeRequirementComponent {

    override val type: RecipeRequirementType<*>
        get() = RecipeRequirementTypes.ENERGY

}