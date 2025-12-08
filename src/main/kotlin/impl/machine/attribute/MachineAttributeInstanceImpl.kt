package github.kasuminova.prototypemachinery.impl.machine.attribute

import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeInstance
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeModifier
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeType
import java.util.concurrent.ConcurrentHashMap

public class MachineAttributeInstanceImpl(
    override val attribute: MachineAttributeType,
    override var base: Double = 0.0
) : MachineAttributeInstance {

    private val modifiersMap: MutableMap<String, MachineAttributeModifier> = ConcurrentHashMap()

    override val modifiers: Map<String, MachineAttributeModifier>
        get() = modifiersMap

    override val value: Double
        get() = calculateValue()

    override fun addModifier(modifier: MachineAttributeModifier): Boolean {
        return modifiersMap.putIfAbsent(modifier.id, modifier) == null
    }

    override fun removeModifier(id: String): MachineAttributeModifier? {
        return modifiersMap.remove(id)
    }

    override fun hasModifier(id: String): Boolean {
        return modifiersMap.containsKey(id)
    }

    override fun getModifier(id: String): MachineAttributeModifier? {
        return modifiersMap[id]
    }

    private fun calculateValue(): Double {
        // Apply modifiers in order: ADDITION -> MULTIPLY_BASE -> MULTIPLY_TOTAL
        val additions = modifiersMap.values.filter {
            (it as? MachineAttributeModifierImpl)?.operation == MachineAttributeModifier.Operation.ADDITION
        }
        val multiplyBase = modifiersMap.values.filter {
            (it as? MachineAttributeModifierImpl)?.operation == MachineAttributeModifier.Operation.MULTIPLY_BASE
        }
        val multiplyTotal = modifiersMap.values.filter {
            (it as? MachineAttributeModifierImpl)?.operation == MachineAttributeModifier.Operation.MULTIPLY_TOTAL
        }

        var result = base

        // Apply additions first
        for (modifier in additions) {
            result = modifier.apply(base, result)
        }

        // Apply base multipliers
        for (modifier in multiplyBase) {
            result = modifier.apply(base, result)
        }

        // Apply total multipliers
        for (modifier in multiplyTotal) {
            result = modifier.apply(base, result)
        }

        return result
    }

}
