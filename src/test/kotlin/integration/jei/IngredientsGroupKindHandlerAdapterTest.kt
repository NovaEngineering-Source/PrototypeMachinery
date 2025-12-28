package integration.jei

import github.kasuminova.prototypemachinery.integration.jei.api.ingredient.IngredientsGroupKindHandlerAdapter
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlot
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotKind
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotRole
import mezz.jei.api.IGuiHelper
import mezz.jei.api.gui.IGuiIngredientGroup
import mezz.jei.api.gui.IRecipeLayout
import mezz.jei.api.ingredients.IIngredientRenderer
import mezz.jei.api.ingredients.IIngredients
import mezz.jei.api.recipe.IFocus
import mezz.jei.api.recipe.IIngredientType
import net.minecraft.util.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IngredientsGroupKindHandlerAdapterTest {

    private data class GasStack(val id: String)

    private object GasKind : JeiSlotKind {
        override val id: ResourceLocation = ResourceLocation("test", "ingredient/gas")
    }

    private object GasType : IIngredientType<GasStack> {
        override fun getIngredientClass(): Class<out GasStack> = GasStack::class.java
    }

    private class RecordingGroup<T> : IGuiIngredientGroup<T> {
        var lastInit: List<Any?>? = null
        var lastSetIndex: Int? = null
        var lastSetValues: List<T>? = null
        var lastBackgroundIndex: Int? = null

        override fun init(slotIndex: Int, input: Boolean, xPosition: Int, yPosition: Int) {
            lastInit = listOf("simple", slotIndex, input, xPosition, yPosition)
        }

        override fun init(
            slotIndex: Int,
            input: Boolean,
            ingredientRenderer: IIngredientRenderer<T>,
            xPosition: Int,
            yPosition: Int,
            width: Int,
            height: Int,
            paddingX: Int,
            paddingY: Int
        ) {
            lastInit = listOf("full", slotIndex, input, ingredientRenderer, xPosition, yPosition, width, height, paddingX, paddingY)
        }

        override fun set(ingredients: IIngredients) {
            // Not used by this test.
        }

        override fun set(slotIndex: Int, ingredients: MutableList<T>?) {
            lastSetIndex = slotIndex
            lastSetValues = ingredients?.toList().orEmpty()
        }

        override fun set(slotIndex: Int, ingredient: T?) {
            lastSetIndex = slotIndex
            lastSetValues = ingredient?.let { listOf(it) } ?: emptyList()
        }

        override fun setBackground(slotIndex: Int, background: mezz.jei.api.gui.IDrawable) {
            lastBackgroundIndex = slotIndex
        }

        override fun addTooltipCallback(tooltipCallback: mezz.jei.api.gui.ITooltipCallback<T>) {
            // Not used by this test.
        }

        override fun setOverrideDisplayFocus(focus: IFocus<T>?) {
            // Not used by this test.
        }

        override fun getGuiIngredients(): MutableMap<Int, out mezz.jei.api.gui.IGuiIngredient<T>> {
            return mutableMapOf()
        }
    }

    private class TestLayout<T>(private val group: IGuiIngredientGroup<T>) : IRecipeLayout {
        override fun getItemStacks(): mezz.jei.api.gui.IGuiItemStackGroup {
            throw UnsupportedOperationException()
        }

        override fun getFluidStacks(): mezz.jei.api.gui.IGuiFluidStackGroup {
            throw UnsupportedOperationException()
        }

        override fun <X> getIngredientsGroup(ingredientType: IIngredientType<X>): IGuiIngredientGroup<X> {
            @Suppress("UNCHECKED_CAST")
            return group as IGuiIngredientGroup<X>
        }

        override fun getFocus(): mezz.jei.api.recipe.IFocus<*> {
            throw UnsupportedOperationException()
        }

        override fun getRecipeCategory(): mezz.jei.api.recipe.IRecipeCategory<*> {
            throw UnsupportedOperationException()
        }

        override fun setRecipeTransferButton(x: Int, y: Int) = Unit

        override fun setRecipeTransferButton(x: Int, y: Int, doTransfer: Boolean) = Unit

        override fun setRecipeFavoriteButton(x: Int, y: Int) = Unit

        override fun setRecipeBookmarkButton(x: Int, y: Int) = Unit

        override fun setShapeless() = Unit

        override fun <X> getIngredientsGroup(ingredientClass: Class<X>): IGuiIngredientGroup<X> {
            @Suppress("UNCHECKED_CAST")
            return group as IGuiIngredientGroup<X>
        }
    }

    private fun dummyGuiHelper(): IGuiHelper {
        return object : IGuiHelper {
            override fun toString(): String = "DummyIGuiHelper"

            // This test must not call any IGuiHelper methods.
            // Implementations throw to ensure we notice accidental usage.
            override fun <V> createDrawableIngredient(ingredient: V): mezz.jei.api.gui.IDrawable {
                throw UnsupportedOperationException("IGuiHelper methods should not be called by this test")
            }

            override fun createBlankDrawable(width: Int, height: Int): mezz.jei.api.gui.IDrawableStatic {
                throw UnsupportedOperationException("IGuiHelper methods should not be called by this test")
            }

            override fun drawableBuilder(resourceLocation: ResourceLocation, u: Int, v: Int, width: Int, height: Int): mezz.jei.api.gui.IDrawableBuilder {
                throw UnsupportedOperationException("IGuiHelper methods should not be called by this test")
            }

            override fun createAnimatedDrawable(
                drawable: mezz.jei.api.gui.IDrawableStatic,
                ticksPerCycle: Int,
                startDirection: mezz.jei.api.gui.IDrawableAnimated.StartDirection,
                inverted: Boolean
            ): mezz.jei.api.gui.IDrawableAnimated {
                throw UnsupportedOperationException("IGuiHelper methods should not be called by this test")
            }

            override fun getSlotDrawable(): mezz.jei.api.gui.IDrawableStatic {
                throw UnsupportedOperationException("IGuiHelper methods should not be called by this test")
            }

            override fun createCraftingGridHelper(craftInputSlot1: Int, craftInputSlot2: Int): mezz.jei.api.gui.ICraftingGridHelper {
                throw UnsupportedOperationException("IGuiHelper methods should not be called by this test")
            }

            override fun createTickTimer(ticksPerCycle: Int, startValue: Int, countDown: Boolean): mezz.jei.api.gui.ITickTimer {
                throw UnsupportedOperationException("IGuiHelper methods should not be called by this test")
            }
        }
    }

    @Test
    fun `init uses simple overload when no renderer`() {
        val group = RecordingGroup<GasStack>()
        val layout = TestLayout(group)

        val handler = IngredientsGroupKindHandlerAdapter(
            kind = GasKind,
            ingredientType = GasType,
            ingredientRenderer = null,
        )

        val slot = JeiSlot(
            kind = GasKind,
            nodeId = "n1",
            index = 3,
            role = JeiSlotRole.INPUT,
            x = 11,
            y = 22,
            width = 18,
            height = 18,
        )

        handler.init(layout, dummyGuiHelper(), slot, null)
        assertEquals(listOf("simple", 3, true, 11, 22), group.lastInit)
    }

    @Test
    fun `init uses full overload when renderer exists and set forwards values`() {
        val group = RecordingGroup<GasStack>()
        val layout = TestLayout(group)

        val renderer = IIngredientRenderer<GasStack> { _, _, _, _ -> }

        val handler = IngredientsGroupKindHandlerAdapter(
            kind = GasKind,
            ingredientType = GasType,
            ingredientRenderer = renderer,
            background = { null },
            paddingX = 1,
            paddingY = 2,
        )

        val slot = JeiSlot(
            kind = GasKind,
            nodeId = "n1",
            index = 1,
            role = JeiSlotRole.OUTPUT,
            x = 5,
            y = 6,
            width = 20,
            height = 21,
        )

        handler.init(layout, dummyGuiHelper(), slot, null)
        // full init: slotIndex, input, renderer, x, y, w, h, padX, padY
        assertEquals(listOf("full", 1, false, renderer, 5, 6, 20, 21, 1, 2), group.lastInit)

        val values = listOf(GasStack("a"), GasStack("b"))
        handler.set(layout, slot, values)
        assertEquals(1, group.lastSetIndex)
        assertEquals(values, group.lastSetValues)
    }
}
