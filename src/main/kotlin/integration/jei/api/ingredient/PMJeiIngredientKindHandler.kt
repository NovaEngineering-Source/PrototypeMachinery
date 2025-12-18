package github.kasuminova.prototypemachinery.integration.jei.api.ingredient

import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlot
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotKind
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementNode
import mezz.jei.api.IGuiHelper
import mezz.jei.api.gui.IRecipeLayout
import mezz.jei.api.recipe.IIngredientType
import net.minecraft.util.ResourceLocation

/**
 * Bridge for a [JeiSlotKind] to an actual JEI ingredient type + group init/set logic.
 *
 * This is the missing piece for "VanillaTypes 之外"的 requirement：
 * addons can register a handler for their custom ingredient type (e.g. gas, essentia)
 * and then renderers can declare slots using a custom [JeiSlotKind].
 */
public interface PMJeiIngredientKindHandler<T : Any> {

    /** The slot kind this handler is responsible for. */
    public val kind: JeiSlotKind

    /** The JEI ingredient type used for indexing & group access. */
    public val ingredientType: IIngredientType<T>

    /** Convenience: stable id for registry lookup. */
    public val kindId: ResourceLocation
        get() = kind.id

    /**
     * Initialize (declare bounds/flags) for a JEI ingredient group slot.
     *
     * @param node optional requirement node (can be used to compute capacity etc.)
     */
    public fun init(
        recipeLayout: IRecipeLayout,
        guiHelper: IGuiHelper,
        slot: JeiSlot,
        node: PMJeiRequirementNode<*>?,
    )

    /** Set displayed values for a previously-initialized slot. */
    public fun set(
        recipeLayout: IRecipeLayout,
        slot: JeiSlot,
        values: List<T>,
    )
}
