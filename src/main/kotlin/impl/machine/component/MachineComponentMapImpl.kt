package github.kasuminova.prototypemachinery.impl.machine.component

import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentMap
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType
import github.kasuminova.prototypemachinery.api.machine.component.system.MachineSystem
import github.kasuminova.prototypemachinery.impl.ecs.TopologicalComponentMapImpl
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
                // Derive system order from component topological order
                // 从组件拓扑顺序派生系统顺序
                _cachedSystemsList = orderedComponents.map { it.key.system }.distinct()
            }
            return _cachedSystemsList!!
        }

    private val byInstanceOfCache: MutableMap<Class<out MachineComponent>, MutableCollection<MachineComponent>> = IdentityHashMap()

    override fun add(component: MachineComponent) {
        val type = component.type
        val system = type.system
        val systemClass = system::class.java
        
        // Register type for system
        typesBySystem.computeIfAbsent(systemClass) { mutableSetOf() }.add(type)
        
        // Calculate dependencies
        val dependencies = HashSet<MachineComponentType<*>>()
        dependencies.addAll(type.dependencies)
        
        // 1. Add dependencies based on system.runAfter
        for (depSystemClass in system.runAfter) {
            typesBySystem[depSystemClass]?.let { dependencies.addAll(it) }
        }
        
        // Add to topological map
        super.add(type, component, dependencies)
        
        _components[type] = component
        
        // 2. Add reverse dependencies based on system.runBefore
        for (dependentSystemClass in system.runBefore) {
            typesBySystem[dependentSystemClass]?.forEach { dependentType ->
                addDependency(dependentType, type)
            }
        }
        
        // 3. Handle existing components that should depend on this new component
        // or that this component should depend on (due to their runBefore)
        for ((otherSystemClass, otherTypes) in typesBySystem) {
            if (otherSystemClass == systemClass) continue
            
            val otherSystem = otherTypes.first().system
            
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
        
        // Invalidate systems cache
        _cachedSystemsList = null

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
        val systemClass = type.system::class.java
        
        typesBySystem[systemClass]?.remove(type)
        if (typesBySystem[systemClass]?.isEmpty() == true) {
            typesBySystem.remove(systemClass)
        }
        
        super.remove(type)
        
        _components.remove(component.type)
        
        // Invalidate systems cache
        _cachedSystemsList = null

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
            _components.values.filter { clazz.isInstance(it) }.toMutableSet() 
        } as Collection<C>

}