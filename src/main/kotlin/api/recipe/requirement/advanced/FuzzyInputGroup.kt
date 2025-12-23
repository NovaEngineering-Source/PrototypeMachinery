package github.kasuminova.prototypemachinery.api.recipe.requirement.advanced

import github.kasuminova.prototypemachinery.api.key.PMKey

/**
 * A fuzzy input slot.
 *
 * [candidates] are tried in order; the first satisfiable one will be locked for actual execution.
 */
public data class FuzzyInputGroup<T>(
    public val candidates: List<PMKey<T>>,
    public val count: Long,
)
