package integration.jei

import github.kasuminova.prototypemachinery.integration.jei.builtin.PMJeiIcons
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class JeiRecipeIconsExistenceTest {

    @Test
    fun `all referenced jei recipeicons exist on classpath`() {
        // Background
        assertRecipeIconExists("jei_base.png")

        // Item module
        assertRecipeIconExists("item_module/plaid_base.png")

        // Plaid module (used by ItemRequirementJeiRenderer)
        listOf(
            "plaid_module/base_top_0101.png",
            "plaid_module/base_top_0111.png",
            "plaid_module/base_top_0110.png",
            "plaid_module/base_mid_1101.png",
            "plaid_module/base_mid_1111.png",
            "plaid_module/base_mid_1110.png",
            "plaid_module/base_down_1001.png",
            "plaid_module/base_down_1011.png",
            "plaid_module/base_down_1010.png",

            "plaid_module/0000.png",
            "plaid_module/1010.png",
            "plaid_module/1000.png",
            "plaid_module/1001.png",
            "plaid_module/0001.png",
            "plaid_module/0010.png",
            "plaid_module/0110.png",
            "plaid_module/0100.png",
            "plaid_module/0101.png",
        ).forEach(::assertRecipeIconExists)

        // Fluid module variants (used by FluidRequirementJeiRenderer)
        PMJeiIcons.ALL_VARIANTS.values
            .filter { it.id.path.startsWith("fluid/") }
            .forEach { variant ->
                val key = when (variant.id) {
                    PMJeiIcons.FLUID_PLAID_1X1 -> "1_1_plaid"
                    else -> variant.id.path.removePrefix("fluid/")
                        .replace("x", "_")
                }

                // 0o5_1 is currently shipped under gas_module/.
                val moduleDir = if (key == "0o5_1") "gas_module" else "fluid_module"

                assertRecipeIconExists("$moduleDir/${key}_base.png")
                assertRecipeIconExists("$moduleDir/${key}_top.png")
            }

        // Energy module variants (used by EnergyRequirementJeiRenderer)
        PMJeiIcons.ALL_VARIANTS.values
            .filter { it.id.path.startsWith("energy/") }
            .forEach { variant ->
                val suffix = variant.id.path.removePrefix("energy/")
                val key = when (suffix) {
                    "default" -> "default_in"
                    else -> suffix.replace("x", "_")
                }

                assertRecipeIconExists("energy_module/${key}_empty.png")
                assertRecipeIconExists("energy_module/${key}_full.png")
            }

        // Progress module textures (used by ProgressModuleJeiDecorator)
        listOf(
            // Simple base/run pairs
            "right",
            "left",
            "up",
            "down",
            "compress",
            "cut",
            "merge",
            "split",
            "rolling",
        ).forEach { type ->
            assertRecipeIconExists("progress_module/${type}_base.png")
            assertRecipeIconExists("progress_module/${type}_run.png")
        }

        // Heat/Cool special cases
        assertRecipeIconExists("progress_module/heat_base.png")
        assertRecipeIconExists("progress_module/heat_run_0.png")
        assertRecipeIconExists("progress_module/heat_run_1.png")
    }

    private fun assertRecipeIconExists(relPath: String) {
        val fullPath = "assets/prototypemachinery/textures/gui/jei_recipeicons/$relPath"
        val url = javaClass.classLoader.getResource(fullPath)
        assertNotNull(url, "Missing recipe icon resource on classpath: $fullPath")
    }
}
