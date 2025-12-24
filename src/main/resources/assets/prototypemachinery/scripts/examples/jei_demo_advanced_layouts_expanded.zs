#loader crafttweaker reloadable

/*
 * PrototypeMachinery - JEI Advanced Layout (Expanded Candidates)
 * PrototypeMachinery - JEI 高级语义演示布局（候选拆分为多个 Slot）
 *
 * 说明：
 * - 本示例演示“layout-scope”配置：
 *   - setCandidateSlotRenderMode("expanded")：将 fuzzy_inputs / random_outputs 的候选拆成 N 个 slot
 *   - setChanceOverlayEnabled(true/false)：仅对该 layout 生效的概率角标开关
 * - 本布局注册到一个独立的演示机器：prototypemachinery:jei_demo_processor_adv_expanded
 *   因此可与 jei_demo_advanced_layouts.zs 共存，无需互相覆盖。
 */

import mods.prototypemachinery.jei.PMJEI;
import mods.prototypemachinery.jei.LayoutRegistry;

val MACHINE = "prototypemachinery:jei_demo_processor_adv_expanded";

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
    .setBackgroundNineSlice("jei_base.png")

    // layout-scope：候选拆分为 N 个 Slot（每个 slot 固定渲染一个候选）
    .setCandidateSlotRenderMode("expanded")

    // layout-scope：概率角标（左上角）
    .setChanceOverlayEnabled(true)

    // 左侧：输入（expanded 后 fuzzy_inputs 会拆成更多 INPUT 节点）
    .placeGridWithVariant(TYPE_ITEM, "INPUT", 6, 6, 2, 2, 18, 18, VAR_ITEM_PLAID)

    // 右侧：输出（expanded 后 random_outputs 会拆成更多 OUTPUT 节点）
    .placeGridWithVariant(TYPE_ITEM, "OUTPUT", W - 6 - 36, 6, 2, 2, 18, 18, VAR_ITEM_PLAID)

    // 中间：能量
    .placeFirstWithVariant(TYPE_ENERGY, "INPUT", (W - 18) / 2, 6, VAR_ENERGY_1X3)

    // 底部两角：流体 input/output
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

    // 把没有显式 place 的节点自动摆放出来（expanded 模式下更容易超出，建议保留）
    .autoPlaceRemainingWithSpacing(6, 6 + 40 + 22, 2, 2, 2)
;

LayoutRegistry.register(MACHINE, layout);
