#loader preinit

/*
 * PrototypeMachinery - JEI Advanced Requirement Demo Machines
 * PrototypeMachinery - JEI 高级需求语义演示机器
 *
 * 目标：用于测试 JEI 中对以下语义的展示效果：
 * - chance / chanceAttribute
 * - fuzzy_inputs（模糊输入候选）
 * - random_outputs（随机输出池 + 权重 + pickCount）
 *
 * 配套文件：
 * - reloadable: jei_demo_advanced_recipes.zs
 * - reloadable: jei_demo_advanced_layouts.zs
 */

import mods.prototypemachinery.MachineRegistry;

val GROUP_ADV = "prototypemachinery:jei_demo/group_adv";

val m = MachineRegistry.create("prototypemachinery", "jei_demo_processor_adv");
m.name("JEI Demo Processor ADV (Chance/Fuzzy/Random)");

// 使用 assets 内置结构模板（首次启动会复制到 config 目录）
m.structure("example_recipe_processor_machine_io_demo");

m.addRecipeGroup(GROUP_ADV);
m.addComponentType(<pmcomponent:factory_recipe_processor>);

MachineRegistry.register(m);

// 额外新机器：用于让多个 JEI layout 示例“共存”，互不覆盖。
// 它加入同一个 recipe group，因此会展示同一组演示配方。
val mExpanded = MachineRegistry.create("prototypemachinery", "jei_demo_processor_adv_expanded");
mExpanded.name("JEI Demo Processor ADV (Expanded Candidates)");

// 复用同一套结构模板
mExpanded.structure("example_recipe_processor_machine_io_demo");

mExpanded.addRecipeGroup(GROUP_ADV);
mExpanded.addComponentType(<pmcomponent:factory_recipe_processor>);

MachineRegistry.register(mExpanded);
