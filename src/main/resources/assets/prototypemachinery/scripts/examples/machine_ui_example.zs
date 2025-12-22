#loader crafttweaker reloadable

/*
 * PrototypeMachinery - Custom Machine UI Example
 * 自定义机器 UI 示例
 *
 * This script demonstrates how to create custom machine GUIs using the
 * PMUI (PrototypeMachinery UI) API via ZenScript.
 * 此脚本演示如何使用 PMUI (PrototypeMachinery UI) API 通过 ZenScript 创建自定义机器 GUI。
 *
 * Place this file in: scripts/prototypemachinery/
 * 将此文件放置在：scripts/prototypemachinery/
 */

import mods.prototypemachinery.ui.PMUI;
import mods.prototypemachinery.ui.UIRegistry;
import mods.prototypemachinery.ui.UIBindings;


// ============================================================================
// UI Components Demo
// UI 组件展示（使用 gui_states 预定义控件模板，开箱即用）
// ============================================================================

// 注意：机器本体注册已迁移到 machine_registration.zs（preinit）。
// 此脚本使用 reloadable loader，便于在重载脚本时仅刷新 UI。
val MACHINE_ID = "prototypemachinery:ui_components_demo";

// ============================================================================
// Tabs / 多标签页
//
// Tab A: 默认“原版机械 UI”布局（参考 DefaultMachineUI.kt）
// Tab B: gui_states 组件演示（原本内容整体迁移到第二页）
//
// 说明：
// - 这里使用 PMUI.tabContainer（运行时 TabContainerDefinition），用于脚本侧展示多页面。
// - DefaultMachineUI.kt 里原生 UI 用的是 ModularUI 的 PagedWidget + PageButton。
//   TabContainer 的视觉实现是“最小实现”，但布局/坐标可以对齐默认 UI。
// ============================================================================

// Root panel（不设背景；背景由每个 Tab 的 panel 自己提供）
val rootPanel = PMUI.createPanel()
    .setSize(384, 256);

// ------------------------------
// Tab A: Default Machine UI
// ------------------------------
val tabA = PMUI.createPanel()
    .setSize(384, 256)
    .setBackground("prototypemachinery:gui/gui_controller_a");

// FactoryRecipeProcessor: 配方进度列表（默认 syncKey = factoryRecipeProgress）
// - DefaultMachineUI 仅在机器含 FactoryRecipeProcessorComponent 时才会提供数据；
//   没有该组件时此列表会显示为空（这是预期行为）。
tabA.addChild(
    PMUI.recipeProgressList()
        .setPos(27, 8)
        .setSize(89, 238)
);

// 右侧内容区（简化版）：展示默认内置 binding keys
tabA.addChild(
    PMUI.dynamicText("machine_name")
        .setPos(140, 12)
        .setSize(236, 12)
        .setColor(0xFFFFFF)
        .setShadow(true)
);

tabA.addChild(
    PMUI.dynamicText("machine_id")
        .setPos(140, 26)
        .setSize(236, 10)
        .setColor(0xA0A0A0)
        .setShadow(false)
        .setFormat("ID: %s")
);

// 这些 key 来自 DefaultMachineUIBindings（无自定义脚本也可用）：formed / active / uptime / attr:...
tabA.addChild(
    PMUI.dynamicText("str(formed)")
        .setPos(140, 40)
        .setSize(236, 10)
        .setColor(0x808080)
        .setShadow(false)
        .setFormat("Formed: %s")
);
tabA.addChild(
    PMUI.dynamicText("str(active)")
        .setPos(140, 52)
        .setSize(236, 10)
        .setColor(0x808080)
        .setShadow(false)
        .setFormat("Active: %s")
);

// 左下：4x4 机器槽位区（DefaultMachineUI: pos(138, 173), size(72, 72)）
// 注意：ItemSlotGroup 的槽位间距/背景与原生 PMSmoothGrid 不完全相同，但用途一致。
tabA.addChild(
    PMUI.itemSlotGroup("default", 0, 4, 4)
        .setPos(138, 173)
);

// 右下：玩家物品栏（DefaultMachineUI: pos(215, 171)）
tabA.addChild(PMUI.text("Player Inventory:").setPos(215, 160).setColor(0x404040).setShadow(false));
tabA.addChild(
    PMUI.playerInventory()
        .setPos(215, 171)
);

// ------------------------------
// Tab B: UI Components Demo (gui_states)
// ------------------------------
val demoPanel = PMUI.createPanel()
    .setSize(384, 256)
    .setBackground("prototypemachinery:gui/gui_controller_b");

// 创建 TabContainer 并挂到 root
val tabContainer = PMUI.tabContainer()
    .setPos(0, 0)
    .setSize(384, 256)
    .setTabPosition("LEFT")
    .addTab(PMUI.tab("A", "Default").setContent(tabA))
    .addTab(PMUI.tab("B", "Components").setContent(demoPanel));

