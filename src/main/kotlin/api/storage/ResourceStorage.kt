package github.kasuminova.prototypemachinery.api.storage

import github.kasuminova.prototypemachinery.api.key.PMKey

/**
 * # ResourceStorage - Generic Resource Storage Interface
 * # ResourceStorage - 通用资源存储接口
 *
 * A generic storage interface for [PMKey] based resources.
 * Supports unordered storage with O(1) lookup by key prototype.
 *
 * 基于 [PMKey] 的通用存储接口。
 * 支持按键原型进行 O(1) 查找的无序存储。
 *
 * ## Design Goals / 设计目标
 *
 * - **High Performance**: O(1) lookup using interned key references
 *   **高性能**: 使用驻留键引用实现 O(1) 查找
 *
 * - **Large Counts**: Supports Long counts (beyond int limits)
 *   **大数量**: 支持 Long 类型的数量（超过 int 限制）
 *
 * - **Change Tracking**: Optional incremental change listeners
 *   **变更追踪**: 可选的增量变更监听器
 *
 * @param K The specific PMKey type this storage holds
 */
public interface ResourceStorage<K : PMKey<*>> {

    /**
     * Maximum number of different resource types this storage can hold.
     * 此存储可容纳的最大不同资源类型数量。
     */
    public val maxTypes: Int

    /**
     * Maximum count per resource type.
     * 每种资源类型的最大数量。
     */
    public val maxCountPerType: Long

    /**
     * Current number of different resource types stored.
     * 当前存储的不同资源类型数量。
     */
    public val usedTypes: Int

    /**
     * Whether this storage is empty.
     * 此存储是否为空。
     */
    public val isEmpty: Boolean
        get() = usedTypes == 0

    /**
     * Whether this storage is full (no more types can be added).
     * 此存储是否已满（无法添加更多类型）。
     */
    public val isFull: Boolean
        get() = usedTypes >= maxTypes

    /**
     * Gets the amount of a specific resource.
     * Returns 0 if the resource is not present.
     *
     * 获取特定资源的数量。
     * 如果资源不存在，返回 0。
     *
     * @param key The resource key to look up
     * @return The amount stored, or 0 if not present
     */
    public fun getAmount(key: K): Long

    /**
     * Checks if this storage contains the specified resource.
     *
     * 检查此存储是否包含指定的资源。
     */
    public fun contains(key: K): Boolean

    /**
     * Inserts a resource into this storage.
     *
     * 将资源插入到此存储中。
     *
     * @param key The resource key to insert
     * @param amount The amount to insert
     * @param simulate If true, only simulates the insertion without modifying storage
     * @return The amount that was actually inserted
     */
    public fun insert(key: K, amount: Long, simulate: Boolean): Long

    /**
     * Extracts a resource from this storage.
     *
     * 从此存储中提取资源。
     *
     * @param key The resource key to extract
     * @param amount The maximum amount to extract
     * @param simulate If true, only simulates the extraction without modifying storage
     * @return The amount that was actually extracted
     */
    public fun extract(key: K, amount: Long, simulate: Boolean): Long

    /**
     * Gets all resources stored in this storage.
     * The returned collection is a snapshot and may be safely iterated.
     *
     * 获取此存储中存储的所有资源。
     * 返回的集合是快照，可以安全迭代。
     *
     * @return An immutable collection of all stored resources
     */
    public fun getAllResources(): Collection<K>

    /**
     * Clears all resources from this storage.
     * 清空此存储中的所有资源。
     */
    public fun clear()

}
