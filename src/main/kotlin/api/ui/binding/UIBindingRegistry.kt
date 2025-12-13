package github.kasuminova.prototypemachinery.api.ui.binding

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import net.minecraft.util.ResourceLocation

/**
 * Registry for UI data bindings.
 *
 * Bindings are resolved on the server to provide getters (and optional setters) which are then
 * wired into ModularUI SyncValues.
 *
 * 设计目标：
 * - UI 定义只引用 key（字符串）
 * - 运行时按 machineType 解析 key -> getter / setter（可写）
 * - 低开销扩展：按组件/模组/脚本注册 bindings
 */
public interface UIBindingRegistry {

    public enum class ValueType {
        BOOL,
        DOUBLE,
        STRING
    }

    public data class BindingKey(
        val type: ValueType,
        val key: String
    )

    public sealed interface ResolvedBinding<T> {
        public val key: BindingKey
        public val getter: (MachineInstance) -> T
        public val writable: Boolean
    }

    public data class ResolvedBool(
        override val key: BindingKey,
        override val getter: (MachineInstance) -> Boolean,
        public val setter: ((MachineInstance, Boolean) -> Unit)? = null
    ) : ResolvedBinding<Boolean> {
        override val writable: Boolean get() = setter != null
    }

    public data class ResolvedDouble(
        override val key: BindingKey,
        override val getter: (MachineInstance) -> Double,
        public val setter: ((MachineInstance, Double) -> Unit)? = null
    ) : ResolvedBinding<Double> {
        override val writable: Boolean get() = setter != null
    }

    public data class ResolvedString(
        override val key: BindingKey,
        override val getter: (MachineInstance) -> String,
        public val setter: ((MachineInstance, String) -> Unit)? = null
    ) : ResolvedBinding<String> {
        override val writable: Boolean get() = setter != null
    }

    // ------------------------ registration ------------------------

    public fun registerBool(
        machineId: ResourceLocation?,
        key: String,
        getter: (MachineInstance) -> Boolean,
        setter: ((MachineInstance, Boolean) -> Unit)? = null,
        owner: String = "unknown"
    )

    public fun registerDouble(
        machineId: ResourceLocation?,
        key: String,
        getter: (MachineInstance) -> Double,
        setter: ((MachineInstance, Double) -> Unit)? = null,
        owner: String = "unknown"
    )

    public fun registerString(
        machineId: ResourceLocation?,
        key: String,
        getter: (MachineInstance) -> String,
        setter: ((MachineInstance, String) -> Unit)? = null,
        owner: String = "unknown"
    )

    public fun clear(machineId: ResourceLocation)

    public fun clearAll()

    // ------------------------ resolve ------------------------

    public fun resolveBool(machine: MachineInstance, key: String): ResolvedBool?

    public fun resolveDouble(machine: MachineInstance, key: String): ResolvedDouble?

    public fun resolveString(machine: MachineInstance, key: String): ResolvedString?
}
