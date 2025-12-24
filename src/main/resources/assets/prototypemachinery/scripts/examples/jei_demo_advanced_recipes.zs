#loader crafttweaker reloadable

/*
 * PrototypeMachinery - JEI Advanced Requirement Demo Recipes
 * PrototypeMachinery - JEI 高级需求语义演示配方
 *
 * 覆盖点：
 * - item/fluid 的 chance + chanceAttribute（tooltip 文案）
 * - fuzzy_inputs：候选 alternatives + “锁定一种输入”的语义提示
 * - random_outputs：候选 alternatives + pickCount + 权重提示
 */

import mods.prototypemachinery.recipe.PMRecipeBuilder;
import mods.prototypemachinery.recipe.PMRecipeRequirement;
import mods.prototypemachinery.recipe.ItemMatchers;

val GROUP = "prototypemachinery:jei_demo/group_adv";

// ============================================================================
// Dynamic matcher demo (for JEI preview + runtime)
// ============================================================================
// 放在 reloadable：避免 preinit 阶段环境/类型未就绪，并支持 /ct reload。
// 规则：如果 pattern 上有 foo(int)，则要求 candidate 的 foo 与其相等。
ItemMatchers.register("prototypemachinery:jei_demo/foo_eq", function(candidate, pattern) {
    if (ItemMatchers.itemId(candidate) != ItemMatchers.itemId(pattern)) return false;
    if (!ItemMatchers.hasNbtInt(pattern, "foo")) return true;
    if (!ItemMatchers.hasNbtInt(candidate, "foo")) return false;
    return ItemMatchers.nbtInt(candidate, "foo", 0) == ItemMatchers.nbtInt(pattern, "foo", 0);
});

// ============================================================================
// Chance（物品/流体）
// ============================================================================

// 50% 概率消耗铁锭（用于验证：输入槽 tooltip 的 Chance 行）
PMRecipeBuilder.create("prototypemachinery:jei_demo/adv_item_input_chance", 60)
    .addRecipeGroup(GROUP)
    .addRequirement(PMRecipeRequirement.itemInputChance("in", <minecraft:iron_ingot> * 1, 50))
    .addRequirement(PMRecipeRequirement.itemOutput("out", <minecraft:gold_nugget> * 2))
    .addRequirement(PMRecipeRequirement.parallelism("p", 1))
    .register();

// 25% 概率产出钻石，且受属性影响（用于验证：chanceAttribute 行）
PMRecipeBuilder.create("prototypemachinery:jei_demo/adv_item_output_chance_attr", 80)
    .addRecipeGroup(GROUP)
    .addRequirement(PMRecipeRequirement.itemInput("in", <minecraft:coal> * 1))
    .addRequirement(PMRecipeRequirement.itemOutputChanceWithAttribute(
        "out",
        <minecraft:diamond> * 1,
        25,
        "prototypemachinery:process_speed"
    ))
    .addRequirement(PMRecipeRequirement.parallelism("p", 1))
    .register();

// 30% 概率消耗 250mB 水（用于验证：流体输入槽 tooltip 的 Chance 行）
PMRecipeBuilder.create("prototypemachinery:jei_demo/adv_fluid_input_chance", 60)
    .addRecipeGroup(GROUP)
    .addRequirement(PMRecipeRequirement.fluidInputChance("fin", <liquid:water> * 250, 30))
    .addRequirement(PMRecipeRequirement.itemOutput("out", <minecraft:clay_ball> * 1))
    .addRequirement(PMRecipeRequirement.parallelism("p", 1))
    .register();

// ============================================================================
// Fuzzy Inputs（物品/流体）
// ============================================================================

// 模糊物品输入：从候选中锁定一种，需求 2 个
PMRecipeBuilder.create("prototypemachinery:jei_demo/adv_item_fuzzy_input", 100)
    .addRecipeGroup(GROUP)
    .addRequirement(PMRecipeRequirement.itemFuzzyInput(
        "fuzzy_item",
        2,
        [<minecraft:iron_ingot> * 1, <minecraft:gold_ingot> * 1, <minecraft:diamond> * 1]
    ))
    .addRequirement(PMRecipeRequirement.itemOutput("out", <minecraft:emerald> * 1))
    .addRequirement(PMRecipeRequirement.parallelism("p", 1))
    .register();

