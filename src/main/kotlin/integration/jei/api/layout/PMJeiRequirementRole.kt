package github.kasuminova.prototypemachinery.integration.jei.api.layout

/**
 * Role of a requirement node in a JEI recipe screen.
 *
 * This is a UI-facing classification (not the IOType used by the runtime).
 */
public enum class PMJeiRequirementRole {
    INPUT,
    OUTPUT,
    INPUT_PER_TICK,
    OUTPUT_PER_TICK,
    OTHER,
}
