package github.kasuminova.prototypemachinery.common.block.hatch.fluid

import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidHandler
import net.minecraftforge.fluids.capability.IFluidTankProperties

/**
 * # FluidIOHatchOutputHandler - IO Hatch Output Fluid Handler
 * # FluidIOHatchOutputHandler - 交互仓输出流体处理器
 *
 * IFluidHandler wrapper for the output storage of a FluidIOHatchBlockEntity.
 * External access can only drain fluids, not fill.
 *
 * FluidIOHatchBlockEntity 输出存储的 IFluidHandler 包装器。
 * 外部访问只能排出流体，不能填充。
 */
public class FluidIOHatchOutputHandler(
    private val blockEntity: FluidIOHatchBlockEntity
) : IFluidHandler {

    override fun getTankProperties(): Array<IFluidTankProperties> {
        return Array(blockEntity.outputStorage.maxTypes) { index ->
            OutputTankProperty(blockEntity, index)
        }
    }

    override fun fill(resource: FluidStack?, doFill: Boolean): Int {
        // Output handler does not allow fill from external
        return 0
    }

    override fun drain(resource: FluidStack?, doDrain: Boolean): FluidStack? {
        if (resource == null || resource.amount <= 0) return null
        val limit = blockEntity.maxOutputRate
        val amount = if (limit <= 0L) resource.amount else resource.amount.coerceAtMost(limit.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        if (amount <= 0) return null
        val stack = if (amount == resource.amount) resource else resource.copy().apply { this.amount = amount }
        return blockEntity.outputStorage.extractFluidResult(stack, stack.amount, !doDrain)
    }

    override fun drain(maxDrain: Int, doDrain: Boolean): FluidStack? {
        if (maxDrain <= 0) return null
        val limit = blockEntity.maxOutputRate
        val amount = if (limit <= 0L) maxDrain else maxDrain.coerceAtMost(limit.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        if (amount <= 0) return null
        return blockEntity.outputStorage.drain(amount, !doDrain)
    }

    private class OutputTankProperty(
        private val blockEntity: FluidIOHatchBlockEntity,
        private val index: Int
    ) : IFluidTankProperties {

        override fun getContents(): FluidStack? {
            val storage = blockEntity.outputStorage
            val keys = storage.getAllResources().toList()
            if (index >= keys.size) return null
            val key = keys[index]
            val count = storage.getAmount(key)
            val stack = key.get().copy()
            stack.amount = count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            return stack
        }

        override fun getCapacity(): Int {
            return blockEntity.config.outputTankCapacity.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }

        override fun canFill(): Boolean = false
        override fun canDrain(): Boolean = true
        override fun canFillFluidType(fluidStack: FluidStack?): Boolean = false
        override fun canDrainFluidType(fluidStack: FluidStack?): Boolean = fluidStack != null
    }

}
