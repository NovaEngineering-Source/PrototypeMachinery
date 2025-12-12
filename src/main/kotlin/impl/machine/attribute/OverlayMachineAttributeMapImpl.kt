package github.kasuminova.prototypemachinery.impl.machine.attribute

import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeInstance
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeMap
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeType
import java.util.concurrent.ConcurrentHashMap

/**
 * Attribute map that overlays per-owner (e.g. per process) modifiers on top of a parent map.
 *
 * This enables:
 * - MachineInstance attributes providing the baseline.
 * - Each RecipeProcess adding extra modifiers without affecting other processes.
 *
 * 在父 Map（通常是机器基线）之上叠加“本地修改”（通常是某个 RecipeProcess 的修改器）。
 *
 * ## Serialization / 序列化
 *
 * Only *local changes* should be persisted (local modifiers + base override),
 * otherwise the machine baseline would be duplicated into every process save.
 *
 * 只应持久化 *本地变化*（本地 modifiers + base override），
 * 否则会把机器基线重复写进每个进程存档。
 */
public class OverlayMachineAttributeMapImpl(
    private val parent: MachineAttributeMap,
    private val defaultBase: Double = 0.0,
) : MachineAttributeMap {

    private val local: MutableMap<MachineAttributeType, OverlayMachineAttributeInstanceImpl> = ConcurrentHashMap()

    internal fun localInstances(): Collection<OverlayMachineAttributeInstanceImpl> = local.values

    internal fun clearLocal() {
        local.clear()
    }

    override val attributes: Map<MachineAttributeType, MachineAttributeInstance>
        get() {
            // Merge view (local overrides parent by key).
            val merged = LinkedHashMap<MachineAttributeType, MachineAttributeInstance>()

            // Parent attributes are visible via overlay instances.
            parent.attributes.forEach { (type, _) ->
                merged[type] = getOrCreateAttribute(type, defaultBase = defaultBase)
            }

            // Local-only attributes.
            local.forEach { (type, instance) ->
                if (!merged.containsKey(type)) {
                    merged[type] = instance
                }
            }

            return merged
        }

    public fun getAttribute(type: MachineAttributeType): MachineAttributeInstance? {
        return local[type] ?: findParentInstance(type)?.let { parentInstance ->
            getOrCreateAttribute(type, defaultBase = parentInstance.base)
        }
    }

    public fun getOrCreateAttribute(type: MachineAttributeType, defaultBase: Double = this.defaultBase): MachineAttributeInstance {
        return local.computeIfAbsent(type) {
            val parentInstance = findParentInstance(type)
            val baseWhenNoParent = parentInstance?.base ?: defaultBase
            OverlayMachineAttributeInstanceImpl(it, parentInstance, baseWhenNoParent)
        }
    }

    private fun findParentInstance(type: MachineAttributeType): MachineAttributeInstance? {
        // Fast path: same key instance.
        parent.attributes[type]?.let { return it }

        // Fallback: match by id.
        return parent.attributes.entries.firstOrNull { (k, _) -> k.id == type.id }?.value
    }
}
