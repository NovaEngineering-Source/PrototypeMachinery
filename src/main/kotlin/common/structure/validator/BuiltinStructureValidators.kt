package github.kasuminova.prototypemachinery.common.structure.validator

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.machine.structure.logic.StructureValidatorRegistry
import net.minecraft.util.ResourceLocation

/**
 * Built-in [StructureValidator] registrations.
 *
 * 内置结构验证器注册。
 */
public object BuiltinStructureValidators {

    /** Register all built-in validators. Safe to call multiple times. */
    public fun registerAll(replace: Boolean = false) {
        // Dimension gates
        StructureValidatorRegistry.register(id("overworld_only"), { ctx, _ ->
            val world = ctx.machine.blockEntity.world ?: return@register false
            world.provider.dimension == 0
        }, replace = replace)

        StructureValidatorRegistry.register(id("nether_only"), { ctx, _ ->
            val world = ctx.machine.blockEntity.world ?: return@register false
            world.provider.dimension == -1
        }, replace = replace)

        StructureValidatorRegistry.register(id("end_only"), { ctx, _ ->
            val world = ctx.machine.blockEntity.world ?: return@register false
            world.provider.dimension == 1
        }, replace = replace)

        // Time / weather gates
        StructureValidatorRegistry.register(id("day_only"), { ctx, _ ->
            val world = ctx.machine.blockEntity.world ?: return@register false
            world.isDaytime
        }, replace = replace)

        StructureValidatorRegistry.register(id("night_only"), { ctx, _ ->
            val world = ctx.machine.blockEntity.world ?: return@register false
            !world.isDaytime
        }, replace = replace)

        StructureValidatorRegistry.register(id("clear_weather_only"), { ctx, _ ->
            val world = ctx.machine.blockEntity.world ?: return@register false
            !world.isRaining && !world.isThundering
        }, replace = replace)

        // Configurable validators (JSON params)
        StructureValidatorRegistry.register(id("tile_nbt"), { TileEntityNbtStructureValidator() }, replace = replace)
    }

    private fun id(path: String): ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, path)
}
