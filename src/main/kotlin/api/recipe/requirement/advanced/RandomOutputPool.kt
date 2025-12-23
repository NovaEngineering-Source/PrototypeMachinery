package github.kasuminova.prototypemachinery.api.recipe.requirement.advanced

import github.kasuminova.prototypemachinery.api.key.PMKey

/**
 * Weighted key for random output selection.
 */
public data class WeightedKey<T>(
    public val key: PMKey<T>,
    public val weight: Int,
)

/**
 * Random output pool:
 * - pick [pickCount] distinct candidates per "output loop" (without replacement)
 * - each chosen candidate outputs its [WeightedKey.key.count]
 */
public data class RandomOutputPool<T>(
    public val candidates: List<WeightedKey<T>>,
    public val pickCount: Int,
)
