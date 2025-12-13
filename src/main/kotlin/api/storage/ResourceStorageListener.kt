package github.kasuminova.prototypemachinery.api.storage

import github.kasuminova.prototypemachinery.api.key.PMKey

/**
 * # ResourceStorageListener - Storage Change Listener
 * # ResourceStorageListener - 存储变更监听器
 *
 * Listens for incremental changes in a [ResourceStorage].
 * Used for synchronization and change tracking.
 *
 * 监听 [ResourceStorage] 中的增量变更。
 * 用于同步和变更追踪。
 *
 * @param K The specific PMKey type this listener handles
 */
public fun interface ResourceStorageListener<K : PMKey<*>> {

    /**
     * Called when a resource changes in the storage.
     *
     * 当存储中的资源发生变化时调用。
     *
     * @param change The change that occurred
     */
    public fun onResourceChanged(change: ResourceChange<K>)

}

/**
 * # ResourceChange - Describes a single resource change
 * # ResourceChange - 描述单个资源变更
 *
 * Immutable data class representing a change in resource storage.
 *
 * 不可变数据类，表示资源存储中的变更。
 *
 * @param key The resource key that changed
 * @param previousAmount The amount before the change
 * @param newAmount The amount after the change
 * @param type The type of change
 */
public data class ResourceChange<K : PMKey<*>>(
    val key: K,
    val previousAmount: Long,
    val newAmount: Long,
    val type: ChangeType
) {
    /**
     * The delta (difference) of this change.
     * Positive for insertions, negative for extractions.
     *
     * 此变更的增量（差异）。
     * 插入为正，提取为负。
     */
    public val delta: Long
        get() = newAmount - previousAmount
}

/**
 * # ChangeType - Type of resource change
 * # ChangeType - 资源变更类型
 */
public enum class ChangeType {
    /**
     * Resource was inserted (amount increased).
     * 资源被插入（数量增加）。
     */
    INSERTED,

    /**
     * Resource was extracted (amount decreased).
     * 资源被提取（数量减少）。
     */
    EXTRACTED,

    /**
     * Resource was removed completely.
     * 资源被完全移除。
     */
    REMOVED,

    /**
     * Storage was cleared.
     * 存储被清空。
     */
    CLEARED
}
