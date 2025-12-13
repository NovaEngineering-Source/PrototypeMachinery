package github.kasuminova.prototypemachinery.impl.machine.component

import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentMap
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType
import github.kasuminova.prototypemachinery.api.machine.component.system.MachineSystem
import github.kasuminova.prototypemachinery.impl.ecs.TopologicalComponentMapImpl
import java.util.Collections
import java.util.IdentityHashMap

public class MachineComponentMapImpl : TopologicalComponentMapImpl<MachineComponentType<*>, MachineComponent>(), MachineComponentMap {

    private val _components: MutableMap<MachineComponentType<*>, MachineComponent> = IdentityHashMap()
    private val typesBySystem: MutableMap<Class<out MachineSystem<*>>, MutableSet<MachineComponentType<*>>> = IdentityHashMap()

    override val components: Map<MachineComponentType<*>, MachineComponent>
        get() = _components

    private var _cachedSystemsList: List<MachineSystem<*>>? = null
    override val systems: List<MachineSystem<*>>
        get() {
            if (_cachedSystemsList == null) {
                // Derive system order from component topological order, filter out null systems
                // 从组件拓扑顺序派生系统顺序，过滤掉 null 系统
                _cachedSystemsList = orderedComponents.mapNotNull { it.key.system }.distinct()
            }
            return _cachedSystemsList!!
        }

    private val byInstanceOfCache: MutableMap<Class<out MachineComponent>, MutableSet<MachineComponent>> = IdentityHashMap()

    internal data class OrderedTickEntry(
        val system: MachineSystem<MachineComponent>,
        val component: MachineComponent
    )

    @Volatile
    private var cachedOrderedTickEntriesModCount: Int = -1

    @Volatile
    private var cachedOrderedTickEntries: Array<OrderedTickEntry> = emptyArray()

    @Suppress("UNCHECKED_CAST")
    internal fun orderedTickEntries(): Array<OrderedTickEntry> {
        val mod = modificationCount
        if (mod == cachedOrderedTickEntriesModCount) {
            return cachedOrderedTickEntries
        }

        // Derive from component topological order.
        // Filter out components without systems.
        // 从组件拓扑顺序派生。
        // 过滤掉没有系统的组件。
        val entries = ArrayList<OrderedTickEntry>(orderedComponents.size)
        for (node in orderedComponents) {
            val system = node.key.system as? MachineSystem<MachineComponent> ?: continue
            entries.add(OrderedTickEntry(system, node.component))
        }
        val arr = entries.toTypedArray()

        cachedOrderedTickEntries = arr
        cachedOrderedTickEntriesModCount = mod
        return arr
    }

    private fun identityComponentSet(): MutableSet<MachineComponent> =
        Collections.newSetFromMap(IdentityHashMap())

    override fun add(component: MachineComponent) {
        val type = component.type
        val system = type.system

        // Register type for system (if system exists)
        if (system != null) {
            val systemClass = system::class.java
            typesBySystem.computeIfAbsent(systemClass) { mutableSetOf() }.add(type)
        }

        // Calculate dependencies
        val dependencies = HashSet<MachineComponentType<*>>()
        dependencies.addAll(type.dependencies)

        // 1. Add dependencies based on system.runAfter (if system exists)
        if (system != null) {
            for (depSystemClass in system.runAfter) {
                typesBySystem[depSystemClass]?.let { dependencies.addAll(it) }
            }
        }

        // Add to topological map
        super.add(type, component, dependencies)

        _components[type] = component

        // 2. Add reverse dependencies based on system.runBefore (if system exists)
        if (system != null) {
            for (dependentSystemClass in system.runBefore) {
                typesBySystem[dependentSystemClass]?.forEach { dependentType ->
                    addDependency(dependentType, type)
                }
            }
        }

        // 3. Handle existing components that should depend on this new component
        // or that this component should depend on (due to their runBefore)
        if (system != null) {
            val systemClass = system::class.java
            for ((otherSystemClass, otherTypes) in typesBySystem) {
                if (otherSystemClass == systemClass) continue

                val otherSystem = otherTypes.first().system ?: continue

                // If other system runs after this system, other types depend on this type
                if (otherSystem.runAfter.contains(systemClass)) {
                    otherTypes.forEach { otherType ->
                        addDependency(otherType, type)
                    }
                }

                // If other system runs before this system, this type depends on other types
                if (otherSystem.runBefore.contains(systemClass)) {
                    otherTypes.forEach { otherType ->
                        addDependency(type, otherType)
                    }
                }
            }
        }

        // Invalidate systems cache
        _cachedSystemsList = null

        // Invalidate ordered tick cache
        cachedOrderedTickEntriesModCount = -1

        // Update cache incrementally
        // 增量更新缓存
        for ((clazz, collection) in byInstanceOfCache) {
            if (clazz.isInstance(component)) {
                collection.add(component)
            }
        }
    }

    override fun remove(component: MachineComponent) {
        val type = component.type
        val system = type.system

        if (system != null) {
            val systemClass = system::class.java
            typesBySystem[systemClass]?.remove(type)
            if (typesBySystem[systemClass]?.isEmpty() == true) {
                typesBySystem.remove(systemClass)
            }
        }

        super.remove(type)

        _components.remove(component.type)

        // Invalidate systems cache
        _cachedSystemsList = null

        // Invalidate ordered tick cache
        cachedOrderedTickEntriesModCount = -1

        // Update cache incrementally
        // 增量更新缓存
        for ((clazz, collection) in byInstanceOfCache) {
            if (clazz.isInstance(component)) {
                collection.remove(component)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <C : MachineComponent> getByInstanceOf(clazz: Class<out C>): Collection<C> =
        byInstanceOfCache.getOrPut(clazz) {
            // Use identity-based set to avoid relying on component equals/hashCode.
            // 使用 identity set，避免依赖组件的 equals/hashCode。
            val set = identityComponentSet()
            for (c in _components.values) {
                if (clazz.isInstance(c)) {
                    set.add(c)
                }
            }
            set
        } as Collection<C>

}