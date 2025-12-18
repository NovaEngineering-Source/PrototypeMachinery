#loader crafttweaker reloadable

/*
 * PrototypeMachinery - Recipes Example (with hatches)
 * PrototypeMachinery - 配方示例（带仓室）
 *
 * IMPORTANT:
 * - This file is intentionally #loader postinit because some setups can't resolve items in preinit.
 * - The machine definition is in: recipe_processor_with_hatches_example.zs (#loader preinit)
 *
 * Where to put this script:
 * - scripts/prototypemachinery/recipe_processor_with_hatches_recipes.zs
 */

import mods.prototypemachinery.recipe.PMRecipeBuilder;
import mods.prototypemachinery.recipe.PMRecipeRequirement;

val GROUP = "prototypemachinery:example_hatch_group";

// Consumes 1x iron ingot from the item INPUT hatch,
// and outputs 1x gold ingot into the item OUTPUT hatch.
PMRecipeBuilder.create("prototypemachinery:example_hatch_recipe", 100)
    .addRecipeGroup(GROUP)
    .addRequirement(PMRecipeRequirement.itemInput("in", <minecraft:iron_ingot> * 1))
    .addRequirement(PMRecipeRequirement.itemOutput("out", <minecraft:gold_ingot> * 1))
    .addRequirement(PMRecipeRequirement.parallelism("p", 1))
    .register();
