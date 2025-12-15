package github.kasuminova.prototypemachinery.impl.recipe.modifier

import java.util.concurrent.ConcurrentHashMap

/**
 * Global registry for [RecipeOverlayModifier]s.
 */
public object RecipeOverlayModifierRegistry {

    private val modifiers: MutableMap<String, RecipeOverlayModifier> = ConcurrentHashMap()

    public fun register(modifier: RecipeOverlayModifier) {
        val prev = modifiers.put(modifier.id, modifier)
        if (prev != null && prev !== modifier) {
            // last registration wins
        }
    }

    public fun unregister(id: String) {
        modifiers.remove(id)
    }

    public fun get(id: String): RecipeOverlayModifier? = modifiers[id]

    /** For tests only. */
    internal fun clear() {
        modifiers.clear()
    }
}
