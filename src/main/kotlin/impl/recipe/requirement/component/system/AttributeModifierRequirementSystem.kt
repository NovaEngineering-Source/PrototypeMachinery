package github.kasuminova.prototypemachinery.impl.recipe.requirement.component.system

import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeInstance
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeMap
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeModifier
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeType
import github.kasuminova.prototypemachinery.api.recipe.process.ProcessResult
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RecipeRequirementSystem
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RequirementTransaction
import github.kasuminova.prototypemachinery.impl.machine.attribute.MachineAttributeModifierImpl
import github.kasuminova.prototypemachinery.impl.machine.attribute.OverlayMachineAttributeMapImpl
import github.kasuminova.prototypemachinery.impl.recipe.requirement.component.AttributeModifierRequirementComponent
import net.minecraft.util.ResourceLocation

/**
 * Minimal built-in system that applies/removes process attribute modifiers.
 *
 * 这是一个“先起功能”的系统：
 * - start(): 施加 modifier（可配置）
 * - onEnd(): 撤销 modifier（可配置）
 */
public object AttributeModifierRequirementSystem : RecipeRequirementSystem<AttributeModifierRequirementComponent> {

    override fun start(process: RecipeProcess, component: AttributeModifierRequirementComponent): RequirementTransaction {
        if (!component.applyAtStart) return noOpSuccess()

        val attrType = attributeType(component.attributeId)
        val instance = getOrCreateAttribute(process.attributeMap, attrType)
            ?: return noOpSuccess()

        val internalId = internalModifierId(component.id, component.attributeId)

        // Apply immediately; rollback will undo.
        val applied = if (instance.hasModifier(internalId)) {
            false
        } else {
            instance.addModifier(component.modifier.toInternal(internalId, adder = component))
        }

        return object : RequirementTransaction {
            override val result: ProcessResult = ProcessResult.Success

            override fun commit() {
                // No-op: effects already applied.
            }

            override fun rollback() {
                if (applied) {
                    instance.removeModifier(internalId)
                }
            }
        }
    }

    override fun onEnd(process: RecipeProcess, component: AttributeModifierRequirementComponent): RequirementTransaction {
        if (!component.removeAtEnd) return noOpSuccess()

        val attrType = attributeType(component.attributeId)
        val instance = getAttribute(process.attributeMap, attrType) ?: return noOpSuccess()

        val internalId = internalModifierId(component.id, component.attributeId)
        val removed: MachineAttributeModifier? = instance.removeModifier(internalId)

        return object : RequirementTransaction {
            override val result: ProcessResult = ProcessResult.Success

            override fun commit() {
                // No-op: removal already applied.
            }

            override fun rollback() {
                // Re-add if end stage is rolled back.
                if (removed != null && !instance.hasModifier(internalId)) {
                    instance.addModifier(removed)
                }
            }
        }
    }

    private fun internalModifierId(componentId: String, attributeId: ResourceLocation): String {
        return "req:${componentId}:${attributeId.namespace}:${attributeId.path}"
    }

    private fun MachineAttributeModifier.toInternal(id: String, adder: Any?): MachineAttributeModifier {
        return MachineAttributeModifierImpl(
            id = id,
            amount = this.amount,
            operation = this.operation,
            adder = adder,
        )
    }

    private fun attributeType(id: ResourceLocation): MachineAttributeType {
        // Until we have a real attribute registry, we construct a lightweight type wrapper.
        return object : MachineAttributeType {
            override val id: ResourceLocation = id
            override val name: String = id.toString()
        }
    }

    private fun getAttribute(map: MachineAttributeMap, type: MachineAttributeType): MachineAttributeInstance? {
        // Fast path: exact key
        map.attributes[type]?.let { return it }
        // Fallback: match by id
        return map.attributes.entries.firstOrNull { (k, _) -> k.id == type.id }?.value
    }

    private fun getOrCreateAttribute(map: MachineAttributeMap, type: MachineAttributeType): MachineAttributeInstance? {
        val existing = getAttribute(map, type)
        if (existing != null) return existing

        // Only overlay map is mutable in our current design.
        val overlay = map as? OverlayMachineAttributeMapImpl ?: return null
        return overlay.getOrCreateAttribute(type)
    }

    private fun noOpSuccess(): RequirementTransaction {
        return RequirementTransaction.NoOpSuccess
    }
}
