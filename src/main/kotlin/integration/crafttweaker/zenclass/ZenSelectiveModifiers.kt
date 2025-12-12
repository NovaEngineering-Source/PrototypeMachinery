package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass

import crafttweaker.annotations.ZenRegister
import github.kasuminova.prototypemachinery.api.recipe.selective.SelectiveModifierRegistry
import org.jetbrains.annotations.ApiStatus
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

/**
 * ZenScript entry for selective modifier registration.
 * ZenScript 选择性修改器注册入口。
 */
@ZenClass("mods.prototypemachinery.SelectiveModifiers")
@ApiStatus.Experimental
@ZenRegister
public class ZenSelectiveModifiers private constructor() {

    public companion object {

        /**
         * Register a selective modifier.
         *
         * Usage:
         * ```zenscript
         * import mods.prototypemachinery.SelectiveModifiers;
         *
         * SelectiveModifiers.register("my_speedup", function(ctx) {
         *     ctx.mulProcessSpeed("speed", 1.25);
         * });
         * ```
         */
        @JvmStatic
        @ZenMethod
        public fun register(id: String, modifier: ZenSelectiveModifier) {
            SelectiveModifierRegistry.register(id) { ctx ->
                modifier.apply(ZenSelectiveContext(ctx))
            }
        }

        /** Convenience: always multiply process speed by a factor. */
        @JvmStatic
        @ZenMethod
        public fun registerProcessSpeedMultiplier(id: String, modifierId: String, factor: Double) {
            SelectiveModifierRegistry.register(id) { ctx ->
                ZenSelectiveContext(ctx).mulProcessSpeed(modifierId, factor)
            }
        }

    }
}

/**
 * Functional interface for ZenScript lambdas.
 * 用于接收 ZenScript lambda 的函数式接口。
 */
public fun interface ZenSelectiveModifier {
    public fun apply(ctx: ZenSelectiveContext)
}
