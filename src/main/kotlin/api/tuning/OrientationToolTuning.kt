package github.kasuminova.prototypemachinery.api.tuning

/**
 * Tuning values for the controller orientation tool (wrench).
 *
 * These values live in API to avoid hard dependency on Forge config classes.
 * They are updated by [github.kasuminova.prototypemachinery.common.config.PrototypeMachineryCommonConfig].
 */
public object OrientationToolTuning {

    /** Max durability of the wrench item. */
    @JvmField
    public var maxDurability: Int = 1024

    /** Enchantability, same scale as vanilla tools. */
    @JvmField
    public var enchantability: Int = 14

    /** Attack damage when enabled (controller rotation mode). */
    @JvmField
    public var attackDamageEnabled: Double = 20.0

    /** Attack damage when disabled (iron multi-tool mode). */
    @JvmField
    public var attackDamageDisabled: Double = 5.0

    /** Attack speed modifier (vanilla style, e.g. -2.4). */
    @JvmField
    public var attackSpeed: Double = -2.4

    /** Durability cost per successful controller rotate when enabled. */
    @JvmField
    public var durabilityCostRotateEnabled: Int = 5

    /** Durability cost per hit when enabled. */
    @JvmField
    public var durabilityCostAttackEnabled: Int = 5

    /** Durability cost per hit when disabled. */
    @JvmField
    public var durabilityCostAttackDisabled: Int = 1

    /** Durability cost per block broken when enabled. */
    @JvmField
    public var durabilityCostBreakEnabled: Int = 5

    /** Durability cost per block broken when disabled. */
    @JvmField
    public var durabilityCostBreakDisabled: Int = 1
}
