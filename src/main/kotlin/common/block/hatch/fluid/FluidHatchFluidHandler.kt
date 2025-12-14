package github.kasuminova.prototypemachinery.common.block.hatch.fluid

import github.kasuminova.prototypemachinery.common.block.hatch.HatchType
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidHandler
import net.minecraftforge.fluids.capability.IFluidTankProperties

/**
 * # FluidHatchFluidHandler - Fluid Hatch Fluid Handler
 * # FluidHatchFluidHandler - 流体仓流体处理器
 *
 * IFluidHandler wrapper for FluidHatchBlockEntity's storage.
 *
 * FluidHatchBlockEntity 存储的 IFluidHandler 包装器。
 */
public class FluidHatchFluidHandler(
    private val blockEntity: FluidHatchBlockEntity
) : IFluidHandler {

    override fun getTankProperties(): Array<IFluidTankProperties> {
        // Build dynamically so runtime config updates (storage replacement) are reflected.
        return Array(blockEntity.storage.maxTypes) { index ->
            FluidTankProperty(blockEntity, index)
        }
    }

    override fun fill(resource: FluidStack?, doFill: Boolean): Int {
        if (resource == null || resource.amount <= 0) return 0

        // Check if this hatch allows insertion
        if (blockEntity.config.hatchType == HatchType.OUTPUT) {
            return 0
        }

        val limit = blockEntity.maxInputRate
        val toInsert = if (limit > 0L) {
            resource.copy().apply { amount = minOf(amount.toLong(), limit).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
        } else {
            resource
        }

        val inserted = blockEntity.storage.insertFluid(toInsert, !doFill)
        return inserted.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    override fun drain(resource: FluidStack?, doDrain: Boolean): FluidStack? {
        if (resource == null || resource.amount <= 0) return null

        // Check if this hatch allows extraction
        if (blockEntity.config.hatchType == HatchType.INPUT) {
            return null
        }

        val limit = blockEntity.maxOutputRate
        val maxDrain = if (limit > 0L) minOf(resource.amount.toLong(), limit).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else resource.amount
        val req = resource.copy().apply { amount = maxDrain }
        return blockEntity.storage.extractFluidResult(req, req.amount, !doDrain)
    }

    override fun drain(maxDrain: Int, doDrain: Boolean): FluidStack? {
        if (maxDrain <= 0) return null

        // Check if this hatch allows extraction
        if (blockEntity.config.hatchType == HatchType.INPUT) {
            return null
        }

        val limit = blockEntity.maxOutputRate
        val capped = if (limit > 0L) minOf(maxDrain.toLong(), limit).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else maxDrain
        return blockEntity.storage.drain(capped, !doDrain)
    }

    /**
     * Internal tank property implementation.
     */
    private class FluidTankProperty(
        private val blockEntity: FluidHatchBlockEntity,
        private val index: Int
    ) : IFluidTankProperties {

        override fun getContents(): FluidStack? {
            val storage = blockEntity.storage
            val keys = storage.getAllResources().toList()
            if (index >= keys.size) return null
            val key = keys[index]
            val count = storage.getAmount(key)
            val stack = key.get().copy()
            stack.amount = count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            return stack
        }

        override fun getCapacity(): Int {
            return blockEntity.config.tankCapacity.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }

        override fun canFill(): Boolean {
            return blockEntity.config.hatchType != HatchType.OUTPUT
        }

        override fun canDrain(): Boolean {
            return blockEntity.config.hatchType != HatchType.INPUT
        }

        override fun canFillFluidType(fluidStack: FluidStack?): Boolean {
            return canFill() && fluidStack != null
        }

        override fun canDrainFluidType(fluidStack: FluidStack?): Boolean {
            return canDrain() && fluidStack != null
        }

    }

}
