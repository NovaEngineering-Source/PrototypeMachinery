package github.kasuminova.prototypemachinery.impl.machine.attribute

import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeInstance
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeModifier
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeType
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess

/**
 * An attribute instance view that overlays local modifiers on top of a parent instance.
 *
 * - Local modifiers are mutable and belong to the overlay owner (e.g. a [RecipeProcess]).
 * - Parent modifiers are read-only from this view.
 * - Value is computed by applying parent + local modifiers together in the standard order.
 *
 * 一个“叠加视图”：在父实例（机器基线）之上叠加本地修改器（进程级）。
 *
 * - 本地 modifiers 可变，归属于 overlay 的拥有者（如 RecipeProcess）。
 * - 父 modifiers 在本视图中只读（不会从这里移除/修改）。
 * - [value] 会把父 + 本地 modifiers 合并后按统一顺序计算。
 *
 * ## Base override / base 覆盖
 *
 * If a parent exists, setting [base] will store an override instead of mutating the parent.
 *
 * 如果存在 parent，设置 [base] 会记录 override，而不会修改 parent。
 */
public class OverlayMachineAttributeInstanceImpl(
    override val attribute: MachineAttributeType,
    private val parent: MachineAttributeInstance?,
    baseWhenNoParent: Double,
) : MachineAttributeInstance {

    private val local: MachineAttributeInstanceImpl = MachineAttributeInstanceImpl(attribute, baseWhenNoParent)

    /**
     * If parent exists, base defaults to parent.base unless overridden.
     * If no parent, base is stored locally.
     */
    private var baseOverride: Double? = null

    internal fun hasParent(): Boolean = parent != null

    internal fun getBaseOverrideOrNull(): Double? = baseOverride

    internal fun setBaseOverrideOrNull(value: Double?) {
        baseOverride = value
    }

    internal fun localModifiers(): Map<String, MachineAttributeModifier> = local.modifiers

    internal fun hasLocalChanges(): Boolean {
        // If no parent exists, local base/modifiers are the source of truth.
        if (parent == null) return true
        return baseOverride != null || local.modifiers.isNotEmpty()
    }

    internal fun clearLocalModifiers() {
        val keys = local.modifiers.keys.toList()
        for (k in keys) {
            local.removeModifier(k)
        }
    }

    internal fun addLocalModifier(modifier: MachineAttributeModifier): Boolean {
        return local.addModifier(modifier)
    }

    override var base: Double
        get() = baseOverride ?: parent?.base ?: local.base
        set(value) {
            if (parent != null) {
                baseOverride = value
            } else {
                local.base = value
            }
        }

    override val modifiers: Map<String, MachineAttributeModifier>
        get() {
            val merged = LinkedHashMap<String, MachineAttributeModifier>()
            parent?.modifiers?.forEach { (k, v) -> merged[k] = v }
            local.modifiers.forEach { (k, v) -> merged[k] = v }
            return merged
        }

    override val value: Double
        get() = calculateValue()

    override fun addModifier(modifier: MachineAttributeModifier): Boolean {
        return local.addModifier(modifier)
    }

    override fun removeModifier(id: String): MachineAttributeModifier? {
        // Only remove local modifiers.
        return local.removeModifier(id)
    }

    override fun hasModifier(id: String): Boolean {
        return local.hasModifier(id) || (parent?.hasModifier(id) == true)
    }

    override fun getModifier(id: String): MachineAttributeModifier? {
        return local.getModifier(id) ?: parent?.getModifier(id)
    }

    private fun calculateValue(): Double {
        val base = this.base
        var result = base

        val merged = modifiers.values

        // Apply in order: ADDITION -> MULTIPLY_BASE -> MULTIPLY_TOTAL
        for (modifier in merged) {
            if (modifier.operation == MachineAttributeModifier.Operation.ADDITION) {
                result = modifier.apply(base, result)
            }
        }

        for (modifier in merged) {
            if (modifier.operation == MachineAttributeModifier.Operation.MULTIPLY_BASE) {
                result = modifier.apply(base, result)
            }
        }

        for (modifier in merged) {
            if (modifier.operation == MachineAttributeModifier.Operation.MULTIPLY_TOTAL) {
                result = modifier.apply(base, result)
            }
        }

        return result
    }
}
