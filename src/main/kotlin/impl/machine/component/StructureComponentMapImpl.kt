package github.kasuminova.prototypemachinery.impl.machine.component

import github.kasuminova.prototypemachinery.api.machine.component.StructureComponent
import github.kasuminova.prototypemachinery.api.machine.component.StructureComponentMap
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Default in-memory implementation of [StructureComponentMap].
 *
 * - Maintains insertion order.
 * - Provides a small instanceOf cache.
 */
public class StructureComponentMapImpl : StructureComponentMap {

    /**
     * Snapshot for lock-free concurrent reads.
     *
     * Writers rebuild a new snapshot and replace the reference.
     */
    private data class Snapshot(
        val components: List<StructureComponent>,
        val instanceOfCache: ConcurrentHashMap<Class<*>, Set<StructureComponent>>
    )

    @Volatile
    private var snapshot: Snapshot = Snapshot(
        components = Collections.emptyList(),
        instanceOfCache = ConcurrentHashMap()
    )

    override fun clear() {
        replaceAll(emptyList())
    }

    override val components: List<StructureComponent>
        get() = snapshot.components

    override fun add(component: StructureComponent) {
        // Copy-on-write to keep reads lock-free.
        val current = snapshot
        val newList = ArrayList<StructureComponent>(current.components.size + 1)
        newList.addAll(current.components)
        newList.add(component)

        snapshot = Snapshot(
            components = Collections.unmodifiableList(newList),
            instanceOfCache = ConcurrentHashMap()
        )
    }

    /**
     * Atomically replace all components.
     *
     * Intended for structure forming/refresh: build a new list, then swap.
     */
    internal fun replaceAll(components: Collection<StructureComponent>) {
        snapshot = Snapshot(
            components = Collections.unmodifiableList(ArrayList(components)),
            instanceOfCache = ConcurrentHashMap()
        )
    }

    override fun <C : StructureComponent> getByInstanceOf(clazz: Class<out C>): Collection<C> {
        val current = snapshot
        val cached = current.instanceOfCache[clazz]
        if (cached != null) {
            @Suppress("UNCHECKED_CAST")
            return cached as Collection<C>
        }

        // Build once per class per snapshot; safe for concurrent readers.
        val computed: Set<StructureComponent> = Collections.newSetFromMap(IdentityHashMap<StructureComponent, Boolean>())
            .also { set ->
                for (c in current.components) {
                    if (clazz.isInstance(c)) {
                        set.add(c)
                    }
                }
            }

        current.instanceOfCache.putIfAbsent(clazz, computed)
        @Suppress("UNCHECKED_CAST")
        return (current.instanceOfCache[clazz] ?: computed) as Collection<C>
    }
}
