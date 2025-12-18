package integration.jei

import github.kasuminova.prototypemachinery.integration.jei.api.ingredient.IngredientsGroupKindHandlerAdapter
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlot
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotKind
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotRole
import mezz.jei.api.IGuiHelper
import mezz.jei.api.gui.IGuiIngredientGroup
import mezz.jei.api.gui.IRecipeLayout
import mezz.jei.api.ingredients.IIngredientRenderer
import mezz.jei.api.recipe.IIngredientType
import net.minecraft.util.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.Collections

class IngredientsGroupKindHandlerAdapterTest {

    private data class GasStack(val id: String)

    private object GasKind : JeiSlotKind {
        override val id: ResourceLocation = ResourceLocation("test", "ingredient/gas")
    }

    private object GasType : IIngredientType<GasStack> {
        override fun getIngredientClass(): Class<out GasStack> = GasStack::class.java
    }

    private class RecordingGroup<T> {
        var lastInit: List<Any?>? = null
        var lastSetIndex: Int? = null
        var lastSetValues: List<T>? = null
        var lastBackgroundIndex: Int? = null

        val proxy: IGuiIngredientGroup<T> = Proxy.newProxyInstance(
            IGuiIngredientGroup::class.java.classLoader,
            arrayOf(IGuiIngredientGroup::class.java),
        ) { _, method, args ->
            when (method.name) {
                "init" -> {
                    val a = args?.toList().orEmpty()
                    // Distinguish overloads by parameter count.
                    lastInit = if (a.size == 4) listOf("simple") + a else listOf("full") + a
                    null
                }

                "set" -> {
                    val a = args?.toList().orEmpty()
                    if (a.size >= 2) {
                        @Suppress("UNCHECKED_CAST")
                        lastSetIndex = a[0] as? Int

                        @Suppress("UNCHECKED_CAST")
                        lastSetValues = when (val v = a[1]) {
                            is java.util.List<*> -> v.filterNotNull() as List<T>
                            null -> emptyList()
                            else -> listOf(v as T)
                        }
                    }
                    null
                }

                "setBackground" -> {
                    val a = args?.toList().orEmpty()
                    lastBackgroundIndex = a.getOrNull(0) as? Int
                    null
                }

                "getGuiIngredients" -> Collections.emptyMap<Int, Any>()

                else -> null
            }
        } as IGuiIngredientGroup<T>
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
        return Proxy.newProxyInstance(
            IGuiHelper::class.java.classLoader,
            arrayOf(IGuiHelper::class.java),
        ) { _, _, _ ->
            throw UnsupportedOperationException("IGuiHelper methods should not be called by this test")
        } as IGuiHelper
    }

    @Test
    fun `init uses simple overload when no renderer`() {
        val group = RecordingGroup<GasStack>()
        val layout = TestLayout(group.proxy)

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
        val layout = TestLayout(group.proxy)

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
