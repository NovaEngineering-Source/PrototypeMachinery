package github.kasuminova.prototypemachinery.integration.jei.wrapper

import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.api.recipe.MachineRecipe
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.integration.jei.api.JeiRecipeContext
import github.kasuminova.prototypemachinery.integration.jei.api.ingredient.PMJeiIngredientKindHandler
import github.kasuminova.prototypemachinery.integration.jei.api.ingredient.PMJeiNodeIngredientProvider
import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiRequirementRole
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementNode
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementRenderer
import github.kasuminova.prototypemachinery.integration.jei.builtin.PMJeiBuiltins
import github.kasuminova.prototypemachinery.integration.jei.registry.JeiIngredientKindRegistry
import github.kasuminova.prototypemachinery.integration.jei.registry.JeiNodeIngredientProviderRegistry
import github.kasuminova.prototypemachinery.integration.jei.registry.JeiRequirementRendererRegistry
import github.kasuminova.prototypemachinery.integration.jei.runtime.JeiPanelRuntime
import github.kasuminova.prototypemachinery.integration.jei.util.JeiControllerStack
import mezz.jei.api.ingredients.IIngredients
import mezz.jei.api.ingredients.VanillaTypes
import mezz.jei.api.recipe.IIngredientType
import mezz.jei.api.recipe.IRecipeWrapper
import net.minecraft.client.Minecraft

/**
 * Minimal JEI recipe wrapper for PrototypeMachinery recipes.
 *
 * Ingredients are provided via requirement renderers + node ingredient providers,
 * and written into [mezz.jei.api.ingredients.IIngredients] for JEI indexing.
 */
public class PMMachineRecipeWrapper(
    public val machineType: MachineType,
    public val recipe: MachineRecipe,
) : IRecipeWrapper {

    /** Set by category#setRecipe. */
    @Volatile
    public var runtime: JeiPanelRuntime? = null

    @Suppress("UNCHECKED_CAST")
    override fun getIngredients(ingredients: IIngredients) {
        // Ensure built-ins exist; wrapper can be queried before the category is instantiated.
        PMJeiBuiltins.ensureRegistered()

        val ctx = JeiRecipeContext(machineType, recipe)

        // JEI indexing relies on IIngredients, so we must expose real inputs/outputs here.
        // We support vanilla + addon custom ingredient types via registries.
        val inputsByType: MutableMap<IIngredientType<*>, MutableList<List<Any>>> = LinkedHashMap()
        val outputsByType: MutableMap<IIngredientType<*>, MutableList<List<Any>>> = LinkedHashMap()

        for ((type, components) in recipe.requirements) {
            val renderer = getUnsafeRenderer(type) ?: continue
            for (component in components) {
                val nodes = renderer.splitUnsafe(ctx, component)
                for (node in nodes) {
                    val provider = getUnsafeProvider(node.type) as? PMJeiNodeIngredientProvider<RecipeRequirementComponent, Any> ?: continue

                    val kindHandler = JeiIngredientKindRegistry.get(provider.kind.id) as? PMJeiIngredientKindHandler<Any> ?: continue

                    val displayed: List<Any> = provider.getDisplayedUnsafe(ctx, node)
                    if (displayed.isEmpty()) continue

                    val bucket = when (node.role) {
                        PMJeiRequirementRole.INPUT, PMJeiRequirementRole.INPUT_PER_TICK -> inputsByType
                        PMJeiRequirementRole.OUTPUT, PMJeiRequirementRole.OUTPUT_PER_TICK -> outputsByType
                        else -> null
                    } ?: continue

                    bucket.getOrPut(kindHandler.ingredientType) { ArrayList() } += displayed
                }
            }
        }

        for ((type, lists) in inputsByType) {
            if (lists.isEmpty()) continue
            @Suppress("UNCHECKED_CAST")
            ingredients.setInputLists(type as IIngredientType<Any>, lists)
        }

        // Make controller -> recipes discoverable in the *recipe direction* (JEI default key: R).
        // We intentionally do not declare a visible slot for it; this is a hidden output used for navigation.
        JeiControllerStack.get(machineType)?.let { controller ->
            outputsByType.getOrPut(VanillaTypes.ITEM) { ArrayList() } += listOf(controller as Any)
        }

        for ((type, lists) in outputsByType) {
            if (lists.isEmpty()) continue
            @Suppress("UNCHECKED_CAST")
            ingredients.setOutputLists(type as IIngredientType<Any>, lists)
        }
    }

    override fun drawInfo(
        minecraft: Minecraft,
        recipeWidth: Int,
        recipeHeight: Int,
        mouseX: Int,
        mouseY: Int,
    ) {
        // JEI translates the GL matrix to the recipe origin before calling drawInfo.
        // Therefore we can draw at (0,0) and use relative mouse coordinates.
        runtime?.drawAt(0, 0, mouseX, mouseY, minecraft.renderPartialTicks)
    }

    private fun getUnsafeRenderer(type: RecipeRequirementType<*>): PMJeiRequirementRenderer<RecipeRequirementComponent>? {
        @Suppress("UNCHECKED_CAST")
        return (JeiRequirementRendererRegistry.get(type as RecipeRequirementType<RecipeRequirementComponent>))
    }

    private fun getUnsafeProvider(type: RecipeRequirementType<*>): PMJeiNodeIngredientProvider<*, *>? {
        return JeiNodeIngredientProviderRegistry.get(type)
    }
}

private fun PMJeiRequirementRenderer<RecipeRequirementComponent>.splitUnsafe(
    ctx: JeiRecipeContext,
    component: RecipeRequirementComponent,
): List<PMJeiRequirementNode<out RecipeRequirementComponent>> {
    return try {
        split(ctx, component)
    } catch (_: Throwable) {
        emptyList()
    }
}

private fun PMJeiNodeIngredientProvider<RecipeRequirementComponent, Any>.getDisplayedUnsafe(
    ctx: JeiRecipeContext,
    node: PMJeiRequirementNode<out RecipeRequirementComponent>,
): List<Any> {
    return try {
        @Suppress("UNCHECKED_CAST")
        getDisplayed(ctx, node as PMJeiRequirementNode<RecipeRequirementComponent>)
    } catch (_: Throwable) {
        emptyList()
    }
}
