package github.kasuminova.prototypemachinery.integration.jei

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI
import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.api.recipe.MachineRecipe
import github.kasuminova.prototypemachinery.integration.jei.builtin.PMJeiBuiltins
import github.kasuminova.prototypemachinery.integration.jei.builtin.ingredient.EnergyJeiIngredient
import github.kasuminova.prototypemachinery.integration.jei.builtin.ingredient.EnergyJeiIngredientHelper
import github.kasuminova.prototypemachinery.integration.jei.builtin.ingredient.EnergyJeiIngredientListRenderer
import github.kasuminova.prototypemachinery.integration.jei.builtin.ingredient.EnergyJeiType
import github.kasuminova.prototypemachinery.integration.jei.category.PMMachineRecipeCategory
import github.kasuminova.prototypemachinery.integration.jei.layout.DefaultJeiMachineLayout
import github.kasuminova.prototypemachinery.integration.jei.layout.ExampleRecipeProcessorHatchesJeiLayout
import github.kasuminova.prototypemachinery.integration.jei.registry.JeiMachineLayoutRegistry
import github.kasuminova.prototypemachinery.integration.jei.runtime.JeiEmbeddedModularUiInputBridge
import github.kasuminova.prototypemachinery.integration.jei.util.JeiControllerStack
import github.kasuminova.prototypemachinery.integration.jei.wrapper.PMMachineRecipeWrapper
import github.kasuminova.prototypemachinery.integration.jei.wrapper.PMStructurePreviewWrapper
import mezz.jei.api.IModPlugin
import mezz.jei.api.IModRegistry
import mezz.jei.api.JEIPlugin
import mezz.jei.api.ingredients.IModIngredientRegistration
import mezz.jei.api.recipe.IRecipeCategoryRegistration
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

/**
 * HadEnoughItems/JEI plugin entrypoint.
 */
@JEIPlugin
@SideOnly(Side.CLIENT)
public class PMJeiPlugin : IModPlugin {

    override fun registerIngredients(registry: IModIngredientRegistration) {
        // Register built-ins early so addon authors can rely on types/kinds being present.
        PMJeiBuiltins.ensureRegistered()

        // Custom energy ingredient: makes recipes searchable by energy IO.
        // We also provide 4 virtual browse entries so players can click them in the ingredient list:
        // - 消耗(一次性) / 消耗(每tick) / 产出(一次性) / 产出(每tick)
        // IMPORTANT: use the IIngredientType overload so JEI keys the ingredient registry by *our* EnergyJeiType instance.
        // Otherwise, registering by class will create a different internal ingredient type instance, and recipe wrappers
        // that write ingredients using EnergyJeiType will crash with "Unknown ingredient type".
        registry.register(
            EnergyJeiType,
            listOf(
                EnergyJeiIngredient.consumeOnce(1),
                EnergyJeiIngredient.consumePerTick(1),
                EnergyJeiIngredient.produceOnce(1),
                EnergyJeiIngredient.producePerTick(1),
            ),
            EnergyJeiIngredientHelper,
            EnergyJeiIngredientListRenderer,
        )
    }

    override fun registerCategories(registration: IRecipeCategoryRegistration) {
        // Register built-in requirement renderers/decorators.
        PMJeiBuiltins.ensureRegistered()

        // JEI 1.12 has no mouse callbacks on recipe categories; enable Forge GUI input bridge.
        JeiEmbeddedModularUiInputBridge.register()

        // Ensure a default layout exists to avoid spam and make the system functional even without per-machine overrides.
        if (JeiMachineLayoutRegistry.getDefault() == null) {
            JeiMachineLayoutRegistry.setDefault(DefaultJeiMachineLayout)
        }

        // Ship one machine-specific layout example for documentation/testing.
        // Do NOT override if modpack already registered a layout.
        JeiMachineLayoutRegistry.register(
            ResourceLocation(PrototypeMachinery.MOD_ID, "example_recipe_processor_hatches"),
            ExampleRecipeProcessorHatchesJeiLayout,
            replace = false,
        )

        val guiHelper = registration.jeiHelpers.guiHelper

        // One JEI category per MachineType (+ one shared structure preview category).
        registration.addRecipeCategories(github.kasuminova.prototypemachinery.integration.jei.category.PMStructurePreviewCategory(guiHelper))

        for (machineType in PrototypeMachineryAPI.machineTypeRegistry.all()) {
            if (!shouldExposeCategory(machineType)) continue
            registration.addRecipeCategories(PMMachineRecipeCategory(machineType, guiHelper))
        }
    }

    override fun register(registry: IModRegistry) {
        val recipeManager = PrototypeMachineryAPI.recipeManager

        // Shared structure preview recipes: one virtual recipe per machine type.
        run {
            val previewUid = github.kasuminova.prototypemachinery.integration.jei.category.PMStructurePreviewCategory.UID
            val wrappers = PrototypeMachineryAPI.machineTypeRegistry.all()
                .filter { shouldExposeCategory(it) }
                .map { PMStructurePreviewWrapper(it, it.structure.id) }

            if (wrappers.isNotEmpty()) {
                registry.addRecipes(wrappers, previewUid)
            }

            for (machineType in PrototypeMachineryAPI.machineTypeRegistry.all()) {
                if (!shouldExposeCategory(machineType)) continue
                // Also expose structure preview under the controller catalyst.
                JeiControllerStack.get(machineType)?.let { stack ->
                    registry.addRecipeCatalyst(stack, previewUid)
                }
            }
        }

        for (machineType in PrototypeMachineryAPI.machineTypeRegistry.all()) {
            if (!shouldExposeCategory(machineType)) continue

            val uid = PMMachineRecipeCategory.uidFor(machineType.id)

            // Link controller block -> JEI category via recipe catalyst.
            // This enables: hover controller in inventory and press JEI 'recipe' key to open this category.
            JeiControllerStack.get(machineType)?.let { stack ->
                registry.addRecipeCatalyst(stack, uid)
            }

            val recipes: Collection<MachineRecipe> = if (machineType.recipeGroups.isEmpty()) {
                // Align with RecipeIndexRegistry: empty groups -> no recipes.
                // This avoids accidentally exposing "everything" when groups are not configured.
                emptyList()
            } else {
                recipeManager.getByGroups(machineType.recipeGroups)
            }

            if (recipes.isEmpty()) continue

            val wrappers = recipes.map { PMMachineRecipeWrapper(machineType, it) }
            registry.addRecipes(wrappers, uid)
        }

        PrototypeMachinery.logger.info("JEI: registered PrototypeMachinery recipe categories and recipes")
    }

    private fun shouldExposeCategory(machineType: MachineType): Boolean {
        // For now, expose all machine types.
        // Later we can hide special/debug machine types or those explicitly opted-out.
        return true
    }
}
