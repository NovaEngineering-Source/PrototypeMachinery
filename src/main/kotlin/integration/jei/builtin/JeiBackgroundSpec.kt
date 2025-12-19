package github.kasuminova.prototypemachinery.integration.jei.builtin

import github.kasuminova.prototypemachinery.PrototypeMachinery
import net.minecraft.util.ResourceLocation

/**
 * Background customization hook for JEI panel runtime.
 *
 * This is implemented as a reserved decorator id that is *consumed* by the runtime
 * (so it won't go through the normal decorator registry).
 */
public object JeiBackgroundSpec {

    /** Reserved decorator id used to override the default JEI panel background. */
    public val id: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "decorator/background")

    public data class Spec(
        val texture: ResourceLocation,
        val borderPx: Int = 2,
        val fillCenter: Boolean = false,
    )

    /**
     * Parse a background spec from a decorator data map.
     *
     * Supported keys:
     * - texture: String
     *   - if contains ':' -> treated as a raw [ResourceLocation] (must point to a valid texture)
     *   - else -> treated as a path relative to `prototypemachinery:textures/gui/jei_recipeicons/`
     * - borderPx: Number (default 2)
     * - fillCenter: Boolean (default false)
     */
    public fun parse(data: Map<String, Any>): Spec {
        val texRaw = data["texture"] as? String
        val texture = if (texRaw.isNullOrBlank()) {
            PMJeiIcons.tex("jei_base.png")
        } else if (texRaw.contains(':')) {
            ResourceLocation(texRaw)
        } else {
            PMJeiIcons.tex(texRaw)
        }

        val borderPx = (data["borderPx"] as? Number)?.toInt() ?: 2
        val fillCenter = (data["fillCenter"] as? Boolean) ?: false
        return Spec(texture = texture, borderPx = borderPx.coerceAtLeast(0), fillCenter = fillCenter)
    }
}
