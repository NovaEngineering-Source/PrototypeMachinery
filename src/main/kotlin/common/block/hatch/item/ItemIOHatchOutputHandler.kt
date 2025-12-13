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
        return blockEntity.outputStorage.maxTypes
    }

    override fun getStackInSlot(slot: Int): ItemStack {
        val storage = blockEntity.outputStorage
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
        // Output handler does not allow insertion from external
        return stack
    }

    override fun extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack {
        val storage = blockEntity.outputStorage
        val keys = storage.getAllResources().toList()
        if (slot >= keys.size) {
            return ItemStack.EMPTY
        }
        val key = keys[slot]
        val pmKey = key as? PMItemKey ?: return ItemStack.EMPTY
        val extracted = storage.extract(key, amount.toLong(), simulate)
        if (extracted <= 0) {
            return ItemStack.EMPTY
        }
        return pmKey.uniqueKey.createStack(extracted.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }

    override fun getSlotLimit(slot: Int): Int {
        return blockEntity.config.outputMaxStackSize.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

}
