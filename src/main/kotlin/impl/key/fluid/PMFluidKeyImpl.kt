package github.kasuminova.prototypemachinery.impl.key.fluid

import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.api.key.PMKeyType
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.fluids.FluidStack

/**
 * # FluidStackKey Implementation
 * # 流体栈键实现
 *
 * Implementation of [PMKey] for FluidStacks.
 *
 * FluidStack 的 [PMKey] 实现。
 */
@Suppress("EqualsOrHashCode")
public class PMFluidKeyImpl(
    public override val uniqueKey: UniquePMFluidKey,
    public override var count: Long
) : PMKey<FluidStack>(), PMFluidKey {

    public override val type: PMKeyType
        get() = PMFluidKeyType

    // Hash code is derived ONLY from the unique key (prototype).
    // This means keys with different counts have the same hash code.
    // 哈希码仅源自唯一键（原型）。
    // 这意味着具有不同数量的键具有相同的哈希码。
    override val internalHashCode: Int = uniqueKey.hashCode()

    public override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PMFluidKey) return false
        // Equality is based on the unique key reference.
        // This ignores count!
        // 相等性基于唯一键引用。
        // 这忽略了数量！
        return this.uniqueKey === other.uniqueKey
    }

    public override fun copy(): PMKey<FluidStack> {
        return PMFluidKeyImpl(uniqueKey, count)
    }

    public override fun writeNBT(nbt: NBTTagCompound): NBTTagCompound {
        // Write the fluid data
        val stack = uniqueKey.createStack(1)
        stack.writeToNBT(nbt)
        nbt.setLong("PMCount", count)
        return nbt
    }

    public override fun get(): FluidStack {
        // Clamp count to Int max for vanilla FluidStack
        val stackCount = if (count > Int.MAX_VALUE) Int.MAX_VALUE else count.toInt()
        return uniqueKey.createStack(stackCount)
    }

    public override fun asPMKey(): PMKey<FluidStack> = this

    public override fun toString(): String = "${count}mb x ${uniqueKey.fluid.name}"

}
