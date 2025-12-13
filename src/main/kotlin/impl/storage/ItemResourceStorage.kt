package github.kasuminova.prototypemachinery.impl.storage

import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.impl.key.item.PMItemKey
import github.kasuminova.prototypemachinery.impl.key.item.PMItemKeyImpl
import github.kasuminova.prototypemachinery.impl.key.item.PMItemKeyType
import github.kasuminova.prototypemachinery.impl.key.item.UniquePMItemKey
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound

/**
 * # ItemResourceStorage - Item-specific Resource Storage
 * # ItemResourceStorage - 物品特定资源存储
 *
 * Specialized implementation of [ResourceStorageImpl] for ItemStacks.
 * Uses [UniquePMItemKey] for efficient lookup.
 *
 * [ResourceStorageImpl] 的物品栈专用实现。
 * 使用 [UniquePMItemKey] 进行高效查找。
 *
 * @param maxTypes Maximum number of different item types
 * @param maxCountPerType Maximum count per item type
 */
public class ItemResourceStorage(
    maxTypes: Int,
    maxCountPerType: Long = Long.MAX_VALUE
) : ResourceStorageImpl<PMKey<ItemStack>>(maxTypes, maxCountPerType) {

    override fun getUniqueKey(key: PMKey<ItemStack>): Any {
        return (key as PMItemKey).uniqueKey
    }

    /**
     * Convenience method to insert an ItemStack.
     *
     * 便捷方法，用于插入 ItemStack。
     */
    public fun insertStack(stack: ItemStack, simulate: Boolean): Long {
        if (stack.isEmpty) return 0L
        val key = PMItemKeyType.create(stack)
        return insert(key, stack.count.toLong(), simulate)
    }

    /**
     * Convenience method to extract an ItemStack.
     *
     * 便捷方法，用于提取 ItemStack。
     */
    public fun extractStack(stack: ItemStack, simulate: Boolean): Long {
        if (stack.isEmpty) return 0L
        val key = PMItemKeyType.create(stack)
        return extract(key, stack.count.toLong(), simulate)
    }

    /**
     * Extracts a specific amount of items matching the stack.
     * Returns the extracted ItemStack.
     *
     * 提取与栈匹配的特定数量的物品。
     * 返回提取的 ItemStack。
     */
    public fun extractStackResult(stack: ItemStack, maxAmount: Int, simulate: Boolean): ItemStack {
        if (stack.isEmpty) return ItemStack.EMPTY
        val key = PMItemKeyType.create(stack)
        val extracted = extract(key, maxAmount.toLong(), simulate)
        if (extracted <= 0) return ItemStack.EMPTY
        val result = stack.copy()
        result.count = extracted.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return result
    }

    /**
     * Gets a resource by its ItemStack representation.
     *
     * 通过 ItemStack 表示获取资源。
     */
    public fun getByStack(stack: ItemStack): PMKey<ItemStack>? {
        if (stack.isEmpty) return null
        val uniqueKey = PMItemKeyType.getUniqueKey(stack)
        return storage[uniqueKey]
    }

    /**
     * Writes this storage to NBT.
     *
     * 将此存储写入 NBT。
     */
    public fun writeNBT(nbt: NBTTagCompound): NBTTagCompound {
        return writeNBT(nbt) { key, keyNbt ->
            key.writeNBT(keyNbt)
        }
    }

    /**
     * Reads this storage from NBT.
     *
     * 从 NBT 读取此存储。
     */
    public fun readNBT(nbt: NBTTagCompound) {
        readNBT(nbt) { keyNbt ->
            PMItemKeyType.readNBT(keyNbt) as? PMItemKeyImpl
        }
    }

}
