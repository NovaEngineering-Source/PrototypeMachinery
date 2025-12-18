#loader crafttweaker reloadable

/*
 * PrototypeMachinery - JEI Layout Example
 * 自定义 JEI 配方布局示例
 *
 * Place this file in: scripts/prototypemachinery/
 * 将此文件放置在：scripts/prototypemachinery/
 *
 * ---------------------------------------------------------------------------
 * 使用说明 / How to use
 * ---------------------------------------------------------------------------
 * 1) 把 MACHINE_ID 改成你自己的 machine type id。
 * 2) 选择下面某个“示例块”，取消注释并修改参数。
 * 3) 规则是按顺序执行的；同一类节点可多次 placeGrid/placeAllLinear 来分区摆放。
 *
 * 重要：物品/流体的“Index”在内部是 0 开始。
 * - 物品输入 nodeId 格式："<componentId>:input:<index>"（index=0..n-1）
 * - 物品输出 nodeId 格式："<componentId>:output:<index>"
 */

import mods.prototypemachinery.jei.PMJEI;
import mods.prototypemachinery.jei.LayoutRegistry;
import mods.prototypemachinery.jei.FixedSlotProviders;

// 你的 machine type id（示例）
val MACHINE_ID = "prototypemachinery:example_recipe_processor";

// 常用常量（写起来短一点）
val TYPE_ITEM = "prototypemachinery:item";
val TYPE_FLUID = "prototypemachinery:fluid";
val TYPE_ENERGY = "prototypemachinery:energy";

val VAR_ITEM_18 = "prototypemachinery:slot/18";
val VAR_TANK_16x58 = "prototypemachinery:tank/16x58";
val VAR_TANK_16x34 = "prototypemachinery:tank/16x34";

val DECOR_PROGRESS = "prototypemachinery:decorator/progress";
val DECOR_DURATION = "prototypemachinery:decorator/recipe_duration";

// ============================================================================
// 示例 1：常规布局（多列物品 + 纵向 tank + 进度/耗时）
// ============================================================================
val layout = PMJEI.createLayoutSized(176, 96)

    // 左侧：输入物品 2x3
    .placeGridWithVariant(
        TYPE_ITEM, "INPUT",
        6, 6,
        2, 3,
        18, 18,
        VAR_ITEM_18
    )

    // 右侧：输出物品 2x3
    .placeGridWithVariant(
        TYPE_ITEM, "OUTPUT",
        176 - 6 - 2 * 18 - 2, 6,
        2, 3,
        18, 18,
        VAR_ITEM_18
    )

    // 中间：输入流体（纵向 1 列）
    .placeAllLinearWithVariant(
        TYPE_FLUID, "INPUT",
        80, 6,
        0, 60,
        VAR_TANK_16x58
    )

    // 中间：输出流体（纵向 1 列）
    .placeAllLinearWithVariant(
        TYPE_FLUID, "OUTPUT",
        100, 6,
        0, 60,
        VAR_TANK_16x58
    )

    // 进度箭头
    .addDecoratorWithData(
        DECOR_PROGRESS,
        (176 - 20) / 2, 40,
        {
            "style": "arrow",
            "direction": "RIGHT",
            // "cycleTicks": 200
        }
    )

    // 耗时文本
    .addDecoratorWithData(
        DECOR_DURATION,
        (176 - 80) / 2, 62,
        {
            "width": 80,
            "height": 10,
            "align": "CENTER",
            "template": "{ticks} t ({seconds}s)",
            "shadow": true
        }
    )

    // 可选：把“未显式摆放”的 nodes 自动摆放（用于 addon requirement）
    // .autoPlaceRemainingWithSpacing(6, 6, 2, 2, 4)
;

LayoutRegistry.register(MACHINE_ID, layout);


// ============================================================================
// 示例 2：把多个物品输入分布在不同区域
// 目标：输入 1/2/3 在上排，4/5/6 在下排
// 说明：多次 placeGrid 会自动跳过已摆放的节点（默认 skipPlaced=true）
// ============================================================================
// val layout_split_inputs = PMJEI.createLayoutSized(176, 96)
//     // 上排：先放 3 个 INPUT
//     .placeGridWithVariant(TYPE_ITEM, "INPUT", 6, 6, 3, 1, 18, 18, VAR_ITEM_18)
//     // 下排：再放剩余的 INPUT（会从第 4 个开始）
//     .placeGridWithVariant(TYPE_ITEM, "INPUT", 6, 6 + 18 + 2, 3, 1, 18, 18, VAR_ITEM_18)
// ;
// LayoutRegistry.register(MACHINE_ID, layout_split_inputs);


// ============================================================================
// 示例 3：精确指定某个 index 的位置（nodeId 定位）
// 场景：你想把特定输入（比如第 4 个）放到一个“特殊位置”，或打乱顺序
//
// 注意：nodeId 前缀里的 <componentId> 是你创建 ItemRequirementComponent(id=...) 时传入的 id。
// 例如你配方里 item requirement 的 id 叫 "items"，则输入索引为：
// - 第 1 个输入 => items:input:0
// - 第 6 个输入 => items:input:5
// ============================================================================
// val ITEM_COMPONENT_ID = "items";
// val layout_by_node_id = PMJEI.createLayoutSized(176, 96)
//     .placeNodeWithVariant(ITEM_COMPONENT_ID + ":input:0", 6, 6, VAR_ITEM_18)
//     .placeNodeWithVariant(ITEM_COMPONENT_ID + ":input:1", 26, 6, VAR_ITEM_18)
//     .placeNodeWithVariant(ITEM_COMPONENT_ID + ":input:2", 46, 6, VAR_ITEM_18)
//
//     .placeNodeWithVariant(ITEM_COMPONENT_ID + ":input:3", 6, 28, VAR_ITEM_18)
//     .placeNodeWithVariant(ITEM_COMPONENT_ID + ":input:4", 26, 28, VAR_ITEM_18)
//     .placeNodeWithVariant(ITEM_COMPONENT_ID + ":input:5", 46, 28, VAR_ITEM_18)
// ;
// LayoutRegistry.register(MACHINE_ID, layout_by_node_id);


