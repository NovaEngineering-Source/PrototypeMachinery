package github.kasuminova.prototypemachinery.api.system

/**
 * Generic ECS-style system with three-phase ticking.
 * 通用 ECS 风格系统，包含前/中/后的三阶段 tick。
 */
public interface ComponentSystem<E, C> {

    public fun onPreTick(entity: E, component: C)

    public fun onTick(entity: E, component: C)

    public fun onPostTick(entity: E, component: C)

}
