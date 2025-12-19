package github.kasuminova.prototypemachinery.integration.jei.builtin

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRendererVariant
import net.minecraft.util.ResourceLocation

/**
 * Registry of built-in JEI icon variants and texture constants.
 */
public object PMJeiIcons {

    private const val BASE_PATH = "prototypemachinery:textures/gui/jei_recipeicons"

    public fun tex(path: String): ResourceLocation = ResourceLocation("$BASE_PATH/$path")

    // --- Item Module ---
    public val ITEM_PLAID: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "item/plaid")

    // --- Plaid Module ---
    public val PLAID_BASE_0101: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "plaid/base_0101")
    public val PLAID_BASE_0111: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "plaid/base_0111")
    public val PLAID_BASE_0110: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "plaid/base_0110")
    public val PLAID_BASE_1101: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "plaid/base_1101")
    public val PLAID_BASE_1111: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "plaid/base_1111")
    public val PLAID_BASE_1110: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "plaid/base_1110")
    public val PLAID_BASE_1001: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "plaid/base_1001")
    public val PLAID_BASE_1011: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "plaid/base_1011")
    public val PLAID_BASE_1010: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "plaid/base_1010")

    public val PLAID_TOP_0000: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "plaid/top_0000")
    public val PLAID_TOP_1011: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "plaid/top_1011")
    public val PLAID_TOP_0011: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "plaid/top_0011")
    public val PLAID_TOP_0111: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "plaid/top_0111")
    public val PLAID_TOP_1110: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "plaid/top_1110")
    public val PLAID_TOP_1100: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "plaid/top_1100")
    public val PLAID_TOP_1101: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "plaid/top_1101")
    public val PLAID_TOP_1001: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "plaid/top_1001")
    public val PLAID_TOP_0101: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "plaid/top_0101")
    public val PLAID_TOP_0110: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "plaid/top_0110")
    public val PLAID_TOP_1010: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "plaid/top_1010")
    public val PLAID_TOP_1000: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "plaid/top_1000")
    public val PLAID_TOP_0010: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "plaid/top_0010")
    public val PLAID_TOP_0001: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "plaid/top_0001")
    public val PLAID_TOP_0100: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "plaid/top_0100")

    // --- Fluid Module ---
    public val FLUID_PLAID_1X1: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "fluid/plaid_1x1")
    public val FLUID_0O5X1: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "fluid/0o5x1")
    public val FLUID_0O5X2: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "fluid/0o5x2")
    public val FLUID_0O5X3: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "fluid/0o5x3")
    public val FLUID_0O5X4: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "fluid/0o5x4")
    public val FLUID_1X1: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "fluid/1x1")
    public val FLUID_1X2: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "fluid/1x2")
    public val FLUID_1X3: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "fluid/1x3")
    public val FLUID_1X4: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "fluid/1x4")
    public val FLUID_1O5X1: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "fluid/1o5x1")
    public val FLUID_1O5X2: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "fluid/1o5x2")
    public val FLUID_1O5X3: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "fluid/1o5x3")
    public val FLUID_1O5X4: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "fluid/1o5x4")
    public val FLUID_2X1: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "fluid/2x1")
    public val FLUID_2X2: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "fluid/2x2")
    public val FLUID_2X3: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "fluid/2x3")
    public val FLUID_2X4: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "fluid/2x4")

    // --- Energy Module ---
    public val ENERGY_DEFAULT: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "energy/default")
    public val ENERGY_1X1: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "energy/1x1")
    public val ENERGY_1X2: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "energy/1x2")
    public val ENERGY_1X3: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "energy/1x3")
    public val ENERGY_1X4: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "energy/1x4")
    public val ENERGY_2X1: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "energy/2x1")
    public val ENERGY_2X2: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "energy/2x2")
    public val ENERGY_2X3: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "energy/2x3")
    public val ENERGY_2X4: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "energy/2x4")

    // --- Progress Module ---
    public val PROGRESS_COMPRESS: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "progress/compress")
    public val PROGRESS_CUT: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "progress/cut")
    public val PROGRESS_MERGE: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "progress/merge")
    public val PROGRESS_SPLIT: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "progress/split")
    public val PROGRESS_HEAT: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "progress/heat")
    public val PROGRESS_COOL: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "progress/cool")
    public val PROGRESS_ROLLING: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "progress/rolling")
    public val PROGRESS_RIGHT: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "progress/right")
    public val PROGRESS_LEFT: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "progress/left")
    public val PROGRESS_UP: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "progress/up")
    public val PROGRESS_DOWN: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "progress/down")

    // --- Variants ---

    public class SimpleVariant(
        override val id: ResourceLocation,
        override val width: Int,
        override val height: Int,
    ) : PMJeiRendererVariant

    public val ALL_VARIANTS: Map<ResourceLocation, PMJeiRendererVariant> = buildMap {
        // Item
        put(ITEM_PLAID, SimpleVariant(ITEM_PLAID, 18, 18))

        // Fluid
        put(FLUID_PLAID_1X1, SimpleVariant(FLUID_PLAID_1X1, 18, 18))
        put(FLUID_0O5X1, SimpleVariant(FLUID_0O5X1, 9, 18))
        put(FLUID_0O5X2, SimpleVariant(FLUID_0O5X2, 9, 36))
        put(FLUID_0O5X3, SimpleVariant(FLUID_0O5X3, 9, 54))
        put(FLUID_0O5X4, SimpleVariant(FLUID_0O5X4, 9, 72))
        put(FLUID_1X1, SimpleVariant(FLUID_1X1, 18, 18))
        put(FLUID_1X2, SimpleVariant(FLUID_1X2, 18, 36))
        put(FLUID_1X3, SimpleVariant(FLUID_1X3, 18, 54))
        put(FLUID_1X4, SimpleVariant(FLUID_1X4, 18, 72))
        put(FLUID_1O5X1, SimpleVariant(FLUID_1O5X1, 27, 18))
        put(FLUID_1O5X2, SimpleVariant(FLUID_1O5X2, 27, 36))
        put(FLUID_1O5X3, SimpleVariant(FLUID_1O5X3, 27, 54))
        put(FLUID_1O5X4, SimpleVariant(FLUID_1O5X4, 27, 72))
        put(FLUID_2X1, SimpleVariant(FLUID_2X1, 36, 18))
        put(FLUID_2X2, SimpleVariant(FLUID_2X2, 36, 36))
        put(FLUID_2X3, SimpleVariant(FLUID_2X3, 36, 54))
        put(FLUID_2X4, SimpleVariant(FLUID_2X4, 36, 72))

        // Energy
        put(ENERGY_DEFAULT, SimpleVariant(ENERGY_DEFAULT, 22, 72))
        put(ENERGY_1X1, SimpleVariant(ENERGY_1X1, 18, 18))
        put(ENERGY_1X2, SimpleVariant(ENERGY_1X2, 18, 36))
        put(ENERGY_1X3, SimpleVariant(ENERGY_1X3, 18, 54))
        put(ENERGY_1X4, SimpleVariant(ENERGY_1X4, 18, 72))
        put(ENERGY_2X1, SimpleVariant(ENERGY_2X1, 36, 18))
        put(ENERGY_2X2, SimpleVariant(ENERGY_2X2, 36, 36))
        put(ENERGY_2X3, SimpleVariant(ENERGY_2X3, 36, 54))
        put(ENERGY_2X4, SimpleVariant(ENERGY_2X4, 36, 72))

        // Plaid Base
        put(PLAID_BASE_0101, SimpleVariant(PLAID_BASE_0101, 18, 18))
        put(PLAID_BASE_0111, SimpleVariant(PLAID_BASE_0111, 18, 18))
        put(PLAID_BASE_0110, SimpleVariant(PLAID_BASE_0110, 18, 18))
        put(PLAID_BASE_1101, SimpleVariant(PLAID_BASE_1101, 18, 18))
        put(PLAID_BASE_1111, SimpleVariant(PLAID_BASE_1111, 18, 18))
        put(PLAID_BASE_1110, SimpleVariant(PLAID_BASE_1110, 18, 18))
        put(PLAID_BASE_1001, SimpleVariant(PLAID_BASE_1001, 18, 18))
        put(PLAID_BASE_1011, SimpleVariant(PLAID_BASE_1011, 18, 18))
        put(PLAID_BASE_1010, SimpleVariant(PLAID_BASE_1010, 18, 18))
    }
}