// oreDict 模糊输入（语法糖）：候选由 OreDictionary 在加载时展开。
// 用于验证：JEI 候选展示（alternatives/expanded）与 tooltip 的 fuzzy 语义。
PMRecipeBuilder.create("prototypemachinery:jei_demo/adv_item_fuzzy_input_oredict", 100)
    .addRecipeGroup(GROUP)
    .addRequirement(PMRecipeRequirement.itemOreDictFuzzyInput(
        "fuzzy_ore",
        2,
        "ingotIron"
    ))
    .addRequirement(PMRecipeRequirement.itemOutput("out", <minecraft:emerald> * 1))
    .addRequirement(PMRecipeRequirement.parallelism("p", 1))
    .register();

// 动态 NBT 匹配输入：候选来自端口枚举 + matcher 过滤 + 锁定。
// 这里显式给出 displayedCandidates，确保 JEI 可预览“候选”长什么样。
// matcherId 的实现注册在 preinit：jei_demo_advanced_machine_registration.zs
PMRecipeBuilder.create("prototypemachinery:jei_demo/adv_item_dynamic_input_nbt", 100)
    .addRecipeGroup(GROUP)
    .addRequirement(PMRecipeRequirement.itemDynamicInputWithDisplayed(
        "dyn_item",
        1,
        <minecraft:diamond>.withTag({foo: 1}),
        "prototypemachinery:jei_demo/foo_eq",
        [<minecraft:diamond>.withTag({foo: 1}), <minecraft:diamond>.withTag({foo: 2})]
    ))
    .addRequirement(PMRecipeRequirement.itemOutput("out", <minecraft:gold_nugget> * 8))
    .addRequirement(PMRecipeRequirement.parallelism("p", 1))
    .register();

// 模糊流体输入：从候选中锁定一种，需求 250mB
PMRecipeBuilder.create("prototypemachinery:jei_demo/adv_fluid_fuzzy_input", 100)
    .addRecipeGroup(GROUP)
    .addRequirement(PMRecipeRequirement.fluidFuzzyInput(
        "fuzzy_fluid",
        250,
        [<liquid:water> * 1, <liquid:lava> * 1]
    ))
    .addRequirement(PMRecipeRequirement.itemOutput("out", <minecraft:obsidian> * 1))
    .addRequirement(PMRecipeRequirement.parallelism("p", 1))
    .register();

// ============================================================================
// Random Outputs（物品/流体）
// ============================================================================

// 随机物品输出：从候选中抽取 2 个（无放回），并带权重
PMRecipeBuilder.create("prototypemachinery:jei_demo/adv_item_random_output", 120)
    .addRecipeGroup(GROUP)
    .addRequirement(PMRecipeRequirement.itemInput("in", <minecraft:stick> * 1))
    .addRequirement(PMRecipeRequirement.itemRandomOutput(
        "rand_item",
        2,
        [<minecraft:coal> * 1, <minecraft:iron_ingot> * 1, <minecraft:diamond> * 1],
        [80, 15, 5]
    ))
    .addRequirement(PMRecipeRequirement.parallelism("p", 1))
    .register();

// 随机流体输出：从候选中抽取 1 个（无放回），并带权重
PMRecipeBuilder.create("prototypemachinery:jei_demo/adv_fluid_random_output", 120)
    .addRecipeGroup(GROUP)
    .addRequirement(PMRecipeRequirement.itemInput("in", <minecraft:cobblestone> * 1))
    .addRequirement(PMRecipeRequirement.fluidRandomOutput(
        "rand_fluid",
        1,
        [<liquid:water> * 250, <liquid:lava> * 250],
        [70, 30]
    ))
    .addRequirement(PMRecipeRequirement.parallelism("p", 1))
    .register();
