package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.recipe

import crafttweaker.annotations.ZenRegister
import github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.impl.recipe.SimpleMachineRecipe
import net.minecraft.util.ResourceLocation
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

@ZenClass("mods.prototypemachinery.recipe.PMRecipeBuilder")
@ZenRegister
public class ZenRecipeBuilder private constructor(
    private val id: String,
    private var durationTicks: Int,
) {

    private val recipeGroups: MutableSet<ResourceLocation> = linkedSetOf()
    private val requirements: MutableMap<RecipeRequirementType<*>, MutableList<RecipeRequirementComponent>> = linkedMapOf()

    public companion object {
        @ZenMethod
        @JvmStatic
        public fun create(id: String, durationTicks: Int): ZenRecipeBuilder {
            require(id.isNotBlank()) { "recipe id cannot be blank" }
            require(durationTicks > 0) { "durationTicks must be > 0" }
            return ZenRecipeBuilder(id, durationTicks)
        }
    }

    @ZenMethod
    public fun setDurationTicks(durationTicks: Int): ZenRecipeBuilder {
        require(durationTicks > 0) { "durationTicks must be > 0" }
        this.durationTicks = durationTicks
        return this
    }

    @ZenMethod
    public fun addRecipeGroup(groupId: String): ZenRecipeBuilder {
        require(groupId.isNotBlank()) { "groupId cannot be blank" }
        recipeGroups += ResourceLocation(groupId)
        return this
    }

    @ZenMethod
    public fun addRecipeGroups(groupIds: Array<String>): ZenRecipeBuilder {
        groupIds.forEach { addRecipeGroup(it) }
        return this
    }

    @ZenMethod
    public fun addRequirement(requirement: ZenRecipeRequirement): ZenRecipeBuilder {
        val component = requirement.component
        requirements.getOrPut(component.type) { mutableListOf() }.add(component)
        return this
    }

    @ZenMethod
    public fun register() {
        val recipe = SimpleMachineRecipe(
            id = id,
            durationTicks = durationTicks,
            requirements = requirements.mapValues { it.value.toList() },
            recipeGroups = recipeGroups.toSet(),
        )

        PrototypeMachineryAPI.recipeManager.register(recipe)
    }
}
