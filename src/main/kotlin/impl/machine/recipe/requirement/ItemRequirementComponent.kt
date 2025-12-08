package github.kasuminova.prototypemachinery.impl.machine.recipe.requirement

import github.kasuminova.prototypemachinery.api.machine.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.machine.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.common.registry.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.impl.machine.component.container.ItemContainerComponent

public class ItemRequirementComponent(
    public val container: ItemContainerComponent
) : RecipeRequirementComponent {

    override val type: RecipeRequirementType<*>
        get() = RecipeRequirementTypes.ITEM

}