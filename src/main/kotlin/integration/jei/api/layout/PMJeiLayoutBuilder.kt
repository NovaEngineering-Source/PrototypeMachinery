package github.kasuminova.prototypemachinery.integration.jei.api.layout

import net.minecraft.util.ResourceLocation

/**
 * Collects a layout plan (node placements + decorator placements).
 *
 * This builder is intentionally JEI-API agnostic and side-effect free.
 * The runtime will translate the plan into JEI ingredient slots and ModularUI widgets.
 */
public interface PMJeiLayoutBuilder {

    /** Place a requirement node on the JEI background. */
    public fun placeNode(
        nodeId: String,
        x: Int,
        y: Int,
        variantId: ResourceLocation? = null,
    )

    /** Add a decorator instance. Decorator may interpret x/y as its anchor. */
    public fun addDecorator(
        decoratorId: ResourceLocation,
        x: Int = 0,
        y: Int = 0,
        data: Map<String, Any> = emptyMap(),
    )
}

public data class PMJeiPlacedNode(
    public val nodeId: String,
    public val x: Int,
    public val y: Int,
    public val variantId: ResourceLocation? = null,
)

public data class PMJeiDecoratorPlacement(
    public val decoratorId: ResourceLocation,
    public val x: Int = 0,
    public val y: Int = 0,
    public val data: Map<String, Any> = emptyMap(),
)
