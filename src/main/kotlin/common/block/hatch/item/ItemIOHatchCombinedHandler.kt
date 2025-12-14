package github.kasuminova.prototypemachinery.common.block.hatch.item

import github.kasuminova.prototypemachinery.impl.key.item.PMItemKey
import net.minecraft.item.ItemStack
import net.minecraftforge.items.IItemHandler

/**
 * # ItemIOHatchCombinedHandler - IO Hatch Combined Handler
 * # ItemIOHatchCombinedHandler - 交互仓组合处理器
 *
 * IItemHandler wrapper that combines both input and output storage.
 * Insertion goes to input storage, extraction comes from output storage.
 *
 * 组合输入和输出存储的 IItemHandler 包装器。
 * 插入进入输入存储，提取来自输出存储。
 */
public class ItemIOHatchCombinedHandler(
    private val blockEntity: ItemIOHatchBlockEntity
) : IItemHandler {

    private val inputSlots: Int
        get() = blockEntity.inputStorage.slotCount

    private val outputSlots: Int
        get() = blockEntity.outputStorage.slotCount

    override fun getSlots(): Int {
        return inputSlots + outputSlots
    }

    override fun getStackInSlot(slot: Int): ItemStack {
        return if (slot < inputSlots) {
            getStackFromInput(slot)
        } else {
            getStackFromOutput(slot - inputSlots)
        }
    }

    private fun getStackFromInput(slot: Int): ItemStack {
        val storage = blockEntity.inputStorage
        val key = storage.getSlot(slot) ?: return ItemStack.EMPTY
        return key.get()
    }

    private fun getStackFromOutput(slot: Int): ItemStack {
        val storage = blockEntity.outputStorage
        val key = storage.getSlot(slot) ?: return ItemStack.EMPTY
        return key.get()
    }

    override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack {
        if (stack.isEmpty) return ItemStack.EMPTY

        // Always insert to input storage regardless of slot
        val storage = blockEntity.inputStorage
        val inserted = storage.insertStack(stack, simulate)
        if (inserted <= 0) return stack

        val remaining = stack.copy()
        remaining.shrink(inserted.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        return if (remaining.isEmpty) ItemStack.EMPTY else remaining
    }

    override fun extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack {
        // Only allow extraction from output slots
        if (slot < inputSlots) {
            return ItemStack.EMPTY
        }

        val outputSlot = slot - inputSlots
        val storage = blockEntity.outputStorage
        val key = storage.getSlot(outputSlot) ?: return ItemStack.EMPTY
        val template = (key as? PMItemKey)?.uniqueKey?.createStack(1) ?: key.get().copy().apply { count = 1 }

        val extracted = storage.extractFromSlot(outputSlot, amount.toLong(), simulate)
        if (extracted <= 0L) return ItemStack.EMPTY

        val result = template.copy()
        result.count = extracted.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return result
    }

    override fun getSlotLimit(slot: Int): Int {
        return if (slot < inputSlots) {
            blockEntity.config.inputMaxStackSize.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        } else {
            blockEntity.config.outputMaxStackSize.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
    }

}
