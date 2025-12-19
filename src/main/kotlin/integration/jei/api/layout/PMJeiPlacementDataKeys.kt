package github.kasuminova.prototypemachinery.integration.jei.api.layout

/**
 * Well-known keys for renderer-specific placement data.
 *
 * These keys are primarily intended for script/layout authors.
 */
public object PMJeiPlacementDataKeys {

    /** Int pixels. Overrides the JEI energy bar IO led Y offset for this particular placed node. */
    public const val ENERGY_LED_Y_OFFSET: String = "energyLedYOffset"
}
