package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass

import crafttweaker.annotations.ZenRegister
import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.machine.structure.logic.StructureValidator
import github.kasuminova.prototypemachinery.api.machine.structure.logic.StructureValidatorRegistry
import github.kasuminova.prototypemachinery.common.util.warnWithBlockEntity
import net.minecraft.util.ResourceLocation
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

/**
 * ZenScript API for structure validator registry.
 *
 * Provides methods to register custom structure validators from ZenScript.
 *
 * 结构验证器注册表的 ZenScript API。
 *
 * 提供从 ZenScript 注册自定义结构验证器的方法。
 *
 * ## Usage Example / 使用示例
 *
 * ```zenscript
 * # Register a validator that only allows structures in biomes with "forest" in name
 * mods.prototypemachinery.StructureValidatorRegistry.register(
 *     "myvalidators:forest_only",
 *     function(ctx) {
 *         return ctx.biome.getName().contains("forest");
 *     }
 * );
 * ```
 */
@ZenClass("mods.prototypemachinery.StructureValidatorRegistry")
@ZenRegister
public object ZenStructureValidatorRegistry {

    /**
     * Register a custom structure validator.
     *
     * 注册自定义结构验证器。
     *
     * @param id The unique ID for this validator (e.g., "mymod:my_validator")
     * @param validator A function that takes (context) and returns true if validation passes
     * @return void
     */
    @ZenMethod
    @JvmStatic
    public fun register(id: String, validator: ZenStructureValidator) {
        val resourceId = try {
            ResourceLocation(id)
        } catch (_: Throwable) {
            PrototypeMachinery.logger.warn("Invalid structure validator id: '$id' (skipped)")
            return
        }

        // Wrap Zen validator in a proxy that catches exceptions
        val wrappedValidator = StructureValidator { ctx, offset ->
            try {
                val zenContext = ZenStructureMatchContext(ctx, offset)
                validator.validate(zenContext)
            } catch (e: Exception) {
                // Log error but don't crash validation
                val blockEntity = ctx.machine.blockEntity
                PrototypeMachinery.logger.warnWithBlockEntity(
                    "Structure validator '$id' threw exception during validation: ${e.message}",
                    blockEntity,
                    e
                )
                false
            }
        }

        // Register the wrapped validator
        StructureValidatorRegistry.register(resourceId, wrappedValidator, replace = true)
        PrototypeMachinery.logger.info("Registered ZenScript structure validator: $id")
    }
}
