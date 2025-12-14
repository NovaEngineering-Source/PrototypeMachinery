package github.kasuminova.prototypemachinery.api.storage

import github.kasuminova.prototypemachinery.api.key.PMKey

/**
 * # SlottedResourceStorage - Slot-based storage
 * # SlottedResourceStorage - 槽位型存储
 *
 * A storage model where capacity is represented as a fixed number of slots.
 * Each slot can hold a resource key with its own count up to a per-slot limit.
 * The same resource type MAY appear in multiple slots.
 *
 * 以固定“槽位数”表示容量的存储模型。
 * 每个槽位可单独持有某种资源及其数量（受单槽上限限制）。
 * 同一种资源类型可以出现在多个槽位中。
 */
public interface SlottedResourceStorage<K : PMKey<*>> : ObservableResourceStorage<K> {

    /** Total number of slots. / 槽位总数。 */
    public val slotCount: Int

    /** Gets the resource currently stored in a slot, or null if empty. / 获取槽位内容（空则为 null）。 */
    public fun getSlot(index: Int): K?

    /**
     * Extracts from a specific slot.
     * Returns the amount actually extracted.
     *
     * 从指定槽位提取。
     * 返回实际提取数量。
     */
    public fun extractFromSlot(index: Int, amount: Long, simulate: Boolean): Long

    /**
     * Returns and clears the set of slot indices that changed since the last clear.
     * This is intended for efficient GUI/network synchronization.
     *
     * 返回并清空自上次清理以来发生变化的槽位索引集合。
     * 该方法用于高效的 GUI/网络同步，避免扫描全部槽位。
     */
    public fun drainPendingSlotChanges(): IntArray
}
