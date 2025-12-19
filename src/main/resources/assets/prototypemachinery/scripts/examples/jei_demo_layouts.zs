#loader crafttweaker reloadable

/*
 * PrototypeMachinery - JEI Demo Layouts
 * PrototypeMachinery - JEI 演示布局（多机器/多风格）
 *
 * 配套文件：
 * - preinit:  jei_demo_machine_registration.zs   （注册 3 台 demo 机器）
 * - reloadable: jei_demo_recipes.zs             （注册多 IO 配方）
 * - reloadable: jei_demo_layouts.zs             （本文件：注册 JEI layout）
 */

import mods.prototypemachinery.jei.PMJEI;
import mods.prototypemachinery.jei.LayoutRegistry;
import mods.prototypemachinery.jei.FixedSlotProviders;

// 3 台 demo 机器
val MACHINE_A = "prototypemachinery:jei_demo_processor_a";
val MACHINE_B = "prototypemachinery:jei_demo_processor_b";
val MACHINE_C = "prototypemachinery:jei_demo_processor_c";

// 常用 type / variant / decorator
val TYPE_ITEM = "prototypemachinery:item";
val TYPE_FLUID = "prototypemachinery:fluid";
val TYPE_ENERGY = "prototypemachinery:energy";

val VAR_ITEM_PLAID = "prototypemachinery:item/plaid";

val VAR_FLUID_1X1 = "prototypemachinery:fluid/1x1";
val VAR_FLUID_1X2 = "prototypemachinery:fluid/1x2";
val VAR_FLUID_1X3 = "prototypemachinery:fluid/1x3";

val VAR_ENERGY_1X1 = "prototypemachinery:energy/1x1";
val VAR_ENERGY_1X2 = "prototypemachinery:energy/1x2";
val VAR_ENERGY_1X3 = "prototypemachinery:energy/1x3";

val DECOR_PROGRESS_MODULE = "prototypemachinery:decorator/progress_module";
val DECOR_DURATION = "prototypemachinery:decorator/recipe_duration";


// ============================================================================
// 机器 A：基础多 IO（左右 IO + 中央能量 + 标准进度）
// ============================================================================
val layoutA = PMJEI.createLayoutSized(176, 98)
    .setBackgroundNineSlice("jei_base.png")

    // 顶部：物品 IO
    .placeFirstWithVariant(TYPE_ITEM, "INPUT", 6, 6, VAR_ITEM_PLAID)
    .placeFirstWithVariant(TYPE_ITEM, "OUTPUT", 176 - 6 - 18, 6, VAR_ITEM_PLAID)

    // 中部：流体 IO + 能量
    .placeFirstWithVariant(TYPE_FLUID, "INPUT", 28, 6, VAR_FLUID_1X3)
    .placeFirstWithVariant(TYPE_FLUID, "OUTPUT", 176 - 28 - 18, 6, VAR_FLUID_1X3)
    .placeFirstWithVariant(TYPE_ENERGY, "INPUT", (176 - 18) / 2, 6, VAR_ENERGY_1X3)

    // 底部：进度 + 耗时
    .addDecoratorWithData(
        DECOR_PROGRESS_MODULE,
        (176 - 18) / 2, 6 + 54 + 4,
        {
            "type": "right",
            "direction": "RIGHT"
        }
    )
    .addDecoratorWithData(
        DECOR_DURATION,
        (176 - 70) / 2, 6 + 54 + 4 + 20,
        {
            "width": 70,
            "height": 10,
            "align": "CENTER",
            "template": "{ticks}t ({seconds}s)",
            "shadow": true
        }
    )
;

LayoutRegistry.register(MACHINE_A, layoutA);


