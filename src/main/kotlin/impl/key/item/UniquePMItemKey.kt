package github.kasuminova.prototypemachinery.impl.key.item

import net.minecraft.item.Item
import net.minecraft.item.ItemStack

/**
 * # UniqueItemStackKey
 * # 唯一物品栈键
 *
 * A runtime-unique representation of an ItemStack's definition.
 * Instances of this class are interned by [PMItemKeyType].
 *
 * ItemStack 定义的运行时唯一表示。
 * 此类的实例由 [PMItemKeyType] 驻留。
 */
public class UniquePMItemKey(private val stack: ItemStack) {

    public val item: Item get() = stack.item
    public val meta: Int get() = stack.itemDamage

    private val hash: Int

    init {
        val item = this.stack.item
        val meta = this.stack.itemDamage
        val nbt = this.stack.tagCompound

        val combinedHashCode = java.lang.Long.hashCode(
            (System.identityHashCode(item).toLong() and 0xFFFFFFFFL) or
                    ((meta.toLong() and 0xFFFFFFFFL) shl 32)
        )

        val tagHashCode = nbt?.hashCode() ?: 0
        this.hash = if (tagHashCode != 0) combinedHashCode xor tagHashCode else combinedHashCode
    }

    /**
     * Gets the ItemStack represented by this key, **UNSAFE**, use at your own risk.
     * 获取该键所表示的 ItemStack，**不安全**，请自行承担风险。
     */
    public fun getItemStackUnsafe(): ItemStack = stack

    public override fun hashCode(): Int = hash

    public override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UniquePMItemKey) return false
        return ItemStack.areItemStacksEqual(this.stack, other.stack)
    }

    public fun createStack(count: Int): ItemStack {
        val newStack = stack.copy()
        newStack.count = count
        return newStack
    }

    /**
     * Creates a deep copy of this key.
     * Useful for creating a safe, immutable key from a temporary one that holds mutable NBT references.
     * 创建此键的深拷贝。
     * 对于从持有可变 NBT 引用的临时键创建安全、不可变的键非常有用。
     */
    public fun copy(): UniquePMItemKey = UniquePMItemKey(stack.copy())

}
