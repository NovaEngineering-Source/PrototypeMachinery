package github.kasuminova.prototypemachinery.client.model

import github.kasuminova.prototypemachinery.PrototypeMachinery
import net.minecraft.client.renderer.block.model.IBakedModel
import net.minecraft.client.renderer.block.model.ModelResourceLocation
import net.minecraft.util.EnumFacing
import net.minecraftforge.client.event.ModelBakeEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

/**
 * Handles custom model baking for machine controllers to support twist rotation.
 *
 * Standard blockstate only supports X and Y rotations, but twist requires rotation
 * around the FACING axis. This handler wraps baked models with [TwistRotatedBakedModel]
 * to apply the correct rotation at render time.
 */
@SideOnly(Side.CLIENT)
internal object ControllerModelBakeHandler {

    // Set of model locations that should use twist rotation
    private val controllerModelLocations = mutableSetOf<String>()

    internal fun registerControllerModel(location: String) {
        controllerModelLocations.add(location)
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    internal fun onModelBake(event: ModelBakeEvent) {
        val registry = event.modelRegistry

        // Find all controller model variants and wrap them
        val toWrap = mutableMapOf<ModelResourceLocation, IBakedModel>()

        for (key in registry.keys) {
            if (key !is ModelResourceLocation) continue
            val model = registry.getObject(key) ?: continue

            // Check if this is a controller model that needs twist rotation
            val path = key.namespace + ":" + key.path
            if (path !in controllerModelLocations && !path.endsWith("_controller")) continue

            // Only wrap models with twist > 0
            val variant = key.variant
            if (!variant.contains("twist=")) continue

            val twist = extractTwist(variant)
            if (twist == 0) continue

            // This model needs twist rotation wrapping
            toWrap[key] = model
        }

        // Wrap the models
        for ((key, model) in toWrap) {
            val facing = extractFacing(key.variant)
            val twist = extractTwist(key.variant)

            // For UP/DOWN facings, the blockstate JSON already handles twist via y-rotation
            // so we don't need to apply additional rotation here
            if (facing == EnumFacing.UP || facing == EnumFacing.DOWN) {
                continue
            }

            val wrapped = createRotatedModel(model, facing, twist)
            registry.putObject(key, wrapped)

            PrototypeMachinery.logger.debug(
                "[ControllerModelBakeHandler] Wrapped model {} with twist rotation (facing={}, twist={})",
                key, facing, twist
            )
        }

        PrototypeMachinery.logger.info(
            "[ControllerModelBakeHandler] Wrapped {} controller models with twist rotation",
            toWrap.size
        )
    }

    private fun extractFacing(variant: String): EnumFacing {
        val match = Regex("facing=(\\w+)").find(variant) ?: return EnumFacing.NORTH
        return try {
            EnumFacing.valueOf(match.groupValues[1].uppercase())
        } catch (e: Exception) {
            EnumFacing.NORTH
        }
    }

    private fun extractTwist(variant: String): Int {
        val match = Regex("twist=(\\d+)").find(variant) ?: return 0
        return match.groupValues[1].toIntOrNull() ?: 0
    }

    private fun createRotatedModel(baseModel: IBakedModel, facing: EnumFacing, twist: Int): IBakedModel {
        return RotatedBakedModel(baseModel, facing, twist)
    }
}
