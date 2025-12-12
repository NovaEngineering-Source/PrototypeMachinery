package github.kasuminova.prototypemachinery.impl.key.item

import github.kasuminova.prototypemachinery.api.key.PMKey
import net.minecraft.item.ItemStack

/**
 * # ItemStackKey
 * # 物品栈键
 *
 * Interface for keys representing an ItemStack.
 *
 * 表示物品栈的键的接口。
 */
public interface PMItemKey {

    /**
     * The underlying unique prototype key.
     *
     * 底层唯一原型键。
     */
    public val uniqueKey: UniquePMItemKey

    /**
     * Casts this to the base PMKey class for usage in generic contexts.
     *
     * 将此转换为基础 PMKey 类，以便在泛型上下文中使用。
     */
    public fun asPMKey(): PMKey<ItemStack>

}