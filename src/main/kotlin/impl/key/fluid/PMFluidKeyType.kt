package github.kasuminova.prototypemachinery.impl.key.fluid

import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.api.key.PMKeyType
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.fluids.FluidStack
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * # FluidStackKeyType
 * # 流体栈键类型
 *
 * The [PMKeyType] implementation for FluidStacks.
 * Manages the interning of [UniquePMFluidKey]s.
 *
 * FluidStacks 的 [PMKeyType] 实现。
 * 管理 [UniquePMFluidKey] 的驻留。
 */
public object PMFluidKeyType : PMKeyType {

    public override val name: String = "fluid_stack"

    public override fun readNBT(nbt: NBTTagCompound): PMKey<FluidStack>? {
        val stack = FluidStack.loadFluidStackFromNBT(nbt) ?: return null

        val unique = getUniqueKey(stack)
        val count = if (nbt.hasKey("PMCount")) nbt.getLong("PMCount") else stack.amount.toLong()

        return PMFluidKeyImpl(unique, count)
    }

    private val registry = WeakHashMap<UniquePMFluidKey, WeakReference<UniquePMFluidKey>>()
    private val lock = ReentrantReadWriteLock()

    public fun getUniqueKey(stack: FluidStack): UniquePMFluidKey {
        val originalAmount = stack.amount

        stack.amount = 1

        val temp = UniquePMFluidKey(stack)

        lock.read {
            val existingRef = registry[temp]
            val existing = existingRef?.get()
            if (existing != null) {
                // Restore stack state
                stack.amount = originalAmount
                return existing
            }
        }

        lock.write {
            // Double check
            val existingRef = registry[temp]
            val existing = existingRef?.get()
            if (existing != null) {
                // Restore stack state
                stack.amount = originalAmount
                return existing
            }

            // If we are here, we need to intern a new key.
            // IMPORTANT: 'temp' holds a reference to the stack's NBT, which is mutable.
            // We must create a safe copy if NBT exists to ensure the key in the registry is immutable.
            // Also, 'temp' holds a reference to 'stack' which we are about to restore.
            // So we MUST copy 'temp' (which deep copies the stack) BEFORE restoring 'stack'.
            val safeKey = temp.copy()

            // Restore stack state
            stack.amount = originalAmount

            registry[safeKey] = WeakReference(safeKey)
            return safeKey
        }
    }

    public fun create(stack: FluidStack): PMKey<FluidStack> {
        val unique = getUniqueKey(stack)
        return PMFluidKeyImpl(unique, stack.amount.toLong())
    }
}
