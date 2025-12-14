package github.kasuminova.prototypemachinery.common.block.hatch.item

import github.kasuminova.prototypemachinery.common.block.hatch.HatchType
import github.kasuminova.prototypemachinery.impl.storage.ItemResourceStorage
import net.minecraft.item.ItemStack
import net.minecraftforge.items.IItemHandler

/**
 * # ItemHatchItemHandler - IItemHandler Implementation for Item Hatches
 * # ItemHatchItemHandler - 物品仓的 IItemHandler 实现
 *
 * Wraps [ItemResourceStorage] as a Forge [IItemHandler] capability.
 *
 * 将 [ItemResourceStorage] 包装为 Forge [IItemHandler] 能力。
 */
public class ItemHatchItemHandler(
    private val hatch: ItemHatchBlockEntity
) : IItemHandler {

    private val storage: ItemResourceStorage
        get() = hatch.storage

    private val config: ItemHatchConfig
        get() = hatch.config

    override fun getSlots(): Int = config.slotCount

    override fun getStackInSlot(slot: Int): ItemStack {
        val key = storage.getSlot(slot) ?: return ItemStack.EMPTY
        return key.get()
    }

    override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack {
        if (stack.isEmpty) return ItemStack.EMPTY

        // Check if we can insert based on hatch type
        if (config.hatchType == HatchType.OUTPUT) {
            return stack // Output hatches cannot accept items from outside
        }

        val inserted = storage.insertStack(stack, simulate)
        if (inserted <= 0) return stack

        val remaining = stack.copy()
        remaining.shrink(inserted.toInt())
        return if (remaining.isEmpty) ItemStack.EMPTY else remaining
    }

    override fun extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack {
        if (amount <= 0) return ItemStack.EMPTY

        // Check if we can extract based on hatch type
        if (config.hatchType == HatchType.INPUT) {
            return ItemStack.EMPTY // Input hatches cannot output items
        }

        val key = storage.getSlot(slot) ?: return ItemStack.EMPTY
        val template = (key as? github.kasuminova.prototypemachinery.impl.key.item.PMItemKey)
            ?.uniqueKey
            ?.createStack(1)
            ?: key.get().copy().apply { count = 1 }

        val extracted = storage.extractFromSlot(slot, amount.toLong(), simulate)
        if (extracted <= 0L) return ItemStack.EMPTY
        val result = template.copy()
        result.count = extracted.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return result
    }

    override fun getSlotLimit(slot: Int): Int {
        return storage.maxCountPerType.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    override fun isItemValid(slot: Int, stack: ItemStack): Boolean {
        if (config.hatchType == HatchType.OUTPUT) {
            return false // Output hatches cannot accept items
        }
        return !stack.isEmpty
    }

}
