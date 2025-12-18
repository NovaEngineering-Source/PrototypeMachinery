#loader preinit

/*
 * PrototypeMachinery - Machine Example (with hatches): structure + machine
 * PrototypeMachinery - 机器示例（带仓室）：结构 + 机器
 *
 * Where to put this script:
 * - scripts/prototypemachinery/recipe_processor_with_hatches_example.zs
 *
 * NOTE / 注意：
 * - 某些环境下 CraftTweaker 在 preinit 阶段无法解析物品（<minecraft:...>）。
 * - 因此本示例已拆分：
 *   1) 本文件：#loader preinit，仅注册机器（不引用物品）
 *   2) recipe_processor_with_hatches_recipes.zs：#loader postinit，注册配方（包含物品）
 *
 * Structure JSON required:
 * - config/prototypemachinery/structures/examples/recipe_processor_machine_with_hatches.json
 *   (This file is shipped in the mod assets and will be copied on first run
 *    if your config/prototypemachinery/structures/ directory is empty.)
 *
 * What this demo does:
 * - Registers a machine type with FactoryRecipeProcessorComponent
 * - Configures recipeGroups so scanning is enabled
 *
 * Recipes for this machine are registered in:
 * - recipe_processor_with_hatches_recipes.zs (#loader postinit)
 *
 * In-game quick test:
 * 1) Place the controller block for this machine (created by the machine type).
 * 2) Build the structure:
 *    - 1x Item INPUT hatch (tier 1) at (+1, 0, 0)
 *    - 1x Item OUTPUT hatch (tier 1) at (-1, 0, 0)
 *    - 1x Iron block at (0, +1, 0)
 * 3) Ensure recipes script is loaded (postinit), then put 1x iron ingot into the INPUT hatch.
 * 4) When formed, the machine will consume iron ingots and produce gold ingots into the OUTPUT hatch.
 * 5) Open the GUI and watch the recipe progress list.
 */

import mods.prototypemachinery.MachineRegistry;

// -------------------------
// Machine registration
// -------------------------

val GROUP = "prototypemachinery:example_hatch_group";

val m = MachineRegistry.create("prototypemachinery", "example_recipe_processor_hatches");
m.name("Example Recipe Processor (Hatches)");

// Structure is loaded from config/prototypemachinery/structures/*.json
m.structure("example_recipe_processor_machine_with_hatches");

// Enable recipe scanning (mandatory)
m.addRecipeGroup(GROUP);

// Add the processor component via bracket handler
m.addComponentType(<pmcomponent:factory_recipe_processor>);

MachineRegistry.register(m);
