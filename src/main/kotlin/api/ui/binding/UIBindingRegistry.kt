package github.kasuminova.prototypemachinery.api.ui.binding

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import net.minecraft.util.ResourceLocation

/**
 * # UIBindingRegistry - UI Data Binding Registry
 * # UIBindingRegistry - UI 数据绑定注册表
 *
 * Registry for server-side UI data bindings.
 * Bindings are resolved on the server to provide getters (and optional setters) which are then wired into
 * ModularUI SyncValues.
 *
 * UI 数据绑定注册表（服务端解析）。
 * 绑定会在服务端解析为 getter（以及可选的 setter），并接入 ModularUI 的同步值（SyncValues）。
 *
 * ## Design goals / 设计目标
 * - UI 定义只引用 key（字符串） / UI definitions only reference string keys
 * - 运行时按 machineType 解析 key -> getter / setter（可写） / Resolve key -> getter / setter at runtime
 * - 低开销扩展：按组件/模组/脚本注册 bindings / Low-overhead extension for addons/scripts
 *
 * ## Scoping / 作用域
 * Most registration methods accept a nullable [ResourceLocation] machineId:
 * - `machineId = null` means the binding is **global** (applies to all machines).
 * - `machineId != null` means the binding is **machine-specific**.
 *
 * 多数注册方法接收可空的 machineId：
 * - `machineId = null` 表示 **全局绑定**（对所有机器生效）。
 * - `machineId != null` 表示 **指定机器绑定**。
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

    /** Register a boolean binding. / 注册 bool 类型绑定。 */
    public fun registerBool(
        machineId: ResourceLocation?,
        key: String,
        getter: (MachineInstance) -> Boolean,
        setter: ((MachineInstance, Boolean) -> Unit)? = null,
        owner: String = "unknown"
    )

    /** Register a double binding. / 注册 double 类型绑定。 */
    public fun registerDouble(
        machineId: ResourceLocation?,
        key: String,
        getter: (MachineInstance) -> Double,
        setter: ((MachineInstance, Double) -> Unit)? = null,
        owner: String = "unknown"
    )

    /** Register a string binding. / 注册 string 类型绑定。 */
    public fun registerString(
        machineId: ResourceLocation?,
        key: String,
        getter: (MachineInstance) -> String,
        setter: ((MachineInstance, String) -> Unit)? = null,
        owner: String = "unknown"
    )

    /** Clear bindings for one machine id. / 清理指定 machineId 的绑定。 */
    public fun clear(machineId: ResourceLocation)

    /** Clear all bindings. / 清理全部绑定。 */
    public fun clearAll()

    // ------------------------ resolve ------------------------

    /** Resolve a bool binding for a concrete machine. / 为具体 machine 解析 bool 绑定。 */
    public fun resolveBool(machine: MachineInstance, key: String): ResolvedBool?

    /** Resolve a double binding for a concrete machine. / 为具体 machine 解析 double 绑定。 */
    public fun resolveDouble(machine: MachineInstance, key: String): ResolvedDouble?

    /** Resolve a string binding for a concrete machine. / 为具体 machine 解析 string 绑定。 */
    public fun resolveString(machine: MachineInstance, key: String): ResolvedString?
}
