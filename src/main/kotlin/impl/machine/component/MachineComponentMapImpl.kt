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
    private val systemByClass: MutableMap<Class<out MachineSystem<*>>, MachineSystem<*>> = IdentityHashMap()

    override val components: Map<MachineComponentType<*>, MachineComponent>
        get() = _components

    private var _cachedSystemsList: List<MachineSystem<*>>? = null
    override val systems: List<MachineSystem<*>>
        get() {
            if (_cachedSystemsList == null) {
                // Derive system order from component topological order, filter out null systems
                // 从组件拓扑顺序派生系统顺序，过滤掉 null 系统
                _cachedSystemsList = orderedComponents.flatMap { it.key.systems }.distinct()
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
            val systems = node.key.systems
            if (systems.isEmpty()) continue

            for (sys in systems) {
                @Suppress("UNCHECKED_CAST")
                val system = sys as MachineSystem<MachineComponent>
                entries.add(OrderedTickEntry(system, node.component))
            }
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
        val systems = type.systems

        // Register type for systems
        for (sys in systems) {
            val systemClass = sys::class.java
            systemByClass.putIfAbsent(systemClass, sys)
            typesBySystem.computeIfAbsent(systemClass) { mutableSetOf() }.add(type)
        }

        // Calculate dependencies
        val dependencies = HashSet<MachineComponentType<*>>()
        dependencies.addAll(type.dependencies)

        // 1. Add dependencies based on system.runAfter
        for (sys in systems) {
            for (depSystemClass in sys.runAfter) {
                typesBySystem[depSystemClass]?.let { dependencies.addAll(it) }
            }
        }

        // Add to topological map
        super.add(type, component, dependencies)

        _components[type] = component

        // 2. Add reverse dependencies based on system.runBefore
        for (sys in systems) {
            for (dependentSystemClass in sys.runBefore) {
                typesBySystem[dependentSystemClass]?.forEach { dependentType ->
                    if (dependentType != type) {
                        addDependency(dependentType, type)
                    }
                }
            }
        }

        // 3. Handle existing components that should depend on this new component
        // or that this component should depend on (due to runBefore/runAfter)
        for (sys in systems) {
            val systemClass = sys::class.java

            for ((otherSystemClass, otherTypes) in typesBySystem) {
                if (otherSystemClass == systemClass) continue

                val otherSystem = systemByClass[otherSystemClass] ?: continue

                // If other system runs after this system, other types depend on this type
                if (otherSystem.runAfter.contains(systemClass)) {
                    otherTypes.forEach { otherType ->
                        if (otherType != type) {
                            addDependency(otherType, type)
                        }
                    }
                }

                // If other system runs before this system, this type depends on other types
                if (otherSystem.runBefore.contains(systemClass)) {
                    otherTypes.forEach { otherType ->
                        if (otherType != type) {
                            addDependency(type, otherType)
                        }
                    }
                }

                // Also apply this system's declared ordering against the other system class.
                if (sys.runAfter.contains(otherSystemClass)) {
                    otherTypes.forEach { otherType ->
                        if (otherType != type) {
                            addDependency(type, otherType)
                        }
                    }
                }
                if (sys.runBefore.contains(otherSystemClass)) {
                    otherTypes.forEach { otherType ->
                        if (otherType != type) {
                            addDependency(otherType, type)
                        }
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
        val systems = type.systems

        for (sys in systems) {
            val systemClass = sys::class.java
            typesBySystem[systemClass]?.remove(type)
            if (typesBySystem[systemClass]?.isEmpty() == true) {
                typesBySystem.remove(systemClass)
                systemByClass.remove(systemClass)
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