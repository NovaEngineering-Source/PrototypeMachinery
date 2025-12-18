#loader preinit

/*
 * PrototypeMachinery - Full Example: structure + machine + recipe
 * PrototypeMachinery - 完整示例：结构 + 机器 + 配方
 *
 * Where to put this script:
 * - scripts/prototypemachinery/recipe_processor_full_example.zs
 *
 * Structure JSON required:
 * - config/prototypemachinery/structures/examples/recipe_processor_machine.json
 *   (This file is shipped in the mod assets and will be copied on first run
 *    if your config/prototypemachinery/structures/ directory is empty.)
 *
 * What this demo does:
 * - Registers a machine type with FactoryRecipeProcessorComponent
 * - Configures recipeGroups so scanning is enabled
 * - Registers a simple recipe that always runs (parallelism-only requirement)
 *
 * In-game quick test:
 * 1) Place the controller block for this machine (created by the machine type).
 * 2) Build the structure:
 *    - 1x iron block at (+1, 0, 0) relative to controller
 *    - 1x redstone block at (0, +1, 0) relative to controller
 * 3) When formed, the recipe processor will auto-start the recipe and loop it.
 * 4) Open the GUI and watch the recipe progress list.
 */

import mods.prototypemachinery.MachineRegistry;
import mods.prototypemachinery.recipe.PMRecipeBuilder;
import mods.prototypemachinery.recipe.PMRecipeRequirement;

// -------------------------
// Machine registration
// -------------------------

val GROUP = "prototypemachinery:example_group";

val m = MachineRegistry.create("prototypemachinery", "example_recipe_processor");
m.name("Example Recipe Processor");

// Structure is loaded from config/prototypemachinery/structures/*.json
// 推荐：通过 structureId 延迟解析
m.structure("example_recipe_processor_machine");

// Enable recipe scanning (mandatory)
m.addRecipeGroup(GROUP);

// Add the processor component via bracket handler
// 支持：<pmcomponent:factory_recipe_processor>
// 也支持：<machinecomponent:prototypemachinery:factory_recipe_processor>
m.addComponentType(<pmcomponent:factory_recipe_processor>);

MachineRegistry.register(m);

// -------------------------
// Recipe registration
// -------------------------

// This recipe has a duration and a parallelism requirement only.
// It does not require item/fluid/energy containers, so it is ideal for a minimal demo.
PMRecipeBuilder.create("prototypemachinery:example_parallel_recipe", 80)
    .addRecipeGroup(GROUP)
    .addRequirement(PMRecipeRequirement.parallelism("p", 1))
    .register();
