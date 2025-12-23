package github.kasuminova.prototypemachinery.api.recipe.selective

import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Experimental
/**
 * Global registry for selective modifiers.
 *
 * 选择性修改器全局注册表。
 */
public object SelectiveModifierRegistry {

    private val modifiers: MutableMap<String, SelectiveModifier> = ConcurrentHashMap()

    /** Register or replace a modifier by id. / 通过 id 注册（或覆盖）修改器。 */
    @JvmStatic
    public fun register(id: String, modifier: SelectiveModifier) {
        modifiers[id] = modifier
    }

    /** Resolve a modifier by id. / 通过 id 获取修改器。 */
    @JvmStatic
    public fun get(id: String): SelectiveModifier? = modifiers[id]

    /**
     * Internal test helper.
     * 仅用于测试的清理入口。
     */
    @JvmStatic
    internal fun clearForTests() {
        modifiers.clear()
    }

}
