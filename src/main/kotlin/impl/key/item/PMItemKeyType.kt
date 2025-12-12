package github.kasuminova.prototypemachinery.impl.key.item

import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.api.key.PMKeyType
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * # ItemStackKeyType
 * # 物品栈键类型
 *
 * The [PMKeyType] implementation for ItemStacks.
 * Manages the interning of [UniquePMItemKey]s.
 *
 * ItemStacks 的 [PMKeyType] 实现。
 * 管理 [UniquePMItemKey] 的驻留。
 */
public object PMItemKeyType : PMKeyType {

    public override val name: String = "item_stack"

    public override fun readNBT(nbt: NBTTagCompound): PMKey<ItemStack>? {
        val stack = ItemStack(nbt)
        if (stack.isEmpty) return null
        
        val unique = getUniqueKey(stack)
        val count = if (nbt.hasKey("PMCount")) nbt.getLong("PMCount") else stack.count.toLong()
        
        return PMItemKeyImpl(unique, count)
    }

    // A cache to intern UniqueItemStackKeys.
    // We use a WeakHashMap where the Key is the UniqueItemStackKey itself.
    // Since UniqueItemStackKey implements equals/hashCode based on content,
    // we can use a temporary instance to look up the canonical instance.
    // However, WeakHashMap keys are weak. If the canonical instance is only held by the map, it will be collected.
    // But we want it to be collected if no PMKey is using it.
    // So WeakHashMap<UniqueItemStackKey, WeakReference<UniqueItemStackKey>> is redundant but standard for interning?
    // Actually, usually Interners use a custom map.
    // Let's use a simple synchronized map with WeakReferences for values, and clean up occasionally?
    // Or just use a WeakHashMap where the Key IS the value we want to keep alive?
    // No, if the Key is weak, and we don't hold a strong ref elsewhere, it goes away.
    // That's exactly what we want.
    // But we need to return the *Key* that is in the map.

    // Implementation:
    // Map<UniqueItemStackKey, WeakReference<UniqueItemStackKey>>
    // When looking up:
    // 1. Create temp key.
    // 2. Get from map.
    // 3. If exists and not null, return it.
    // 4. If not, put temp -> WeakRef(temp) and return temp.
    
    // 用于驻留 UniqueItemStackKeys 的缓存。
    // 我们使用 WeakHashMap，其中 Key 是 UniqueItemStackKey 本身。
    // 由于 UniqueItemStackKey 基于内容实现 equals/hashCode，
    // 我们可以使用临时实例来查找规范实例。

    private val registry = WeakHashMap<UniquePMItemKey, WeakReference<UniquePMItemKey>>()
    private val lock = ReentrantReadWriteLock()

    public fun getUniqueKey(stack: ItemStack): UniquePMItemKey {
        if (stack.isEmpty) throw IllegalArgumentException("Cannot create key for empty stack")

        // Normalize meta: if item is damageable, use damage; if subtypes, use metadata; else 0.
        // This avoids creating different keys for items that don't distinguish these states.
        // 归一化 meta：如果物品可损坏，使用 damage；如果有子类型，使用 metadata；否则为 0。
        // 这避免了为不区分这些状态的物品创建不同的键。
        val meta = when {
            stack.isItemStackDamageable -> stack.itemDamage
            stack.hasSubtypes -> stack.metadata
            else -> 0
        }

        val originalCount = stack.count
        val originalDamage = stack.itemDamage

        stack.count = 1
        if (stack.itemDamage != meta) {
            stack.itemDamage = meta
        }

        val temp = UniquePMItemKey(stack)

        lock.read {
            val existingRef = registry[temp]
            val existing = existingRef?.get()
            if (existing != null) {
                // Restore stack state
                stack.count = originalCount
                stack.itemDamage = originalDamage
                return existing
            }
        }

        lock.write {
            // Double check
            val existingRef = registry[temp]
            val existing = existingRef?.get()
            if (existing != null) {
                // Restore stack state
                stack.count = originalCount
                stack.itemDamage = originalDamage
                return existing
            }

            // If we are here, we need to intern a new key.
            // IMPORTANT: 'temp' holds a reference to the stack's NBT, which is mutable.
            // We must create a safe copy if NBT exists to ensure the key in the registry is immutable.
            // Also, 'temp' holds a reference to 'stack' which we are about to restore.
            // So we MUST copy 'temp' (which deep copies the stack) BEFORE restoring 'stack'.
            val safeKey = temp.copy()

            // Restore stack state
            stack.count = originalCount
            stack.itemDamage = originalDamage

            registry[safeKey] = WeakReference(safeKey)
            return safeKey
        }
    }

    public fun create(stack: ItemStack): PMKey<ItemStack> {
        val unique = getUniqueKey(stack)
        return PMItemKeyImpl(unique, stack.count.toLong())
    }
}
