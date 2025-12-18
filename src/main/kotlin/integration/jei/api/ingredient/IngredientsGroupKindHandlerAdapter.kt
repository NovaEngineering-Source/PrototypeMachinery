package github.kasuminova.prototypemachinery.integration.jei.api.ingredient

import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlot
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotKind
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotRole
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementNode
import mezz.jei.api.IGuiHelper
import mezz.jei.api.gui.IDrawable
import mezz.jei.api.gui.IGuiIngredientGroup
import mezz.jei.api.gui.IRecipeLayout
import mezz.jei.api.ingredients.IIngredientRenderer
import mezz.jei.api.recipe.IIngredientType

/**
 * A small utility adapter for custom (non-vanilla) ingredient kinds.
 *
 * Most custom ingredient integrations in JEI 1.12 follow the same pattern:
 * - get the ingredient group via [IRecipeLayout.getIngredientsGroup]
 * - init the slot at (x, y)
 * - set the displayed values
 *
 * This class captures that boilerplate so addon authors can focus on:
 * - defining a [JeiSlotKind]
 * - providing the JEI [IIngredientType]
 * - (optionally) providing an [IIngredientRenderer] to respect slot width/height
 */
public open class IngredientsGroupKindHandlerAdapter<T : Any>(
    override val kind: JeiSlotKind,
    override val ingredientType: IIngredientType<T>,

    /**
     * Optional renderer used for the full init overload that respects slot width/height.
     *
     * If omitted, the adapter will fall back to the simple init overload (x/y only).
     */
    private val ingredientRenderer: IIngredientRenderer<T>? = null,

    /** Optional background for each slot index. */
    private val background: ((guiHelper: IGuiHelper) -> IDrawable?)? = null,

    /** Extra padding parameters for JEI's full init overload. */
    private val paddingX: Int = 0,
    private val paddingY: Int = 0,
) : PMJeiIngredientKindHandler<T> {

    protected open fun group(recipeLayout: IRecipeLayout): IGuiIngredientGroup<T> {
        return recipeLayout.getIngredientsGroup(ingredientType)
    }

    protected open fun isInput(slot: JeiSlot): Boolean {
        return slot.role == JeiSlotRole.INPUT || slot.role == JeiSlotRole.CATALYST
    }

    override fun init(
        recipeLayout: IRecipeLayout,
        guiHelper: IGuiHelper,
        slot: JeiSlot,
        node: PMJeiRequirementNode<*>?,
    ) {
        val group = group(recipeLayout)

        val input = isInput(slot)
        val renderer = ingredientRenderer

        if (renderer != null) {
            group.init(
                slot.index,
                input,
                renderer,
                slot.x,
                slot.y,
                slot.width,
                slot.height,
                paddingX,
                paddingY,
            )
        } else {
            // Fallback: x/y only. Slot width/height will be controlled by JEI's default.
            group.init(slot.index, input, slot.x, slot.y)
        }

        background?.invoke(guiHelper)?.let { group.setBackground(slot.index, it) }
    }

    override fun set(recipeLayout: IRecipeLayout, slot: JeiSlot, values: List<T>) {
        group(recipeLayout).set(slot.index, values)
    }
}
