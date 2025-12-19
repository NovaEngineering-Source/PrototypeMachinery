#loader crafttweaker reloadable

/*
 * PrototypeMachinery - JEI Demo Recipes
 * PrototypeMachinery - JEI 演示配方（物品/流体/能量 输入输出）
 *
 * 说明：
 * - 本文件为 reloadable，便于 /mt reload 迭代。
 * - 某些环境 preinit 无法解析 <minecraft:...> / <liquid:...>，因此配方示例统一放这里。
 *
 * 对应机器：
 * - prototypemachinery:jei_demo_processor_a  (GROUP_A)
 * - prototypemachinery:jei_demo_processor_b  (GROUP_B)
 * - prototypemachinery:jei_demo_processor_c  (GROUP_C)
 */

import mods.prototypemachinery.recipe.PMRecipeBuilder;
import mods.prototypemachinery.recipe.PMRecipeRequirement;

val GROUP_A = "prototypemachinery:jei_demo/group_a";
val GROUP_B = "prototypemachinery:jei_demo/group_b";
val GROUP_C = "prototypemachinery:jei_demo/group_c";


// ============================================================================
// 机器 A：基础多 IO
// ============================================================================

// A-1: 铁 + 水 -> 金粒 + 岩浆；消耗能量
PMRecipeBuilder.create("prototypemachinery:jei_demo/a_1_refine", 120)
    .addRecipeGroup(GROUP_A)
    .addRequirement(PMRecipeRequirement.itemInput("item_in", <minecraft:iron_ingot> * 1))
    .addRequirement(PMRecipeRequirement.fluidInput("fluid_in", <liquid:water> * 1000))
    .addRequirement(PMRecipeRequirement.energy("energy", 8000, 0))

    .addRequirement(PMRecipeRequirement.itemOutput("item_out", <minecraft:gold_nugget> * 4))
    .addRequirement(PMRecipeRequirement.fluidOutput("fluid_out", <liquid:lava> * 250))

    .addRequirement(PMRecipeRequirement.parallelism("p", 1))
    .register();

// A-2: 红石 + 水 -> 粘土；产出少量能量（演示 energy 输出）
PMRecipeBuilder.create("prototypemachinery:jei_demo/a_2_wet_mix", 80)
    .addRecipeGroup(GROUP_A)
    .addRequirement(PMRecipeRequirement.itemInput("item_in", <minecraft:redstone> * 2))
    .addRequirement(PMRecipeRequirement.fluidInput("fluid_in", <liquid:water> * 250))
    .addRequirement(PMRecipeRequirement.energy("energy", 0, 500))

    .addRequirement(PMRecipeRequirement.itemOutput("item_out", <minecraft:clay_ball> * 4))

    .addRequirement(PMRecipeRequirement.parallelism("p", 1))
    .register();


// ============================================================================
// 机器 B：Per-tick 示例（持续消耗/产出）
// ============================================================================

// B-1: 每 tick 消耗少量水 + 能量，输出少量岩浆（纯演示）
PMRecipeBuilder.create("prototypemachinery:jei_demo/b_1_per_tick", 200)
    .addRecipeGroup(GROUP_B)

    // 主输入输出（一次性）
    .addRequirement(PMRecipeRequirement.itemInput("item_in", <minecraft:coal> * 1))
    .addRequirement(PMRecipeRequirement.itemOutput("item_out", <minecraft:obsidian> * 1))

    // per-tick 流体
    .addRequirement(PMRecipeRequirement.fluidInputPerTick("water_pt", <liquid:water> * 5))
    .addRequirement(PMRecipeRequirement.fluidOutputPerTick("lava_pt", <liquid:lava> * 2))

    // per-tick 能量
    .addRequirement(PMRecipeRequirement.energyPerTick("energy_pt", 40, 0))

    .addRequirement(PMRecipeRequirement.parallelism("p", 1))
    .register();

// B-2: 多流体输入输出（一次性 + per-tick 混合），用于测试 JEI 分区摆放
PMRecipeBuilder.create("prototypemachinery:jei_demo/b_2_hybrid", 160)
    .addRecipeGroup(GROUP_B)

    .addRequirement(PMRecipeRequirement.itemInput("item_in", <minecraft:sand> * 2))
    .addRequirement(PMRecipeRequirement.fluidInput("water_in", <liquid:water> * 500))
    .addRequirement(PMRecipeRequirement.fluidOutput("lava_out", <liquid:lava> * 50))

    .addRequirement(PMRecipeRequirement.fluidInputPerTick("water_pt", <liquid:water> * 3))
    .addRequirement(PMRecipeRequirement.energyPerTick("energy_pt", 25, 0))

    .addRequirement(PMRecipeRequirement.itemOutput("item_out", <minecraft:glass> * 2))

    .addRequirement(PMRecipeRequirement.parallelism("p", 1))
    .register();


// ============================================================================
// 机器 C：多物品输入输出 + 催化剂 UI
// ============================================================================

// C-1: 2 个物品输入 + 流体输入 + 能量输入 -> 2 个物品输出 + 流体输出
PMRecipeBuilder.create("prototypemachinery:jei_demo/c_1_multi_in_out", 140)
    .addRecipeGroup(GROUP_C)

    .addRequirement(PMRecipeRequirement.itemInput("in_a", <minecraft:iron_ingot> * 2))
    .addRequirement(PMRecipeRequirement.itemInput("in_b", <minecraft:gold_ingot> * 1))
    .addRequirement(PMRecipeRequirement.fluidInput("fluid_in", <liquid:water> * 750))
    .addRequirement(PMRecipeRequirement.energy("energy", 6000, 0))

    .addRequirement(PMRecipeRequirement.itemOutput("out_a", <minecraft:gold_nugget> * 9))
    .addRequirement(PMRecipeRequirement.itemOutput("out_b", <minecraft:iron_nugget> * 9))
    .addRequirement(PMRecipeRequirement.fluidOutput("fluid_out", <liquid:lava> * 100))

    .addRequirement(PMRecipeRequirement.parallelism("p", 1))
    .register();

// C-2: 单输入单输出 + 仅能量（用于对比 UI 的简洁布局）
PMRecipeBuilder.create("prototypemachinery:jei_demo/c_2_simple", 60)
    .addRecipeGroup(GROUP_C)

    .addRequirement(PMRecipeRequirement.itemInput("in", <minecraft:cobblestone> * 8))
    .addRequirement(PMRecipeRequirement.energy("energy", 1200, 0))

    .addRequirement(PMRecipeRequirement.itemOutput("out", <minecraft:stone> * 8))

    .addRequirement(PMRecipeRequirement.parallelism("p", 1))
    .register();
