package github.kasuminova.prototypemachinery.api.recipe.requirement.advanced

import net.minecraft.item.ItemStack
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry for dynamic item matchers.
 *
 * Matchers are used by [DynamicItemInputGroup] to decide whether a candidate ItemStack
 * from machine containers matches the requirement pattern.
 */
public object ItemRequirementMatcherRegistry {

    public fun interface Matcher {
        public fun matches(candidate: ItemStack, pattern: ItemStack): Boolean
    }

    private val byId: MutableMap<String, Matcher> = ConcurrentHashMap()

    public fun register(id: String, matcher: Matcher) {
        val k = id.trim()
        require(k.isNotEmpty()) { "matcher id is blank" }
        byId[k] = matcher
    }

    public fun get(id: String?): Matcher? {
        val k = id?.trim().orEmpty()
        if (k.isEmpty()) return null
        return byId[k]
    }

    public fun clear(id: String) {
        byId.remove(id.trim())
    }

    public fun clearAll() {
        byId.clear()
    }

    init {
        // A tiny built-in matcher for common cases: item+meta must match; NBT is ignored.
        register("item_only") { cand, pat ->
            if (cand.isEmpty || pat.isEmpty) return@register false
            cand.item === pat.item && cand.metadata == pat.metadata
        }

        // Exact match: item + meta + NBT.
        register("exact") { cand, pat ->
            if (cand.isEmpty || pat.isEmpty) return@register false
            ItemStack.areItemsEqual(cand, pat) && ItemStack.areItemStackTagsEqual(cand, pat)
        }
    }
}
