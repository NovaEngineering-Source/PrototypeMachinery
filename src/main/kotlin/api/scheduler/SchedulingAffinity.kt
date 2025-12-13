package github.kasuminova.prototypemachinery.api.scheduler

/**
 * Provides scheduling affinity keys.
 *
 * If multiple schedulables share at least one affinity key, the scheduler may choose to
 * execute them on the same single-thread lane to avoid contention / jitter.
 *
 * 提供调度亲和键。
 *
 * 如果多个可调度对象共享至少一个 affinity key，调度器可以选择将它们放入同一条单线程 lane
 * 串行执行，以减少资源争用与吞吐抖动。
 */
public interface SchedulingAffinity {

    /**
     * Keys identifying shared resources (e.g., IO device, container provider).
     *
     * IMPORTANT: Keys must have stable equals/hashCode semantics.
     * For identity-based keys, using the provider object itself is acceptable.
     *
     * 标识共享资源（例如 IO 设备/容器提供者）的键集合。
     *
     * 注意：key 必须具备稳定的 equals/hashCode 语义。
     * 若使用“对象身份”作为 key，直接返回 provider 对象本身即可。
     */
    public fun getSchedulingAffinityKeys(): Set<Any>

}
