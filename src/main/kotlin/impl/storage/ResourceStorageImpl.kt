package github.kasuminova.prototypemachinery.impl.storage

import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.api.storage.ChangeType
import github.kasuminova.prototypemachinery.api.storage.ObservableResourceStorage
import github.kasuminova.prototypemachinery.api.storage.ResourceChange
import github.kasuminova.prototypemachinery.api.storage.ResourceStorageListener
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import net.minecraftforge.common.util.Constants
import java.util.concurrent.CopyOnWriteArrayList

/**
 * # ResourceStorageImpl - Default Resource Storage Implementation
 * # ResourceStorageImpl - 默认资源存储实现
 *
 * A hash-based implementation of [ObservableResourceStorage].
 * Uses PMKey's interned unique keys for O(1) lookup performance.
 *
 * 基于哈希的 [ObservableResourceStorage] 实现。
 * 使用 PMKey 的驻留唯一键实现 O(1) 查找性能。
 *
 * @param K The specific PMKey type this storage holds
 * @param maxTypes Maximum number of different resource types
 * @param maxCountPerType Maximum count per resource type
 */
public open class ResourceStorageImpl<K : PMKey<*>>(
    override val maxTypes: Int,
    override val maxCountPerType: Long = Long.MAX_VALUE
) : ObservableResourceStorage<K> {

    // Internal storage using a HashMap for O(1) lookup
    // Key: The unique interned key reference
    // Value: The PMKey with its current count
    protected val storage: MutableMap<Any, K> = LinkedHashMap()

    // Listeners for change notifications
    private val listeners: MutableList<ResourceStorageListener<K>> = CopyOnWriteArrayList()

    // Pending changes flag for sync optimization
    @Volatile
    private var pendingChanges: Boolean = false

    override val usedTypes: Int
        get() = storage.size

    override fun getAmount(key: K): Long {
        val uniqueKey = getUniqueKey(key)
        return storage[uniqueKey]?.count ?: 0L
    }

    override fun contains(key: K): Boolean {
        val uniqueKey = getUniqueKey(key)
        return storage.containsKey(uniqueKey)
    }

    override fun insert(key: K, amount: Long, simulate: Boolean): Long {
        if (amount <= 0) return 0L

        val uniqueKey = getUniqueKey(key)
        val existing = storage[uniqueKey]

        val insertedAmount: Long
        val previousAmount: Long
        val newAmount: Long

        if (existing != null) {
            // Resource exists, add to it
            previousAmount = existing.count
            val remainingSpace = maxCountPerType - previousAmount
            insertedAmount = minOf(amount, remainingSpace)
            newAmount = previousAmount + insertedAmount
        } else {
            // New resource type
            if (storage.size >= maxTypes) {
                // Storage is full, cannot add new types
                return 0L
            }
            previousAmount = 0L
            insertedAmount = minOf(amount, maxCountPerType)
            newAmount = insertedAmount
        }

        if (insertedAmount <= 0) return 0L

        if (!simulate) {
            if (existing != null) {
                existing.count = newAmount
            } else {
                @Suppress("UNCHECKED_CAST")
                val newKey = key.copy() as K
                newKey.count = newAmount
                storage[uniqueKey] = newKey
            }
            notifyChange(
                ResourceChange(
                    key = getStoredKey(uniqueKey) ?: key,
                    previousAmount = previousAmount,
                    newAmount = newAmount,
                    type = ChangeType.INSERTED
                )
            )
            pendingChanges = true
        }

        return insertedAmount
    }

    override fun extract(key: K, amount: Long, simulate: Boolean): Long {
        if (amount <= 0) return 0L

        val uniqueKey = getUniqueKey(key)
        val existing = storage[uniqueKey] ?: return 0L

        val previousAmount = existing.count
        val extractedAmount = minOf(amount, previousAmount)
        val newAmount = previousAmount - extractedAmount

        if (extractedAmount <= 0) return 0L

        if (!simulate) {
            if (newAmount <= 0) {
                storage.remove(uniqueKey)
                notifyChange(
                    ResourceChange(
                        key = existing,
                        previousAmount = previousAmount,
                        newAmount = 0L,
                        type = ChangeType.REMOVED
                    )
                )
            } else {
                existing.count = newAmount
                notifyChange(
                    ResourceChange(
                        key = existing,
                        previousAmount = previousAmount,
                        newAmount = newAmount,
                        type = ChangeType.EXTRACTED
                    )
                )
            }
            pendingChanges = true
        }

        return extractedAmount
    }

    override fun getAllResources(): Collection<K> {
        return storage.values.toList()
    }

    override fun clear() {
        if (storage.isEmpty()) return

        val previousResources = storage.values.toList()
        storage.clear()

        // Notify for each cleared resource
        previousResources.forEach { key ->
            notifyChange(
                ResourceChange(
                    key = key,
                    previousAmount = key.count,
                    newAmount = 0L,
                    type = ChangeType.CLEARED
                )
            )
        }
        pendingChanges = true
    }

    override fun addListener(listener: ResourceStorageListener<K>) {
        listeners.add(listener)
    }

    override fun removeListener(listener: ResourceStorageListener<K>): Boolean {
        return listeners.remove(listener)
    }

    override fun hasPendingChanges(): Boolean = pendingChanges

    override fun clearPendingChanges() {
        pendingChanges = false
    }

    /**
     * Gets the unique key identifier for a PMKey.
     * Subclasses should override this to provide type-specific key extraction.
     *
     * 获取 PMKey 的唯一键标识符。
     * 子类应覆盖此方法以提供类型特定的键提取。
     */
    protected open fun getUniqueKey(key: K): Any {
        // Default implementation uses the key's hashCode as identifier
        // This works because PMKey's equals/hashCode is based on the unique prototype
        return key
    }

    /**
     * Gets the stored key instance by its unique key.
     *
     * 通过唯一键获取存储的键实例。
     */
    protected fun getStoredKey(uniqueKey: Any): K? {
        return storage[uniqueKey]
    }

    /**
     * Notifies all listeners of a change.
     *
     * 通知所有监听器发生了变更。
     */
    protected fun notifyChange(change: ResourceChange<K>) {
        listeners.forEach { listener ->
            runCatching { listener.onResourceChanged(change) }
        }
    }

    /**
     * Writes this storage to NBT.
     * Subclasses should provide a keyWriter function.
     *
     * 将此存储写入 NBT。
     * 子类应提供 keyWriter 函数。
     */
    public fun writeNBT(nbt: NBTTagCompound, keyWriter: (K, NBTTagCompound) -> Unit): NBTTagCompound {
        val list = NBTTagList()
        storage.values.forEach { key ->
            val keyNbt = NBTTagCompound()
            keyWriter(key, keyNbt)
            list.appendTag(keyNbt)
        }
        nbt.setTag("Resources", list)
        return nbt
    }

    /**
     * Reads this storage from NBT.
     * Subclasses should provide a keyReader function.
     *
     * 从 NBT 读取此存储。
     * 子类应提供 keyReader 函数。
     */
    public fun readNBT(nbt: NBTTagCompound, keyReader: (NBTTagCompound) -> K?) {
        storage.clear()
        val list = nbt.getTagList("Resources", Constants.NBT.TAG_COMPOUND)
        for (i in 0 until list.tagCount()) {
            val keyNbt = list.getCompoundTagAt(i)
            val key = keyReader(keyNbt) ?: continue
            val uniqueKey = getUniqueKey(key)
            // Enforce limits on load.
            // - Do not exceed maxTypes
            // - Clamp count to maxCountPerType
            if (!storage.containsKey(uniqueKey) && storage.size >= maxTypes) {
                continue
            }
            if (key.count > maxCountPerType) {
                key.count = maxCountPerType
            }
            storage[uniqueKey] = key
        }
    }

}
