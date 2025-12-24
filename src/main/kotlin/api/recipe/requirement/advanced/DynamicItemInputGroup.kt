package github.kasuminova.prototypemachinery.api.recipe.requirement.advanced

import github.kasuminova.prototypemachinery.api.key.PMKey
import net.minecraft.item.ItemStack

/**
 * Dynamic item fuzzy-like input.
 *
 * This is similar to [FuzzyInputGroup], but candidates are not stored directly.
 * Instead, candidates are enumerated from machine item containers at runtime,
 * filtered by a matcher ([matcherId]) against [pattern], then the first satisfiable
 * candidate is locked for the process (same resolution semantics as fuzzy_inputs).
 *
 * For JEI display, [displayedCandidates] can be provided as a stable, deterministic set.
 */
public data class DynamicItemInputGroup(
    /** Registered matcher id. */
    public val matcherId: String,

    /** Pattern/prototype used by the matcher. Count is ignored. */
    public val pattern: PMKey<ItemStack>,

    /** Required count per parallel execution. */
    public val count: Long,

    /** Candidates to show in JEI (optional). */
    public val displayedCandidates: List<PMKey<ItemStack>> = emptyList(),

    /** Hard cap to avoid enumerating too many candidates. */
    public val maxCandidates: Int = 256,
) {
    init {
        require(matcherId.isNotBlank()) { "DynamicItemInputGroup: matcherId is blank" }
        require(count > 0L) { "DynamicItemInputGroup: count must be > 0" }
        require(maxCandidates >= 1) { "DynamicItemInputGroup: maxCandidates must be >= 1" }
    }
}
