package github.kasuminova.prototypemachinery.common.block.hatch.fluid

import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidHandler
import net.minecraftforge.fluids.capability.IFluidTankProperties

/**
 * # FluidIOHatchCombinedHandler - IO Hatch Combined Fluid Handler
 * # FluidIOHatchCombinedHandler - 交互仓组合流体处理器
 *
 * IFluidHandler wrapper that combines both input and output storage.
 * Fill goes to input storage, drain comes from output storage.
 *
 * 组合输入和输出存储的 IFluidHandler 包装器。
 * 填充进入输入存储，排出来自输出存储。
 */
public class FluidIOHatchCombinedHandler(
    private val blockEntity: FluidIOHatchBlockEntity
) : IFluidHandler {

    override fun getTankProperties(): Array<IFluidTankProperties> {
        val inputProps = Array(blockEntity.inputStorage.maxTypes) { index ->
            InputTankProperty(blockEntity, index)
        }
        val outputProps = Array(blockEntity.outputStorage.maxTypes) { index ->
            OutputTankProperty(blockEntity, index)
        }
        return arrayOf(*inputProps, *outputProps)
    }

    override fun fill(resource: FluidStack?, doFill: Boolean): Int {
        if (resource == null || resource.amount <= 0) return 0
        // Always fill to input storage
        val inserted = blockEntity.inputStorage.insertFluid(resource, !doFill)
        return inserted.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    override fun drain(resource: FluidStack?, doDrain: Boolean): FluidStack? {
        if (resource == null || resource.amount <= 0) return null
        // Always drain from output storage
        return blockEntity.outputStorage.extractFluidResult(resource, resource.amount, !doDrain)
    }

    override fun drain(maxDrain: Int, doDrain: Boolean): FluidStack? {
        if (maxDrain <= 0) return null
        // Always drain from output storage
        return blockEntity.outputStorage.drain(maxDrain, !doDrain)
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
