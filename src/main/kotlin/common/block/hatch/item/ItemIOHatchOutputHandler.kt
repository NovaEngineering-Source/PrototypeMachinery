package github.kasuminova.prototypemachinery.common.block.hatch.item

import github.kasuminova.prototypemachinery.impl.key.item.PMItemKey
import net.minecraft.item.ItemStack
import net.minecraftforge.items.IItemHandler

/**
 * # ItemIOHatchOutputHandler - IO Hatch Output Handler
 * # ItemIOHatchOutputHandler - 交互仓输出处理器
 *
 * IItemHandler wrapper for the output storage of an ItemIOHatchBlockEntity.
 * External access can only extract items, not insert.
 *
 * ItemIOHatchBlockEntity 输出存储的 IItemHandler 包装器。
 * 外部访问只能提取物品，不能插入。
 */
public class ItemIOHatchOutputHandler(
    private val blockEntity: ItemIOHatchBlockEntity
) : IItemHandler {

    override fun getSlots(): Int {
        return blockEntity.outputStorage.slotCount
    }

    override fun getStackInSlot(slot: Int): ItemStack {
        val storage = blockEntity.outputStorage
        val key = storage.getSlot(slot) ?: return ItemStack.EMPTY
        return key.get()
    }

    override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack {
        // Output handler does not allow insertion from external
        return stack
    }

    override fun extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack {
        val storage = blockEntity.outputStorage
        val key = storage.getSlot(slot) ?: return ItemStack.EMPTY
        val template = (key as? PMItemKey)?.uniqueKey?.createStack(1) ?: key.get().copy().apply { count = 1 }

        val extracted = storage.extractFromSlot(slot, amount.toLong(), simulate)
        if (extracted <= 0L) return ItemStack.EMPTY

        val result = template.copy()
        result.count = extracted.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return result
    }

    override fun getSlotLimit(slot: Int): Int {
        return blockEntity.config.outputMaxStackSize.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

}
