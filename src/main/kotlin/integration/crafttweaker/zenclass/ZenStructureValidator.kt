package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass

import crafttweaker.annotations.ZenRegister
import stanhebben.zenscript.annotations.ZenClass

/**
 * Functional interface for ZenScript custom structure validators.
 *
 * This allows addon developers to register custom validation logic
 * via ZenScript without creating a new validator type.
 *
 * 用于 ZenScript 自定义结构验证器的函数接口。
 *
 * 允许整合包开发者通过 ZenScript 注册自定义验证逻辑，
 * 而无需创建新的验证器类型。
 *
 * ## Usage Example / 使用示例
 *
 * ```zenscript
 * # Register a validator that only allows structures in biomes with "forest" in the name
 * mods.prototypemachinery.StructureValidatorRegistry.register(
 *     "myvalidators:forest_only",
 *     function(ctx as mods.prototypemachinery.StructureMatchContext) {
 *         return ctx.biome.name.contains("forest");
 *     }
 * );
 *
 * # Register a height validator (Y between 0 and 64)
 * mods.prototypemachinery.StructureValidatorRegistry.register(
 *     "myvalidators:height_0_64",
 *     function(ctx as mods.prototypemachinery.StructureMatchContext) {
 *         return ctx.y >= 0 && ctx.y <= 64;
 *     }
 * );
 * ```
 */
@ZenClass("mods.prototypemachinery.StructureValidator")
@ZenRegister
public fun interface ZenStructureValidator {

    /**
     * Validate structure placement.
     *
     * 验证结构放置。
     *
     * @param context The ZenScript-friendly structure match context
     * @return true if validation passes, false otherwise
     */
    public fun validate(context: ZenStructureMatchContext): Boolean

}
