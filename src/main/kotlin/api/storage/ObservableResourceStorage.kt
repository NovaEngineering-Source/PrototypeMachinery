package github.kasuminova.prototypemachinery.api.storage

import github.kasuminova.prototypemachinery.api.key.PMKey

/**
 * # ObservableResourceStorage - Observable Storage Interface
 * # ObservableResourceStorage - 可观察存储接口
 *
 * Extends [ResourceStorage] with listener support for tracking changes.
 *
 * 扩展 [ResourceStorage]，支持监听器以追踪变更。
 *
 * @param K The specific PMKey type this storage holds
 */
public interface ObservableResourceStorage<K : PMKey<*>> : ResourceStorage<K> {

    /**
     * Adds a listener to be notified of changes.
     *
     * 添加一个监听器，以便在发生变更时收到通知。
     *
     * @param listener The listener to add
     */
    public fun addListener(listener: ResourceStorageListener<K>)

    /**
     * Removes a previously added listener.
     *
     * 移除之前添加的监听器。
     *
     * @param listener The listener to remove
     * @return True if the listener was present and removed
     */
    public fun removeListener(listener: ResourceStorageListener<K>): Boolean

    /**
     * Checks if there are any pending changes that haven't been processed.
     * Used for incremental sync optimization.
     *
     * 检查是否有尚未处理的待处理变更。
     * 用于增量同步优化。
     *
     * @return True if there are pending changes
     */
    public fun hasPendingChanges(): Boolean

    /**
     * Clears the pending changes flag.
     * Called after changes have been synced.
     *
     * 清除待处理变更标志。
     * 在变更已同步后调用。
     */
    public fun clearPendingChanges()

}