rootPanel.addChild(tabContainer);

// Text / 文本
// 小提示：Text / DynamicText 在布局容器（row/column/grid）里如果不设置 height，
// 可能会出现“下一行 y 不推进 -> 文本重叠/换行爆炸”的效果。
// 因此本示例里，凡是放进 column/grid 的文本，我们都会显式 setSize(..., 10~12)。

demoPanel.addChild(
    PMUI.text("UI Components Demo")
        .setPos(120, 8)
        .setSize(240, 12)
        .setAlignment("CENTER")
        .setColor(0x404040)
        .setShadow(false)
);

demoPanel.addChild(
    PMUI.text("gui_states templates + conditions")
        .setPos(120, 19)
        .setSize(240, 10)
        .setAlignment("CENTER")
        .setColor(0x707070)
        .setShadow(false)
);

// Row / 行布局（childPadding 由 setSpacing 控制）
val buttonRow = PMUI.row()
    .setPos(30, 34)
    .setSize(240, 22)
    .setSpacing(3);

// gui_states: empty button / expand button / switch
buttonRow.addChild(PMUI.emptyButton());
buttonRow.addChild(PMUI.emptyButtonShadow());

// 使用模板皮肤 + 文本按钮：你也可以直接对普通 button 设置 skin
buttonRow.addChild(
    PMUI.button("OK")
        .setSize(60, 19)
        .setSkin("gui_states/expand_button/normal")
        .onClick("demo_ok")
);

buttonRow.addChild(
    PMUI.button("Cancel")
        .setSize(60, 19)
        .setSkin("gui_states/expand_button/shadow")
        .onClick("demo_cancel")
);

// enabledIf 示例：switch 关闭时按钮不可用（仍可见）。
buttonRow.addChild(
    PMUI.conditional(
        PMUI.button("Needs ON")
            .setSize(70, 19)
            .setSkin("gui_states/expand_button/normal")
            .onClick("demo_needs_on")
    ).setEnabledIf("demo_switch")
);

demoPanel.addChild(buttonRow);

// Column / 列布局
val infoColumn = PMUI.column()
    .setSize(160, 80)
    .setPos(30, 58)
    .setSpacing(2);

infoColumn.addChild(
    PMUI.text("Sections")
        .setSize(160, 12)
        .setColor(0x404040)
        .setShadow(false)
);
infoColumn.addChild(PMUI.text("- Button / Toggle").setSize(160, 10).setColor(0x555555).setShadow(false));
infoColumn.addChild(PMUI.text("- Slider / Progress").setSize(160, 10).setColor(0x555555).setShadow(false));
infoColumn.addChild(PMUI.text("- Conditions (visible/enabled)").setSize(160, 10).setColor(0x555555).setShadow(false));

demoPanel.addChild(infoColumn);

// Switch / 开关按钮（gui_states）
// - bindState 已接入数据绑定：若该 key 可写（RW），客户端交互将写回服务端并同步更新
val toggleRow = PMUI.row()
    .setPos(210, 44)
    .setSize(160, 22)
    .setSpacing(4);

toggleRow.addChild(
    PMUI.switchButton()
        // 绑定脚本注册的自定义 key：demo_switch（RW）
        .bindState("demo_switch")
);

toggleRow.addChild(
    PMUI.switchButtonShadow()
        .bindState("demo_switch")
);

// Toggle + label 示例：用 textOn/textOff 叠字（方便演示“同一控件”不同状态）
toggleRow.addChild(
    PMUI.toggleButton()
        .setSize(56, 14)
        .setSkin("gui_states/switch/normal")
        .setTextOn("ON")
        .setTextOff("OFF")
        .bindState("demo_switch")
);

demoPanel.addChild(toggleRow);

// visibleIf 示例：switch 打开时才显示这一行（仍可拖 UI / 仍会参与布局）。
demoPanel.addChild(
    PMUI.conditional(
        PMUI.text("visibleIf: demo_switch = TRUE")
            .setPos(210, 66)
            .setSize(160, 10)
            .setColor(0x3b82f6)
            .setShadow(false)
    ).setVisibleIf("demo_switch")
);

// Slider / 滑块（gui_states）
// - 示例：将 1..100 的值写回服务端，并在同一 UI 中驱动 progressbar 同步更新
demoPanel.addChild(
    PMUI.sliderMXExpand()
        .setPos(210, 92)
        .setSize(160, 13)
        // 绑定脚本注册的自定义 key：demo_slider（RW）
        .setRange(1.0, 100.0)
        .setStep(1.0)
        .bindValue("demo_slider")
);

