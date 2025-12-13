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
        val resources = storage.getAllResources().toList()
        if (slot !in resources.indices) return ItemStack.EMPTY
        return resources[slot].get()
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

        val resources = storage.getAllResources().toList()
        if (slot !in resources.indices) return ItemStack.EMPTY

        val resource = resources[slot]
        return storage.extractStackResult(resource.get(), amount, simulate)
    }

    override fun getSlotLimit(slot: Int): Int {
        // Return Int.MAX_VALUE since we support large stacks internally
        return Int.MAX_VALUE
    }

    override fun isItemValid(slot: Int, stack: ItemStack): Boolean {
        if (config.hatchType == HatchType.OUTPUT) {
            return false // Output hatches cannot accept items
        }
        return !stack.isEmpty
    }

}
