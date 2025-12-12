package github.kasuminova.prototypemachinery.api.key

import net.minecraft.nbt.NBTTagCompound

/**
 * # PMKey - Unified Resource Key
 * # PMKey - 统一资源键
 *
 * A unified key representation for various resources (Items, Fluids, etc.) in PrototypeMachinery.
 * Designed for high performance, memory efficiency, and type safety.
 *
 * PrototypeMachinery 中各种资源（物品、流体等）的统一键表示。
 * 专为高性能、内存效率和类型安全而设计。
 *
 * @param T The type of the underlying resource (e.g., ItemStack, FluidStack).
 * @param T 底层资源的类型（例如 ItemStack, FluidStack）。
 */
@Suppress("EqualsOrHashCode")
public abstract class PMKey<T> {

    public abstract val type: PMKeyType
    public abstract var count: Long

    /**
     * Pre-calculated hash code.
     * Must be derived from the underlying unique prototype, ignoring the count.
     *
     * 预计算的哈希码。
     * 必须源自底层唯一原型，忽略数量。
     */
    protected abstract val internalHashCode: Int

    public override fun hashCode(): Int {
        return internalHashCode
    }

    /**
     * Checks equality based on the underlying unique prototype.
     * This MUST ignore [count].
     * Ideally, this should use reference equality (===) on the interned prototype.
     *
     * 基于底层唯一原型检查相等性。
     * 必须忽略 [count]。
     * 理想情况下，这应该对驻留的原型使用引用相等性 (===)。
     */
    public abstract override fun equals(other: Any?): Boolean

    public abstract fun copy(): PMKey<T>

    public abstract fun writeNBT(nbt: NBTTagCompound): NBTTagCompound

    /**
     * Gets the underlying resource value (e.g., a new ItemStack with the correct count).
     *
     * 获取底层资源值（例如，具有正确数量的新 ItemStack）。
     */
    public abstract fun get(): T
}