// DynamicText / 动态文本：显示当前 slider 值
demoPanel.addChild(
    // str(<doubleKey>)：把 double binding 转成字符串（用于 DynamicText）
    PMUI.dynamicText("str(demo_slider)")
        .setPos(210, 108)
        .setSize(160, 10)
        .setColor(0x404040)
        .setShadow(false)
        .setFormat("Value: %s")
);

// 更多 slider 皮肤对比（同一 valueKey，随便拖一个都会同步）
demoPanel.addChild(
    PMUI.sliderSXExpandShadow()
        .setPos(210, 122)
        .setSize(160, 10)
        .setRange(1.0, 100.0)
        .setStep(1.0)
        .bindValue("demo_slider")
);

// ProgressBar / 进度条
// - setTexture 省略或传 null/""：使用默认主题/默认贴图（若你有模板化进度条，后续可在这里扩展）
// - bindProgress 已接入只读数据绑定：服务端 getter -> 客户端显示
demoPanel.addChild(
    PMUI.progressBar()
        .setPos(210, 136)
        .setSize(160, 12)
        .setDirection("RIGHT")
        .setTexture(null)
        // 进度条期望 0..1
        // norm(<srcKey>;<min>;<max>)：把 double binding 归一化到 0..1
        .bindProgress("norm(demo_slider;1;100)")
);

// Inputs / 输入（放在中间列，避免与默认 PlayerInventory 位置重叠）
demoPanel.addChild(
    PMUI.text("Inputs")
    .setPos(210, 152)
    .setSize(160, 12)
        .setColor(0x404040)
        .setShadow(false)
);

// Input box / 输入框（gui_states）
demoPanel.addChild(
    PMUI.inputBox()
    .setPos(210, 166)
    .setSize(160, 13)
        .bindValue("demo_text")
);

demoPanel.addChild(
    PMUI.inputBoxExpandShadow()
    .setPos(210, 182)
    .setSize(160, 13)
        .bindValue("demo_text")
);

// Numeric text field / 数值输入框（long），同样使用 gui_states 输入框皮肤
demoPanel.addChild(
    PMUI.textField()
    .setPos(210, 198)
    .setSize(160, 13)
        .setSkin("gui_states/input_box/shadow")
        .setInputType("long")
        .setLongRange(0, 9999)
        .bindValue("demo_long")
);

// DynamicText / 动态文本
// - 已接入：通过 StringSyncValue 同步字符串，然后在客户端动态渲染
// - 当前实现按“字符串值”进行 String.format(pattern, rawString)
//   因此推荐使用 %s；如果 pattern 不匹配会回退为原始字符串
demoPanel.addChild(
    // 内置 string key 示例：machine_name / machine_id
    PMUI.dynamicText("machine_name")
    .setPos(210, 216)
    .setSize(160, 10)
        .setColor(0xFFAA00)
        .setShadow(true)
        .setFormat("Machine: %s")
);

// Player inventory / 玩家物品栏
// （Tab B）按需求移除玩家物品栏，避免挤占演示空间。

// Image / 图片（示例：显示 gui_states 贴图）
demoPanel.addChild(
    PMUI.image("prototypemachinery:gui/gui_states/empty_button/normal/default_n")
    .setPos(364, 8)
    .setSize(16, 16)
);

// 将 UI 注册到独立注册表：可被外部脚本/模组覆盖（按优先级）。
// reloadable 可能被多次执行：先清理旧注册，避免列表累积。
UIRegistry.clear(MACHINE_ID);

// 同理：bindings 也先清理旧注册，避免脚本 reload 后覆盖/累积。
UIBindings.clear(MACHINE_ID);

// 绑定：bool / double / string
UIBindings.registerBoolData(MACHINE_ID, "demo_switch", "demo_switch_value", false);
UIBindings.registerStringData(MACHINE_ID, "demo_text", "demo_text_value", "Hello gui_states!");
UIBindings.registerStringData(MACHINE_ID, "demo_long", "demo_long_value", "42");

// 注册一个"自定义数据值"（存到 machine 的 ZSDataComponent），并提供一个 UI 可写的 binding key。
// Slider 会把值写回服务端 -> 服务端保存/同步 -> ProgressBar 与 DynamicText 会自动跟随更新。
UIBindings.registerDoubleDataClamped(MACHINE_ID, "demo_slider", "demo_slider_value", 1.0, 1.0, 100.0);

// 注册 UI（rootPanel 内包含 TabContainer + 两个 Tab 内容）
UIRegistry.register(MACHINE_ID, rootPanel);


//
// 备注：
// - gui_states 模板控件可直接用 PMUI 的模板方法（emptyButton/expandButton/switchButton/slider*/inputBox）。
// - 也可以对普通控件调用 setSkin("gui_states/..." ) 来应用模板皮肤。
// - 输入框（TextField）通过 UIBindings.registerStringData 绑定字符串；inputType="long" 时会在客户端做数值校验。
//