// ============================================================================
// 示例 4：条件化（动态显示）的特殊用法
// 场景：当 INPUT 流体数量 >= 2 时，才额外摆放一个“小 tank”（或显示某个装饰器）
// ============================================================================
// val layout_conditional = PMJEI.createLayoutSized(176, 96)
//
//     // 默认先放 1 个大 tank
//     .placeFirstWithVariant(TYPE_FLUID, "INPUT", 80, 6, VAR_TANK_16x58)
//
//     // 当 input 流体 >= 2：再放一个小 tank（这条规则只有条件满足时才执行）
//     .whenCountAtLeast(TYPE_FLUID, "INPUT", 2)
//         .placeAllLinearWithVariant(TYPE_FLUID, "INPUT", 80, 6 + 58 + 2, 0, 0, VAR_TANK_16x34)
//         .then()
//
//     // 当 input 流体 >= 2：再显示一个循环动画作为提示
//     .whenCountAtLeast(TYPE_FLUID, "INPUT", 2)
//         .addDecoratorWithData(DECOR_PROGRESS, 8, 80, {"style": "cycle", "direction": "CIRCULAR_CW"})
//         .then()
// ;
// LayoutRegistry.register(MACHINE_ID, layout_conditional);


// ============================================================================
// 示例 5：Per-tick 流体（INPUT_PER_TICK / OUTPUT_PER_TICK）
// 说明：这些 role 对应 FluidRequirementComponent.inputsPerTick/outputsPerTick
// ============================================================================
// val layout_per_tick = PMJEI.createLayoutSized(176, 96)
//     // 常规输入输出
//     .placeAllLinearWithVariant(TYPE_FLUID, "INPUT", 80, 6, 0, 60, VAR_TANK_16x58)
//     .placeAllLinearWithVariant(TYPE_FLUID, "OUTPUT", 100, 6, 0, 60, VAR_TANK_16x58)
//
//     // per-tick：小 tank 放底部
//     .placeAllLinearWithVariant(TYPE_FLUID, "INPUT_PER_TICK", 80, 96 - 6 - 34, 0, 0, VAR_TANK_16x34)
//     .placeAllLinearWithVariant(TYPE_FLUID, "OUTPUT_PER_TICK", 100, 96 - 6 - 34, 0, 0, VAR_TANK_16x34)
// ;
// LayoutRegistry.register(MACHINE_ID, layout_per_tick);


// ============================================================================
// 示例 6：把 INPUT/OUTPUT 与 *_PER_TICK 视作“同组”摆放
// 场景：你不想分别写 INPUT + INPUT_PER_TICK（输出同理），而是希望一条规则就摆完。
// 注意：这会让 role="INPUT" 的规则也会匹配 INPUT_PER_TICK；如果你需要分开摆放，请不要开启此开关。
// ============================================================================
// val layout_merge_per_tick_roles = PMJEI.createLayoutSized(176, 96)
//     .mergePerTickRoles(true)
//
//     // 这里会摆放：INPUT + INPUT_PER_TICK
//     .placeAllLinearWithVariant(TYPE_FLUID, "INPUT", 80, 6, 0, 60, VAR_TANK_16x58)
//
//     // 这里会摆放：OUTPUT + OUTPUT_PER_TICK
//     .placeAllLinearWithVariant(TYPE_FLUID, "OUTPUT", 100, 6, 0, 60, VAR_TANK_16x58)
// ;
// LayoutRegistry.register(MACHINE_ID, layout_merge_per_tick_roles);


// ============================================================================
// 示例 7：固定值槽位（Fixed Slot）
// 场景：你想在 JEI 配方页里展示“催化剂/模具/工具提示”，但它不属于配方 requirement。
//
// 工作方式：
// 1) 先注册一个 providerId -> 固定显示值（物品/流体）
// 2) 在布局里 placeFixedSlot(providerId, ...) 放置一个“真 JEI 槽位”
//
// 提示：如果你在 reloadable 脚本里重复运行注册逻辑，建议先 clear(providerId) 避免重复。
// ============================================================================
// val CATALYST_PROVIDER = "prototypemachinery:example/catalyst";
//
// // 推荐：reloadable 脚本先清理再注册
// FixedSlotProviders.clear(CATALYST_PROVIDER);
//
// // 注册一个“固定物品 provider”，提供多个显示值（JEI 会轮播/聚焦这些值）
// FixedSlotProviders.registerItems(
//     CATALYST_PROVIDER,
//     [<minecraft:diamond> * 1, <minecraft:emerald> * 1] as crafttweaker.api.item.IItemStack[],
//     true
// );
//
// val layout_fixed_slot = PMJEI.createLayoutSized(176, 96)
//     // 放置一个固定槽位（默认 role=CATALYST）
//     .placeFixedSlot(CATALYST_PROVIDER, "CATALYST", 6, 96 - 6 - 18, 18, 18)
//
//     // 你也可以把固定槽位当作 INPUT/OUTPUT 视觉元素（role 只影响 JEI group 的 isInput）
//     // .placeFixedSlot(CATALYST_PROVIDER, "INPUT", 6, 6, 18, 18)
// ;
//
// LayoutRegistry.register(MACHINE_ID, layout_fixed_slot);
