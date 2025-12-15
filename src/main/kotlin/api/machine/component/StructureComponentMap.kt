package github.kasuminova.prototypemachinery.api.machine.component

/**
 * # StructureComponentMap
 *
 * A lightweight component collection populated from structure forming/refreshing.
 *
 * 结构组件表：在结构形成/刷新期间重建的轻量组件集合。
 *
 * - 不包含 systems 概念
 * - 不参与 machine tick
 * - 主要用于提供“结构提供的容器/端口组件”视图（Item/Fluid/Energy 等）
 */
public interface StructureComponentMap {

    /** Components in insertion order. / 按插入顺序保存的组件列表。 */
    public val components: List<StructureComponent>

    /** Clear all collected components. / 清空所有收集到的组件。 */
    public fun clear()

    /** Add a single component. / 添加单个组件。 */
    public fun add(component: StructureComponent)

    /** Add multiple components. / 添加多个组件。 */
    public fun addAll(components: Iterable<StructureComponent>) {
        for (c in components) add(c)
    }

    /**
     * Get all components that are instances of [clazz].
     * 获取所有是 [clazz] 实例的组件。
     */
    public fun <C : StructureComponent> getByInstanceOf(clazz: Class<out C>): Collection<C>
}
