package github.kasuminova.prototypemachinery.common.block.hatch.item

import github.kasuminova.prototypemachinery.impl.key.item.PMItemKey
import net.minecraft.item.ItemStack
import net.minecraftforge.items.IItemHandler

/**
 * # ItemIOHatchInputHandler - IO Hatch Input Handler
 * # ItemIOHatchInputHandler - 交互仓输入处理器
 *
 * IItemHandler wrapper for the input storage of an ItemIOHatchBlockEntity.
 * External access can only insert items, not extract.
 *
 * ItemIOHatchBlockEntity 输入存储的 IItemHandler 包装器。
 * 外部访问只能插入物品，不能提取。
 */
public class ItemIOHatchInputHandler(
    private val blockEntity: ItemIOHatchBlockEntity
) : IItemHandler {

    override fun getSlots(): Int {
        return blockEntity.inputStorage.maxTypes
    }

    override fun getStackInSlot(slot: Int): ItemStack {
        val storage = blockEntity.inputStorage
        val keys = storage.getAllResources().toList()
        if (slot >= keys.size) {
            return ItemStack.EMPTY
        }
        val key = keys[slot]
        val pmKey = key as? PMItemKey ?: return ItemStack.EMPTY
        val count = storage.getAmount(key)
        return pmKey.uniqueKey.createStack(count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }

    override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack {
        if (stack.isEmpty) return ItemStack.EMPTY

        val storage = blockEntity.inputStorage
        val inserted = storage.insertStack(stack, simulate)
        if (inserted <= 0) return stack

        val remaining = stack.copy()
        remaining.shrink(inserted.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        return if (remaining.isEmpty) ItemStack.EMPTY else remaining
    }

    override fun extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack {
        // Input handler does not allow extraction from external
        return ItemStack.EMPTY
    }

    override fun getSlotLimit(slot: Int): Int {
        return blockEntity.config.inputMaxStackSize.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

}
