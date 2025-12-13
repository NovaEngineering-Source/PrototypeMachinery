package github.kasuminova.prototypemachinery.api.machine.component

/**
 * Provides scheduling affinity keys for grouping schedulables.
 *
 * Components that represent or connect to shared resources (e.g. shared IO devices / shared container providers)
 * should implement this interface and return stable keys.
 *
 * Returned keys are used by the scheduler to detect shared-resource groups (union-find) and
 * execute those machines on the same single-thread lane to reduce contention / throughput jitter.
 *
 * 为调度分组提供亲和键。
 *
 * 表示“共享资源”（例如共享 IO 设备 / 共享容器提供者）的组件应实现此接口并返回稳定的 key。
 * 调度器会基于这些 key 检测共享资源分组，并将同组机器放到同一单线程 lane 串行执行。
 */
public interface AffinityKeyProvider {

    /**
     * Affinity keys for this component.
     *
     * Keys MUST have stable equals/hashCode semantics.
     * It is acceptable to return identity-based keys (e.g., the shared provider object) when appropriate.
     */
    public fun getAffinityKeys(): Set<Any>

}
