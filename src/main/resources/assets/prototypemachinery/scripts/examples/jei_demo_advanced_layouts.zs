#loader crafttweaker reloadable

/*
 * PrototypeMachinery - JEI Advanced Layout
 * PrototypeMachinery - JEI 高级语义演示布局
 *
 * 说明：
 * - 本布局刻意开启 autoPlaceRemaining，确保 fuzzy/random 派生出来的额外节点也能显示。
 * - 适合配合 jei_demo_advanced_recipes.zs 一起使用。
 */

import mods.prototypemachinery.jei.PMJEI;
import mods.prototypemachinery.jei.LayoutRegistry;

// 概率角标（slot 左上角）与 fuzzy/random 的候选展示方式，
// 现在推荐在 Layout 里配置（只影响这个 machine 的 JEI layout），而不是全局开关。

val MACHINE = "prototypemachinery:jei_demo_processor_adv";

val TYPE_ITEM = "prototypemachinery:item";
val TYPE_FLUID = "prototypemachinery:fluid";
val TYPE_ENERGY = "prototypemachinery:energy";

val VAR_ITEM_PLAID = "prototypemachinery:item/plaid";
val VAR_FLUID_1X1 = "prototypemachinery:fluid/1x1";
val VAR_ENERGY_1X3 = "prototypemachinery:energy/1x3";

val DECOR_PROGRESS_MODULE = "prototypemachinery:decorator/progress_module";
val DECOR_DURATION = "prototypemachinery:decorator/recipe_duration";

val W = 176;
val H = 124;

val layout = PMJEI.createLayoutSized(W, H)
    // layout-scope：显式开启概率角标（不写则跟随全局默认）
    .setChanceOverlayEnabled(true)
    // layout-scope：候选展示模式（不写则跟随全局默认：alternatives）
    .setCandidateSlotRenderMode("alternatives")
    .setBackgroundNineSlice("jei_base.png")

    // 左侧：输入（包含 fuzzy_inputs 派生的 INPUT 节点也会优先落到这里）
    .placeGridWithVariant(TYPE_ITEM, "INPUT", 6, 6, 2, 2, 18, 18, VAR_ITEM_PLAID)

    // 右侧：输出（包含 random_outputs 派生的 OUTPUT 节点也会优先落到这里）
    .placeGridWithVariant(TYPE_ITEM, "OUTPUT", W - 6 - 36, 6, 2, 2, 18, 18, VAR_ITEM_PLAID)

    // 中间：能量（给个 1x3 让它显眼一些）
    .placeFirstWithVariant(TYPE_ENERGY, "INPUT", (W - 18) / 2, 6, VAR_ENERGY_1X3)

    // 底部两角：流体 input/output（用于观察流体 fuzzy/random 的候选轮播 + tooltip）
    .placeFirstWithVariant(TYPE_FLUID, "INPUT", 6, 6 + 40, VAR_FLUID_1X1)
    .placeFirstWithVariant(TYPE_FLUID, "OUTPUT", W - 6 - 18, 6 + 40, VAR_FLUID_1X1)

    // 进度 + 耗时
    .addDecoratorWithData(
        DECOR_PROGRESS_MODULE,
        (W - 18) / 2, 6 + 54 + 4,
        {
            "type": "right",
            "direction": "RIGHT"
        }
    )
    .addDecoratorWithData(
        DECOR_DURATION,
        (W - 80) / 2, 6 + 54 + 4 + 20,
        {
            "width": 80,
            "height": 10,
            "align": "CENTER",
            "template": "{ticks}t ({seconds}s)",
            "shadow": true
        }
    )

    // 关键：把没有显式 place 的节点自动摆放出来（比如 fuzzy/random 额外节点）
    .autoPlaceRemainingWithSpacing(6, 6 + 40 + 22, 2, 2, 2)
;

LayoutRegistry.register(MACHINE, layout);
