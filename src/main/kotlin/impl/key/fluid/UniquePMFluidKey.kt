package github.kasuminova.prototypemachinery.impl.key.fluid

import net.minecraftforge.fluids.Fluid
import net.minecraftforge.fluids.FluidStack

/**
 * # UniqueFluidStackKey
 * # 唯一流体栈键
 *
 * A runtime-unique representation of a FluidStack's definition.
 * Instances of this class are interned by [PMFluidKeyType].
 *
 * FluidStack 定义的运行时唯一表示。
 * 此类的实例由 [PMFluidKeyType] 驻留。
 */
public class UniquePMFluidKey(private val stack: FluidStack) {

    public val fluid: Fluid get() = stack.fluid

    private val hash: Int

    init {
        val fluid = this.stack.fluid
        val nbt = this.stack.tag

        val combinedHashCode = System.identityHashCode(fluid)

        val tagHashCode = nbt?.hashCode() ?: 0
        this.hash = if (tagHashCode != 0) combinedHashCode xor tagHashCode else combinedHashCode
    }

    /**
     * Gets the FluidStack represented by this key, **UNSAFE**, use at your own risk.
     * 获取该键所表示的 FluidStack，**不安全**，请自行承担风险。
     */
    public fun getFluidStackUnsafe(): FluidStack = stack

    public fun createStack(amount: Int): FluidStack {
        val newStack = stack.copy()
        newStack.amount = amount
        return newStack
    }

    /**
     * Creates a deep copy of this key.
     * Useful for creating a safe, immutable key from a temporary one that holds mutable NBT references.
     * 创建此键的深拷贝。
     * 对于从持有可变 NBT 引用的临时键创建安全、不可变的键非常有用。
     */
    public fun copy(): UniquePMFluidKey = UniquePMFluidKey(stack.copy())

    public override fun hashCode(): Int = hash

    public override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UniquePMFluidKey) return false
        return this.stack.isFluidEqual(other.stack)
    }
}
