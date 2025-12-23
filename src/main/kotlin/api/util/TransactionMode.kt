package github.kasuminova.prototypemachinery.api.util

/**
 * Transaction mode for operations that can either simulate (no side effects) or execute (apply side effects).
 *
 * 表示事务操作模式：
 * - [SIMULATE]：仅模拟，不产生副作用。
 * - [EXECUTE]：真实执行，产生副作用。
 */
public enum class TransactionMode {
    SIMULATE,
    EXECUTE
}
