#loader preinit

/*
 * PrototypeMachinery - JEI Demo Machines (multi IO)
 * PrototypeMachinery - JEI 演示机器（多输入输出）
 *
 * 目标：提供多台“可直接跑配方、可在 JEI 中展示不同布局风格”的示例机器。
 *
 * 结构：使用 assets 内置结构模板（首次启动会复制到 config 目录）
 * - example_recipe_processor_machine_io_demo
 *   含：物品输入/输出仓、流体输入/输出仓、能量输入/输出仓
 */

import mods.prototypemachinery.MachineRegistry;

// ----------------------------------------------------------------------------
// 机器 A：基础多 IO（左右输入输出 + 中间能量）
// ----------------------------------------------------------------------------
val GROUP_A = "prototypemachinery:jei_demo/group_a";

val mA = MachineRegistry.create("prototypemachinery", "jei_demo_processor_a");
mA.name("JEI Demo Processor A (IO + Energy)");
mA.structure("example_recipe_processor_machine_io_demo");
mA.addRecipeGroup(GROUP_A);
mA.addComponentType(<pmcomponent:factory_recipe_processor>);
MachineRegistry.register(mA);


// ----------------------------------------------------------------------------
// 机器 B：包含 per-tick 示例（用于演示 mergePerTickRoles / 分区摆放）
// ----------------------------------------------------------------------------
val GROUP_B = "prototypemachinery:jei_demo/group_b";

val mB = MachineRegistry.create("prototypemachinery", "jei_demo_processor_b");
mB.name("JEI Demo Processor B (Per-tick)");
mB.structure("example_recipe_processor_machine_io_demo");
mB.addRecipeGroup(GROUP_B);
mB.addComponentType(<pmcomponent:factory_recipe_processor>);
MachineRegistry.register(mB);


// ----------------------------------------------------------------------------
// 机器 C：多物品输入输出 + 固定催化剂槽位展示（Fixed Slot Provider）
// ----------------------------------------------------------------------------
val GROUP_C = "prototypemachinery:jei_demo/group_c";

val mC = MachineRegistry.create("prototypemachinery", "jei_demo_processor_c");
mC.name("JEI Demo Processor C (Catalyst UI)");
mC.structure("example_recipe_processor_machine_io_demo");
mC.addRecipeGroup(GROUP_C);
mC.addComponentType(<pmcomponent:factory_recipe_processor>);
MachineRegistry.register(mC);
