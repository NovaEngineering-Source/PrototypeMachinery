package github.kasuminova.prototypemachinery.integration.jei.api.layout

import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotRole
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

    /**
     * Place a fixed (non-recipe-node) ingredient slot.
     *
     * The slot's displayed values are provided by [providerId] via registry at runtime.
     * This creates a real JEI ingredient slot (clickable, hoverable) but it is NOT tied to a requirement node.
     *
     * Default implementation is a no-op so existing layouts don't need to care.
     */
    public fun placeFixedSlot(
        providerId: ResourceLocation,
        role: JeiSlotRole = JeiSlotRole.CATALYST,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        // default no-op
    }
}

public data class PMJeiPlacedNode(
    public val nodeId: String,
    public val x: Int,
    public val y: Int,
    public val variantId: ResourceLocation? = null,
)

public data class PMJeiFixedSlotPlacement(
    public val providerId: ResourceLocation,
    public val role: JeiSlotRole = JeiSlotRole.CATALYST,
    public val x: Int,
    public val y: Int,
    public val width: Int,
    public val height: Int,
)

public data class PMJeiDecoratorPlacement(
    public val decoratorId: ResourceLocation,
    public val x: Int = 0,
    public val y: Int = 0,
    public val data: Map<String, Any> = emptyMap(),
)
