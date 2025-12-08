package github.kasuminova.prototypemachinery.impl.machine.attribute

import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeInstance
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeMap
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeType
import java.util.concurrent.ConcurrentHashMap

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
