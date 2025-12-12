package github.kasuminova.prototypemachinery.impl.machine.attribute

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeInstance
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeMap
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeType
import java.util.concurrent.ConcurrentHashMap

/**
 * Machine-level attribute map (baseline).
 * 机器层属性 Map（基线）。
 *
 * Stores attributes as mutable instances. Usually attached to a [MachineInstance].
 *
 * 保存可变的属性实例，通常挂在 [MachineInstance]（机器实例）上。
 *
 * ## Serialization / 序列化
 *
 * This map is persisted in full (base + modifiers) via `MachineAttributeNbt.writeMachineMap`.
 *
 * 该 Map 会通过 `MachineAttributeNbt.writeMachineMap` 全量持久化（base + modifiers）。
 */
public class MachineAttributeMapImpl : MachineAttributeMap {

    private val attributesMap: MutableMap<MachineAttributeType, MachineAttributeInstance> = ConcurrentHashMap()

    override val attributes: Map<MachineAttributeType, MachineAttributeInstance>
        get() = attributesMap

    public fun getAttribute(type: MachineAttributeType): MachineAttributeInstance? {
        return attributesMap[type]
    }

    public fun getOrCreateAttribute(type: MachineAttributeType, defaultBase: Double = 0.0): MachineAttributeInstance {
        return attributesMap.computeIfAbsent(type) { MachineAttributeInstanceImpl(it, defaultBase) }
    }

    public fun setAttribute(instance: MachineAttributeInstance): MachineAttributeInstance? {
        return attributesMap.put(instance.attribute, instance)
    }

    public fun removeAttribute(type: MachineAttributeType): MachineAttributeInstance? {
        return attributesMap.remove(type)
    }

    public fun hasAttribute(type: MachineAttributeType): Boolean {
        return attributesMap.containsKey(type)
    }

    public fun clear() {
        attributesMap.clear()
    }

}
