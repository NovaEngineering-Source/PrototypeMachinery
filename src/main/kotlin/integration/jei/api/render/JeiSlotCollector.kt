package github.kasuminova.prototypemachinery.integration.jei.api.render

import github.kasuminova.prototypemachinery.PrototypeMachinery
import net.minecraft.util.ResourceLocation

/**
 * Collects ingredient slot declarations for JEI.
 *
 * Renderers declare slots (bounds + role + data) during layout build/compile.
 * JEI category then uses these declarations to init and populate JEI ingredient groups.
 */
public interface JeiSlotCollector {

    /** Allocate the next sequential slot index for the given kind. */
    public fun nextIndex(kind: JeiSlotKind): Int

    public fun add(slot: JeiSlot)
}

/**
 * JEI ingredient slot kind.
 *
 * This is intentionally extensible: new requirement systems can introduce new kinds
 * (e.g. gases, mana, custom ingredient types) without having to modify a closed enum.
 *
 * NOTE: the built-in JEI category currently only knows how to init/populate ITEM and FLUID groups.
 */
public interface JeiSlotKind {
    public val id: ResourceLocation
}

public object JeiSlotKinds {
    /** Vanilla item stack ingredient slots. */
    public val ITEM: JeiSlotKind = Simple(ResourceLocation(PrototypeMachinery.MOD_ID, "ingredient/item"))

    /** Vanilla fluid stack ingredient slots. */
    public val FLUID: JeiSlotKind = Simple(ResourceLocation(PrototypeMachinery.MOD_ID, "ingredient/fluid"))

    /** Energy IO ingredient slots (custom JEI ingredient). */
    public val ENERGY: JeiSlotKind = Simple(ResourceLocation(PrototypeMachinery.MOD_ID, "ingredient/energy"))

    private data class Simple(override val id: ResourceLocation) : JeiSlotKind
}

public enum class JeiSlotRole {
    INPUT,
    OUTPUT,
    CATALYST,
}

/**
 * A single JEI ingredient slot declaration.
 */
public data class JeiSlot(
    public val kind: JeiSlotKind,

    /** Which requirement node produced this slot. Used to populate ingredient stacks. */
    public val nodeId: String,

    public val index: Int,
    public val role: JeiSlotRole,
    public val x: Int,
    public val y: Int,
    public val width: Int,
    public val height: Int,

    /** Optional JEI-drawn overlay (drawn by JEI after the ingredient renderer). */
    public val overlay: JeiSlotOverlay? = null,
)

/**
 * Overlay spec for JEI ingredient groups.
 *
 * For small standalone textures (like our module PNGs), JEI needs the real texture size
 * to compute correct UVs. Use [textureWidth]/[textureHeight] accordingly.
 */
public data class JeiSlotOverlay(
    public val texture: ResourceLocation,
    public val u: Int,
    public val v: Int,
    public val width: Int,
    public val height: Int,
    public val textureWidth: Int,
    public val textureHeight: Int,
)
