package github.kasuminova.prototypemachinery.common.block.hatch.fluid

import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidHandler
import net.minecraftforge.fluids.capability.IFluidTankProperties

/**
 * # FluidIOHatchInputHandler - IO Hatch Input Fluid Handler
 * # FluidIOHatchInputHandler - 交互仓输入流体处理器
 *
 * IFluidHandler wrapper for the input storage of a FluidIOHatchBlockEntity.
 * External access can only fill fluids, not drain.
 *
 * FluidIOHatchBlockEntity 输入存储的 IFluidHandler 包装器。
 * 外部访问只能填充流体，不能排出。
 */
public class FluidIOHatchInputHandler(
    private val blockEntity: FluidIOHatchBlockEntity
) : IFluidHandler {

    override fun getTankProperties(): Array<IFluidTankProperties> {
        return Array(blockEntity.inputStorage.maxTypes) { index ->
            InputTankProperty(blockEntity, index)
        }
    }

    override fun fill(resource: FluidStack?, doFill: Boolean): Int {
        if (resource == null || resource.amount <= 0) return 0
        val limit = blockEntity.maxInputRate
        val amount = if (limit <= 0L) resource.amount else resource.amount.coerceAtMost(limit.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        if (amount <= 0) return 0
        val stack = if (amount == resource.amount) resource else resource.copy().apply { this.amount = amount }
        val inserted = blockEntity.inputStorage.insertFluid(stack, !doFill)
        return inserted.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    override fun drain(resource: FluidStack?, doDrain: Boolean): FluidStack? {
        // Input handler does not allow drain from external
        return null
    }

    override fun drain(maxDrain: Int, doDrain: Boolean): FluidStack? {
        // Input handler does not allow drain from external
        return null
    }

    private class InputTankProperty(
        private val blockEntity: FluidIOHatchBlockEntity,
        private val index: Int
    ) : IFluidTankProperties {

        override fun getContents(): FluidStack? {
            val storage = blockEntity.inputStorage
            val keys = storage.getAllResources().toList()
            if (index >= keys.size) return null
            val key = keys[index]
            val count = storage.getAmount(key)
            val stack = key.get().copy()
            stack.amount = count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            return stack
        }

        override fun getCapacity(): Int {
            return blockEntity.config.inputTankCapacity.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }

        override fun canFill(): Boolean = true
        override fun canDrain(): Boolean = false
        override fun canFillFluidType(fluidStack: FluidStack?): Boolean = fluidStack != null
        override fun canDrainFluidType(fluidStack: FluidStack?): Boolean = false
    }

}
