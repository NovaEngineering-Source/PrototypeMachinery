package github.kasuminova.prototypemachinery.impl.machine.attribute

import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeInstance
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeModifier
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeType
import java.util.concurrent.ConcurrentHashMap

/**
 * Default attribute instance implementation.
 * 默认属性实例实现。
 *
 * ## Thread-safety / 线程安全
 *
 * Modifiers are stored in a [ConcurrentHashMap]. Reads are safe; concurrent writes are allowed
 * but callers should still avoid heavy contention.
 *
 * 修改器存储在 [ConcurrentHashMap] 中。读取安全；允许并发写入，但调用方应尽量避免高争用。
 *
 * ## Caching / 缓存
 *
 * [value] is cached and invalidated when:
 * - [base] changes
 * - a modifier is added/removed
 *
 * [value] 有缓存，并在以下情况下失效：
 * - [base] 发生变化
 * - 增删修改器
 *
 * ## Modifier order / 修改器顺序
 *
 * The canonical order is: ADDITION -> MULTIPLY_BASE -> MULTIPLY_TOTAL.
 *
 * 统一顺序为：加法 -> 基础乘法 -> 总乘法。
 */
public class MachineAttributeInstanceImpl(
    override val attribute: MachineAttributeType,
    base: Double = 0.0
) : MachineAttributeInstance {

    private val modifiersMap: MutableMap<String, MachineAttributeModifier> = ConcurrentHashMap()

    @Volatile
    private var dirty: Boolean = true

    @Volatile
    private var cachedValue: Double = 0.0

    private var _base: Double = base

    override var base: Double
        get() = _base
        set(value) {
            if (_base == value) return
            _base = value
            markDirty()
        }

    override val modifiers: Map<String, MachineAttributeModifier>
        get() = modifiersMap

    override val value: Double
        get() {
            if (!dirty) return cachedValue

            val computed = calculateValue()
            cachedValue = computed
            dirty = false
            return computed
        }

    override fun addModifier(modifier: MachineAttributeModifier): Boolean {
        val inserted = modifiersMap.putIfAbsent(modifier.id, modifier) == null
        if (inserted) markDirty()
        return inserted
    }

    override fun removeModifier(id: String): MachineAttributeModifier? {
        val removed = modifiersMap.remove(id)
        if (removed != null) markDirty()
        return removed
    }

    override fun hasModifier(id: String): Boolean {
        return modifiersMap.containsKey(id)
    }

    override fun getModifier(id: String): MachineAttributeModifier? {
        return modifiersMap[id]
    }

    private fun markDirty() {
        dirty = true
    }

    private fun calculateValue(): Double {
        // Apply modifiers in order: ADDITION -> MULTIPLY_BASE -> MULTIPLY_TOTAL
        val additions = ArrayList<MachineAttributeModifier>()
        val multiplyBase = ArrayList<MachineAttributeModifier>()
        val multiplyTotal = ArrayList<MachineAttributeModifier>()

        for (modifier in modifiersMap.values) {
            when (modifier.operation) {
                MachineAttributeModifier.Operation.ADDITION -> additions.add(modifier)
                MachineAttributeModifier.Operation.MULTIPLY_BASE -> multiplyBase.add(modifier)
                MachineAttributeModifier.Operation.MULTIPLY_TOTAL -> multiplyTotal.add(modifier)
            }
        }

        val base = this.base
        var result = base

        for (modifier in additions) {
            result = modifier.apply(base, result)
        }

        for (modifier in multiplyBase) {
            result = modifier.apply(base, result)
        }

        for (modifier in multiplyTotal) {
            result = modifier.apply(base, result)
        }

        return result
    }

}
