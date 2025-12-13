package github.kasuminova.prototypemachinery.api.ui.binding

/**
 * UI data bindings exposed by a [github.kasuminova.prototypemachinery.api.machine.MachineInstance].
 *
 * - 只读：当前阶段仅用于服务端->客户端显示同步；客户端不会写回。
 * - 通过 key 获取一个 getter（Supplier），用于创建 ModularUI 的 SyncValue。
 */
public interface MachineUIBindings {

    /**
     * @return 该 key 的布尔 getter；null 表示未提供该 key。
     */
    public fun booleanGetter(key: String): (() -> Boolean)? = null

    /**
     * @return 该 key 的数值 getter（Double）；null 表示未提供该 key。
     */
    public fun doubleGetter(key: String): (() -> Double)? = null

    /**
     * @return 该 key 的字符串 getter；null 表示未提供该 key。
     */
    public fun stringGetter(key: String): (() -> String)? = null

    public companion object {
        /**
         * 默认空实现：不提供任何 key。
         */
        public val EMPTY: MachineUIBindings = object : MachineUIBindings {}
    }
}