// ============================================================================
// 机器 B：强调 Per-tick（分区：常规 IO vs *_PER_TICK）
// ============================================================================
val layoutB = PMJEI.createLayoutSized(176, 114)
    .setBackgroundNineSlice("jei_base.png")

    // 顶部：一次性输入输出
    .placeFirstWithVariant(TYPE_ITEM, "INPUT", 6, 6, VAR_ITEM_PLAID)
    .placeFirstWithVariant(TYPE_ITEM, "OUTPUT", 176 - 6 - 18, 6, VAR_ITEM_PLAID)

    .placeFirstWithVariant(TYPE_FLUID, "INPUT", 28, 6, VAR_FLUID_1X3)
    .placeFirstWithVariant(TYPE_FLUID, "OUTPUT", 176 - 28 - 18, 6, VAR_FLUID_1X3)

    // 顶部中间：能量 per-tick（这里故意用 1x3 让它更显眼）
    .placeFirstWithVariant(TYPE_ENERGY, "INPUT_PER_TICK", (176 - 18) / 2, 6, VAR_ENERGY_1X3)

    // 底部：*_PER_TICK 区域（用 1x1 小模块）
    .placeFirstWithVariant(TYPE_FLUID, "INPUT_PER_TICK", 28, 6 + 54 + 8, VAR_FLUID_1X1)
    .placeFirstWithVariant(TYPE_FLUID, "OUTPUT_PER_TICK", 176 - 28 - 18, 6 + 54 + 8, VAR_FLUID_1X1)

    // 进度：用 heat（纯展示不同风格）
    .addDecoratorWithData(
        DECOR_PROGRESS_MODULE,
        (176 - 18) / 2, 6 + 54 + 8 + 22,
        {
            "type": "heat",
            "direction": "DOWN"
        }
    )
    .addDecoratorWithData(
        DECOR_DURATION,
        (176 - 70) / 2, 6 + 54 + 8 + 22 + 20,
        {
            "width": 70,
            "height": 10,
            "align": "CENTER",
            "template": "{ticks}t ({seconds}s)",
            "shadow": true
        }
    )
;

LayoutRegistry.register(MACHINE_B, layoutB);


// ============================================================================
// 机器 C：多物品输入输出 + 固定催化剂槽位（Fixed Slot Provider）
// ============================================================================
val CATALYST_PROVIDER = "prototypemachinery:jei_demo/catalyst";

// reloadable：先清理避免重复注册
FixedSlotProviders.clear(CATALYST_PROVIDER);
FixedSlotProviders.registerItems(
    CATALYST_PROVIDER,
    [<minecraft:diamond> * 1, <minecraft:emerald> * 1],
    true
);

val layoutC = PMJEI.createLayoutSized(176, 100)
    .setBackgroundNineSlice("jei_base.png")

    // 上方：2 个物品输入 / 2 个物品输出
    .placeGridWithVariant(TYPE_ITEM, "INPUT", 6, 6, 2, 1, 18, 18, VAR_ITEM_PLAID)
    .placeGridWithVariant(TYPE_ITEM, "OUTPUT", 176 - 6 - 36, 6, 2, 1, 18, 18, VAR_ITEM_PLAID)

    // 中部：流体输入/输出 + 能量（这里用 1x2，展示“高度不同的 module”）
    .placeFirstWithVariant(TYPE_FLUID, "INPUT", 6, 28, VAR_FLUID_1X2)
    .placeFirstWithVariant(TYPE_FLUID, "OUTPUT", 176 - 6 - 18, 28, VAR_FLUID_1X2)
    .placeFirstWithVariant(TYPE_ENERGY, "INPUT", (176 - 18) / 2, 28, VAR_ENERGY_1X2)

    // 固定催化剂槽位：左下角（不属于配方 requirement，但会作为 JEI 槽位展示）
    .placeFixedSlot(CATALYST_PROVIDER, "CATALYST", 6, 100 - 6 - 18, 18, 18)

    // 进度：用 merge（纯展示不同风格）
    .addDecoratorWithData(
        DECOR_PROGRESS_MODULE,
        (176 - 18) / 2, 100 - 6 - 18,
        {
            "type": "merge",
            "direction": "RIGHT"
        }
    )
;

LayoutRegistry.register(MACHINE_C, layoutC);
