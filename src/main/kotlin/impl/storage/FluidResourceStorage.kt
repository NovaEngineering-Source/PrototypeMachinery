package github.kasuminova.prototypemachinery.impl.storage

import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.impl.key.fluid.PMFluidKey
import github.kasuminova.prototypemachinery.impl.key.fluid.PMFluidKeyImpl
import github.kasuminova.prototypemachinery.impl.key.fluid.PMFluidKeyType
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.fluids.FluidStack

/**
 * # FluidResourceStorage - Fluid-specific Resource Storage
 * # FluidResourceStorage - 流体特定资源存储
 *
 * Specialized implementation of [ResourceStorageImpl] for FluidStacks.
 * Uses [github.kasuminova.prototypemachinery.impl.key.fluid.UniquePMFluidKey] for efficient lookup.
 *
 * [ResourceStorageImpl] 的流体栈专用实现。
 * 使用 [github.kasuminova.prototypemachinery.impl.key.fluid.UniquePMFluidKey] 进行高效查找。
 *
 * @param maxTypes Maximum number of different fluid types (usually 1 for fluid hatches)
 * @param maxCountPerType Maximum count per fluid type (in mB)
 */
public class FluidResourceStorage(
    maxTypes: Int = 1,
    maxCountPerType: Long = Long.MAX_VALUE
) : ResourceStorageImpl<PMKey<FluidStack>>(maxTypes, maxCountPerType) {

    override fun getUniqueKey(key: PMKey<FluidStack>): Any {
        return (key as PMFluidKey).uniqueKey
    }

    /**
     * Convenience method to insert a FluidStack.
     *
     * 便捷方法，用于插入 FluidStack。
     *
     * @param stack The fluid stack to insert
     * @param simulate If true, only simulates the insertion
     * @return The amount that was actually inserted (in mB)
     */
    public fun insertFluid(stack: FluidStack?, simulate: Boolean): Long {
        if (stack == null || stack.amount <= 0) return 0L
        val key = PMFluidKeyType.create(stack)
        return insert(key, stack.amount.toLong(), simulate)
    }

    /**
     * Convenience method to extract a FluidStack.
     *
     * 便捷方法，用于提取 FluidStack。
     *
     * @param stack The fluid stack to match
     * @param simulate If true, only simulates the extraction
     * @return The amount that was actually extracted (in mB)
     */
    public fun extractFluid(stack: FluidStack?, simulate: Boolean): Long {
        if (stack == null || stack.amount <= 0) return 0L
        val key = PMFluidKeyType.create(stack)
        return extract(key, stack.amount.toLong(), simulate)
    }

    /**
     * Extracts a specific amount of fluid matching the stack.
     * Returns the extracted FluidStack.
     *
     * 提取与栈匹配的特定数量的流体。
     * 返回提取的 FluidStack。
     */
    public fun extractFluidResult(stack: FluidStack?, maxAmount: Int, simulate: Boolean): FluidStack? {
        if (stack == null || stack.amount <= 0) return null
        val key = PMFluidKeyType.create(stack)
        val extracted = extract(key, maxAmount.toLong(), simulate)
        if (extracted <= 0) return null
        val result = stack.copy()
        result.amount = extracted.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return result
    }

    /**
     * Drains fluid without requiring an exact match.
     * Used for IFluidHandler compatibility.
     *
     * 在不需要精确匹配的情况下排出流体。
     * 用于 IFluidHandler 兼容性。
     *
     * @param maxDrain Maximum amount to drain (in mB)
     * @param simulate If true, only simulates the drain
     * @return The drained FluidStack, or null if nothing was drained
     */
    public fun drain(maxDrain: Int, simulate: Boolean): FluidStack? {
        if (maxDrain <= 0 || storage.isEmpty()) return null

        // Get the first available fluid
        val firstKey = storage.values.firstOrNull() ?: return null
        val extracted = extract(firstKey, maxDrain.toLong(), simulate)
        if (extracted <= 0) return null

        val result = firstKey.get().copy()
        result.amount = extracted.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return result
    }

    /**
     * Gets the first stored fluid, or null if empty.
     *
     * 获取第一个存储的流体，如果为空则返回 null。
     */
    public fun getFluid(): FluidStack? {
        val firstKey = storage.values.firstOrNull() ?: return null
        return firstKey.get()
    }

    /**
     * Gets the total amount of fluid stored.
     *
     * 获取存储的流体总量。
     */
    public fun getTotalAmount(): Long {
        return storage.values.sumOf { it.count }
    }

    /**
     * Writes this storage to NBT.
     *
     * 将此存储写入 NBT。
     */
    public fun writeNBT(nbt: NBTTagCompound): NBTTagCompound {
        return writeNBT(nbt) { key, keyNbt ->
            key.writeNBT(keyNbt)
        }
    }

    /**
     * Reads this storage from NBT.
     *
     * 从 NBT 读取此存储。
     */
    public fun readNBT(nbt: NBTTagCompound) {
        readNBT(nbt) { keyNbt ->
            PMFluidKeyType.readNBT(keyNbt) as? PMFluidKeyImpl
        }
    }

}
