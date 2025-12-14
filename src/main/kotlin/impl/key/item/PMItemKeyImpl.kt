package github.kasuminova.prototypemachinery.impl.key.item

import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.api.key.PMKeyType
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound

/**
 * # ItemStackKey Implementation
 * # 物品栈键实现
 *
 * Implementation of [PMKey] for ItemStacks.
 *
 * ItemStack 的 [PMKey] 实现。
 */
@Suppress("EqualsOrHashCode")
public class PMItemKeyImpl(
    public override val uniqueKey: UniquePMItemKey,
    public override var count: Long
) : PMKey<ItemStack>(), PMItemKey {

    public override val type: PMKeyType
        get() = PMItemKeyType

    // Hash code is derived ONLY from the unique key (prototype).
    // This means keys with different counts have the same hash code.
    // 哈希码仅源自唯一键（原型）。
    // 这意味着具有不同数量的键具有相同的哈希码。
    override val internalHashCode: Int = uniqueKey.hashCode()

    public override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PMItemKey) return false
        // Equality is based on the unique key reference.
        // This ignores count!
        // 相等性基于唯一键引用。
        // 这忽略了数量！
        return this.uniqueKey === other.uniqueKey
    }

    public override fun copy(): PMKey<ItemStack> {
        return PMItemKeyImpl(uniqueKey, count)
    }

    public override fun writeNBT(nbt: NBTTagCompound): NBTTagCompound {
        // Write the item data
        val stack = uniqueKey.createStack(1)
        stack.writeToNBT(nbt)
        nbt.setLong("PMCount", count)
        return nbt
    }

    public override fun get(): ItemStack {
        // Clamp count for vanilla ItemStack.
        // We intentionally allow > 64, but keep a safety cap to reduce overflow/compat risks.
        val cap = Int.MAX_VALUE / 2
        val stackCount = when {
            count <= 0L -> 0
            count > cap.toLong() -> cap
            else -> count.toInt()
        }
        return uniqueKey.createStack(stackCount)
    }

    public override fun asPMKey(): PMKey<ItemStack> = this

    public override fun toString(): String = "${count}x${uniqueKey.item.registryName}"

}
