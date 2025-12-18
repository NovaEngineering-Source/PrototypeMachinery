package github.kasuminova.prototypemachinery.integration.jei.api.render

import net.minecraft.util.ResourceLocation

/**
 * A renderer variant describes a concrete visual form for a node.
 *
 * Examples:
 * - Fluid tank: 16x58 / 16x34 / 24x58
 * - Item slot: compact / large / with amount text
 */
public interface PMJeiRendererVariant {

    /** Stable ID for this variant (used by layouts and caching). */
    public val id: ResourceLocation

    /**
     * Logical width/height in JEI background coordinate system.
     * These are used to declare JEI ingredient slot bounds and to help layouts.
     */
    public val width: Int
    public val height: Int
}
