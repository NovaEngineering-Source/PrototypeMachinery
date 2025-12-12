package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass

import crafttweaker.annotations.ZenRegister
import github.kasuminova.prototypemachinery.api.machine.attribute.StandardMachineAttributes
import github.kasuminova.prototypemachinery.api.recipe.selective.SelectiveContext
import github.kasuminova.prototypemachinery.impl.machine.attribute.MachineAttributeModifierImpl
import org.jetbrains.annotations.ApiStatus
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

/**
 * ZenScript-facing wrapper for [SelectiveContext].
 * 面向 ZenScript 的 SelectiveContext 包装。
 */
@ZenClass("mods.prototypemachinery.SelectiveContext")
@ApiStatus.Experimental
@ZenRegister
public class ZenSelectiveContext internal constructor(
    private val ctx: SelectiveContext,
) {

    /** Selective id in recipe. / 配方内选择性 id */
    @ZenMethod
    public fun id(): String = ctx.selectionId

    /**
     * Add an additive modifier to process speed.
     * 对进程速度添加加法修改。
     */
    @ZenMethod
    public fun addProcessSpeed(modifierId: String, amount: Double) {
        ctx.putProcessAttributeModifier(
            StandardMachineAttributes.PROCESS_SPEED,
            MachineAttributeModifierImpl.addition(modifierId, amount)
        )
    }

    /**
     * Multiply process speed by a factor.
     *
     * @param factor e.g. 0.8 means slower, 1.2 means faster.
     */
    @ZenMethod
    public fun mulProcessSpeed(modifierId: String, factor: Double) {
        val delta = factor - 1.0
        ctx.putProcessAttributeModifier(
            StandardMachineAttributes.PROCESS_SPEED,
            MachineAttributeModifierImpl.multiplyTotal(modifierId, delta)
        )
    }

}
