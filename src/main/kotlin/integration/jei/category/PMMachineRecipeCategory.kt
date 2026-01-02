package github.kasuminova.prototypemachinery.integration.jei.category

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.integration.jei.api.JeiRecipeContext
import github.kasuminova.prototypemachinery.integration.jei.api.ingredient.PMJeiIngredientKindHandler
import github.kasuminova.prototypemachinery.integration.jei.api.ingredient.PMJeiNodeIngredientProvider
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotRole
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementNode
import github.kasuminova.prototypemachinery.integration.jei.builtin.PMJeiBuiltins
import github.kasuminova.prototypemachinery.integration.jei.registry.JeiIngredientKindRegistry
import github.kasuminova.prototypemachinery.integration.jei.registry.JeiMachineLayoutRegistry
import github.kasuminova.prototypemachinery.integration.jei.registry.JeiNodeIngredientProviderRegistry
import github.kasuminova.prototypemachinery.integration.jei.runtime.JeiPanelRuntime
import github.kasuminova.prototypemachinery.integration.jei.wrapper.PMMachineRecipeWrapper
import mezz.jei.api.IGuiHelper
import mezz.jei.api.gui.IDrawable
import mezz.jei.api.gui.IRecipeLayout
import mezz.jei.api.ingredients.IIngredients
import mezz.jei.api.recipe.IRecipeCategory
import net.minecraft.util.ResourceLocation

public class PMMachineRecipeCategory(
    private val machineType: MachineType,
    guiHelper: IGuiHelper,
) : IRecipeCategory<PMMachineRecipeWrapper> {

    private val uid: String = uidFor(machineType.id)

    private val background: IDrawable = run {
        val layout = JeiMachineLayoutRegistry.resolve(machineType.id)
        val w = layout?.width ?: 160
        val h = layout?.height ?: 80
        guiHelper.createBlankDrawable(w, h)
    }

    private val guiHelper: IGuiHelper = guiHelper

    override fun getUid(): String = uid

    override fun getTitle(): String = machineType.name

    override fun getModName(): String = PrototypeMachinery.MOD_NAME

    override fun getBackground(): IDrawable = background

    @Suppress("UNCHECKED_CAST")
    override fun setRecipe(
        recipeLayout: IRecipeLayout,
        recipeWrapper: PMMachineRecipeWrapper,
        ingredients: IIngredients,
    ) {
        // Ensure built-in renderers are registered before building the runtime.
        PMJeiBuiltins.ensureRegistered()

        val ctx = JeiRecipeContext(machineType, recipeWrapper.recipe)
        val runtime = JeiPanelRuntime.build(ctx)
        if (runtime == null) {
            PrototypeMachinery.logger.warn("JEI: failed to build runtime for machineType='${machineType.id}', recipe='${recipeWrapper.recipe.id}'")
            return
        }

        recipeWrapper.runtime = runtime

        // 1) Declare slots via kind handlers (supports Vanilla + addon custom ingredient types).
        for (slot in runtime.slots) {
            val node = runtime.getNode(slot.nodeId)
            val handler = JeiIngredientKindRegistry.get(slot.kind.id) as? PMJeiIngredientKindHandler<Any>
            if (handler == null) {
                PrototypeMachinery.logger.debug(
                    "JEI: skipping init for unsupported slot kind '${slot.kind.id}' (nodeId='${slot.nodeId}')"
                )
                continue
            }
            handler.init(recipeLayout, guiHelper, slot, node)
        }

        // 2) Populate groups: requirement nodes + fixed (node-less) slots.
        for (slot in runtime.slots) {
            val node = runtime.getNode(slot.nodeId)

            val handler = JeiIngredientKindRegistry.get(slot.kind.id) as? PMJeiIngredientKindHandler<Any>
            if (handler == null) continue

            val values: List<Any> = if (node != null) {
                val provider = getUnsafeProvider(node.type) as? PMJeiNodeIngredientProvider<RecipeRequirementComponent, Any>
                    ?: continue

                val v = provider.getDisplayedUnsafe(ctx, node)
                if (v.isEmpty()) continue

                if (provider.kind.id != slot.kind.id) {
                    PrototypeMachinery.logger.warn(
                        "JEI: provider kind '${provider.kind.id}' does not match slot kind '${slot.kind.id}' " +
                            "(type='${node.type.id}', nodeId='${node.nodeId}')."
                    )
                }

                v
            } else {
                val v = runtime.getFixedValues(slot.nodeId) ?: continue
                if (v.isEmpty()) continue
                v
            }

            handler.set(recipeLayout, slot, values)
        }
    }

    public companion object {

        /**
         * Category uid format: prototypemachinery:machine/<ns>/<path>
         */
        @JvmStatic
        public fun uidFor(machineTypeId: ResourceLocation): String {
            return ResourceLocation(PrototypeMachinery.MOD_ID, "machine/${machineTypeId.namespace}/${machineTypeId.path}").toString()
        }
    }
}

private fun JeiSlotRole.isInput(): Boolean {
    return this == JeiSlotRole.INPUT || this == JeiSlotRole.CATALYST
}

private fun getUnsafeProvider(type: RecipeRequirementType<*>): PMJeiNodeIngredientProvider<*, *>? {
    return JeiNodeIngredientProviderRegistry.get(type)
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
