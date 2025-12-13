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

// Optional: import builders if you prefer explicit types.
// 可选：如果你更喜欢显式类型，也可以 import 各 Builder。
import mods.prototypemachinery.ui.PanelBuilder;
import mods.prototypemachinery.ui.RowBuilder;
import mods.prototypemachinery.ui.ColumnBuilder;
import mods.prototypemachinery.ui.GridBuilder;
import mods.prototypemachinery.ui.ButtonBuilder;
import mods.prototypemachinery.ui.ToggleButtonBuilder;
import mods.prototypemachinery.ui.SliderBuilder;
import mods.prototypemachinery.ui.ProgressBarBuilder;
import mods.prototypemachinery.ui.TextBuilder;
import mods.prototypemachinery.ui.DynamicTextBuilder;
import mods.prototypemachinery.ui.ImageBuilder;
import mods.prototypemachinery.ui.ItemSlotBuilder;
import mods.prototypemachinery.ui.FluidSlotBuilder;
import mods.prototypemachinery.ui.ItemSlotGroupBuilder;
import mods.prototypemachinery.ui.PlayerInventoryBuilder;
import mods.prototypemachinery.ui.SpacerBuilder;
import mods.prototypemachinery.ui.TooltipAreaBuilder;


// ============================================================================
// UI Components Demo
// UI 组件展示（尽量依赖 states.png 的默认贴图/默认参数）
// ============================================================================

// 注意：机器本体注册已迁移到 machine_registration.zs（preinit）。
// 此脚本使用 reloadable loader，便于在重载脚本时仅刷新 UI。
val MACHINE_ID = "prototypemachinery:ui_components_demo";

// Panel / 面板
// - background 可省略（null/空字符串会被视为未设置），这里用常见背景方便观感。
val demoPanel = PMUI.createPanel()
    .setSize(384, 256)
    .setBackground("prototypemachinery:gui/gui_controller_a");

// Text / 文本
demoPanel.addChild(
    PMUI.text("UI Components Demo")
        .setPos(140, 9)
        .setColor(0x404040)
        .setShadow(false)
);

demoPanel.addChild(
    PMUI.text("(defaults from states.png)")
        .setPos(140, 18)
        .setColor(0x707070)
        .setShadow(false)
);

// Row / 行布局（childPadding 由 setSpacing 控制）
val buttonRow = PMUI.row()
    .setPos(8, 34)
    .setSpacing(2);

buttonRow.addChild(PMUI.toggleButton().setSize(27, 15).setTextOn("A*").setTextOff("A"));
buttonRow.addChild(PMUI.toggleButton().setSize(27, 15).setTextOn("B*").setTextOff("B"));
buttonRow.addChild(PMUI.toggleButton().setSize(27, 15).setTextOn("C*").setTextOff("C"));
buttonRow.addChild(PMUI.toggleButton().setSize(27, 15).setTextOn("D*").setTextOff("D"));

demoPanel.addChild(buttonRow);

// Column / 列布局
val infoColumn = PMUI.column()
    .setSize(120, 80)
    .setPos(140, 56)
    .setSpacing(2);

infoColumn.addChild(PMUI.text("- Button / Toggle / Slider / Progress" ).setColor(0x555555).setShadow(false));
infoColumn.addChild(PMUI.text("- Slots (placeholders for now)" ).setColor(0x555555).setShadow(false));
infoColumn.addChild(PMUI.text("- Spacer / TooltipArea" ).setColor(0x555555).setShadow(false));

demoPanel.addChild(infoColumn);

// ToggleButton / 开关按钮
// - textureOn/textureOff 可省略：会回退到 states.png 默认按钮贴图
// - bindState 已接入数据绑定：若该 key 可写（RW），客户端交互将写回服务端并同步更新
demoPanel.addChild(
    PMUI.toggleButton()
        .setPos(140, 90)
        .setSize(54, 15)
        .setTextOn("ON")
        .setTextOff("OFF")
    // 内置 key 示例：formed / active
    .bindState("formed")
);

// Slider / 滑块
// - 使用 states.png 的默认轨道/滑块把手
// - 示例：将 1..100 的值写回服务端，并在同一 UI 中驱动 progressbar 同步更新
demoPanel.addChild(
    PMUI.slider()
        .setPos(140, 108)
        .setSize(102, 14)
    // 绑定脚本注册的自定义 key：demo_slider（RW）
    .setRange(1.0, 100.0)
    .setStep(1.0)
    .bindValue("demo_slider")
);

// DynamicText / 动态文本：显示当前 slider 值
demoPanel.addChild(
    // str(<doubleKey>)：把 double binding 转成字符串（用于 DynamicText）
    PMUI.dynamicText("str(demo_slider)")
        .setPos(246, 110)
        .setColor(0x404040)
        .setShadow(false)
        .setFormat("Value: %s")
);

// ProgressBar / 进度条
// - setTexture 省略或传 null/""：会回退到 states.png 的默认进度条贴图
// - bindProgress 已接入只读数据绑定：服务端 getter -> 客户端显示
demoPanel.addChild(
    PMUI.progressBar()
    .setPos(140, 126)
        .setSize(160, 12)
        .setDirection("RIGHT")
        .setTexture(null)
        // 进度条期望 0..1
    // norm(<srcKey>;<min>;<max>)：把 double binding 归一化到 0..1
    .bindProgress("norm(demo_slider;1;100)")
);

// DynamicText / 动态文本
// - 已接入：通过 StringSyncValue 同步字符串，然后在客户端动态渲染
// - 当前实现按“字符串值”进行 String.format(pattern, rawString)
//   因此推荐使用 %s；如果 pattern 不匹配会回退为原始字符串
demoPanel.addChild(
    // 内置 string key 示例：machine_name / machine_id
    PMUI.dynamicText("machine_name")
    .setPos(140, 144)
        .setColor(0xFFAA00)
        .setShadow(true)
        .setFormat("Machine: %s")
);

// Grid / 网格布局（minElementMargin 近似 row/column spacing）
val grid = PMUI.grid()
    .setPos(140, 164)
    .setColumns(3)
    .setRows(2)
    .setSpacing(2, 2);

grid.addChild(PMUI.button("1").setSize(27, 15).onClick("demo_grid_1"));
grid.addChild(PMUI.button("2").setSize(27, 15).onClick("demo_grid_2"));
grid.addChild(PMUI.button("3").setSize(27, 15).onClick("demo_grid_3"));
grid.addChild(PMUI.button("4").setSize(27, 15).onClick("demo_grid_4"));
grid.addChild(PMUI.button("5").setSize(27, 15).onClick("demo_grid_5"));
grid.addChild(PMUI.button("6").setSize(27, 15).onClick("demo_grid_6"));

demoPanel.addChild(grid);

// Slots / 槽位（当前为占位渲染，后续会接入真实同步/交互）
demoPanel.addChild(PMUI.text("Slots:").setPos(8, 186).setColor(0x404040).setShadow(false));

demoPanel.addChild(
    PMUI.itemSlot("default", 0)
    .setPos(140, 198)
);

demoPanel.addChild(
    PMUI.itemSlot("default", 1)
    .setPos(168, 198)
);

demoPanel.addChild(
    PMUI.fluidSlot()
    .setPos(152, 198)
        .setSize(18, 50)
        .setTankKey("default")
        .setTankIndex(0)
        .setCapacity(16000)
);

demoPanel.addChild(
    PMUI.itemSlotGroup("default", 0, 3, 2)
    .setPos(140, 220)
);

// Player inventory / 玩家物品栏
// 使用 DefaultMachineUI 的默认位置：SlotGroupWidget.playerInventory(...).pos(215, 171)
demoPanel.addChild(PMUI.text("Player Inventory (Default pos):").setPos(215, 160).setColor(0x404040).setShadow(false));
demoPanel.addChild(
    PMUI.playerInventory()
        .setPos(215, 171)
);

// Spacer / 空白占位（这里主要演示 definition；实际布局由容器决定）
demoPanel.addChild(
    PMUI.spacer()
        .setPos(0, 0)
        .setSize(0, 0)
);

// TooltipArea / 工具提示区域（当前客户端 tooltip 逻辑仍是 TODO）
demoPanel.addChild(
    PMUI.tooltipArea()
        .setPos(8, 126)
        .setSize(160, 12)
        .addLine("TooltipArea: TODO (client-side tooltip not wired yet)")
        .addLine("This shows how to declare tooltip lines.")
);

// Image / 图片（示例：直接显示 states.png 图集的一部分/或完整图）
demoPanel.addChild(
    PMUI.image("prototypemachinery:gui/states")
        .setPos(292, 6)
        .setSize(16, 16)
);

// 将 UI 注册到独立注册表：可被外部脚本/模组覆盖（按优先级）。
// reloadable 可能被多次执行：先清理旧注册，避免列表累积。
UIRegistry.clear(MACHINE_ID);

// 同理：bindings 也先清理旧注册，避免脚本 reload 后覆盖/累积。
UIBindings.clear(MACHINE_ID);

// 注册一个"自定义数据值"（存到 machine 的 ZSDataComponent），并提供一个 UI 可写的 binding key。
// Slider 会把值写回服务端 -> 服务端保存/同步 -> ProgressBar 与 DynamicText 会自动跟随更新。
UIBindings.registerDoubleDataClamped(MACHINE_ID, "demo_slider", "demo_slider_value", 1.0, 1.0, 100.0);

UIRegistry.register(MACHINE_ID, demoPanel);


/*
 * ============================================================================
 * API Reference / API 参考
 * ============================================================================
 *
 * PMUI (mods.prototypemachinery.ui.PMUI):
 *   - createPanel()           : Creates a new PanelBuilder / 创建新的面板构建器
 *   - button(text)            : Creates a ButtonBuilder with text / 创建带文本的按钮构建器
 *   - progressBar()           : Creates a ProgressBarBuilder / 创建进度条构建器
 *   - text(content)           : Creates a TextBuilder / 创建文本构建器
 *   - row()                   : Creates a RowBuilder (horizontal layout) / 创建行构建器（水平布局）
 *   - column()                : Creates a ColumnBuilder (vertical layout) / 创建列构建器（垂直布局）
 *   - grid()                  : Creates a GridBuilder (grid layout) / 创建网格构建器
 *   - slider()                : Creates a SliderBuilder / 创建滑块构建器
 *   - toggleButton()          : Creates a ToggleButtonBuilder / 创建开关按钮构建器
 *   - image(texture)          : Creates an ImageBuilder / 创建图片构建器
 *   - itemSlot()              : Creates an ItemSlotBuilder / 创建物品槽构建器
 *   - fluidSlot()             : Creates a FluidSlotBuilder / 创建流体槽构建器
 *
 * PanelBuilder:
 *   - setPos(x, y)            : Set panel position / 设置面板位置
 *   - setSize(width, height)  : Set panel size / 设置面板大小
 *   - setBackground(texture)  : Set background texture / 设置背景纹理
 *   - addChild(builder)       : Add a child widget / 添加子组件
 *
 * ButtonBuilder:
 *   - setPos(x, y)            : Set button position / 设置按钮位置
 *   - setSize(width, height)  : Set button size / 设置按钮大小
 *   - onClick(actionKey)      : Set action key for click handling / 设置点击动作键
 *
 * ProgressBarBuilder:
 *   - setPos(x, y)            : Set position / 设置位置
 *   - setSize(width, height)  : Set size / 设置大小
 *   - setDirection(dir)       : Direction: "LEFT", "RIGHT", "UP", "DOWN" / 方向
 *   - setTexture(texture)     : Set progress texture / 设置进度纹理
 *   - bindProgress(key)       : Bind to a progress data key / 绑定到进度数据键
 *   - setTooltip(template)    : Set tooltip template (use %d for value) / 设置提示模板
 *
 * TextBuilder:
 *   - setPos(x, y)            : Set text position / 设置文本位置
 *   - setSize(width, height)  : Set size (for alignment box) / 设置大小
 *   - setColor(color)         : Set text color (hex: 0xRRGGBB) / 设置文本颜色
 *   - setShadow(enabled)      : Enable/disable text shadow / 启用/禁用文本阴影
 *   - setAlignment(align)     : "LEFT", "CENTER", "RIGHT", etc. / 对齐方式
 *
 * RowBuilder (Horizontal Layout / 水平布局):
 *   - setPos(x, y)            : Set position / 设置位置
 *   - setSize(width, height)  : Set size / 设置大小
 *   - setSpacing(pixels)      : Set spacing between children / 设置子组件间距
 *   - addChild(builder)       : Add a child widget / 添加子组件
 *
 * ColumnBuilder (Vertical Layout / 垂直布局):
 *   - setPos(x, y)            : Set position / 设置位置
 *   - setSize(width, height)  : Set size / 设置大小
 *   - setSpacing(pixels)      : Set spacing between children / 设置子组件间距
 *   - addChild(builder)       : Add a child widget / 添加子组件
 *
 * GridBuilder (Grid Layout / 网格布局):
 *   - setPos(x, y)            : Set position / 设置位置
 *   - setSize(width, height)  : Set size / 设置大小
 *   - setColumns(count)       : Set number of columns / 设置列数
 *   - setRows(count)          : Set number of rows / 设置行数
 *   - addChild(builder)       : Add a child widget (fills row by row) / 添加子组件（逐行填充）
 *
 * SliderBuilder:
 *   - setPos(x, y)            : Set position / 设置位置
 *   - setSize(width, height)  : Set size / 设置大小
 *   - setRange(min, max)      : Set value range / 设置值范围
 *   - setStep(step)           : Set step increment / 设置步进值
 *   - bindValue(key)          : Bind to a sync value key / 绑定到同步值键
 *
 * ToggleButtonBuilder:
 *   - setPos(x, y)            : Set position / 设置位置
 *   - setSize(width, height)  : Set size / 设置大小
 *   - setOnTexture(texture)   : Set texture when ON / 设置开启状态纹理
 *   - setOffTexture(texture)  : Set texture when OFF / 设置关闭状态纹理
 *   - bindState(key)          : Bind to a boolean state key / 绑定到布尔状态键
 *
 * ImageBuilder:
 *   - setPos(x, y)            : Set position / 设置位置
 *   - setSize(width, height)  : Set size / 设置大小
 *   (texture is set in PMUI.image(texture) / 纹理在 PMUI.image(texture) 中设置)
 *
 * ItemSlotBuilder:
 *   - setPos(x, y)            : Set position / 设置位置
 *   - setSize(width, height)  : Set size (default 18x18) / 设置大小（默认 18x18）
 *   - setSlotIndex(index)     : Set inventory slot index / 设置物品栏槽位索引
 *
 * FluidSlotBuilder:
 *   - setPos(x, y)            : Set position / 设置位置
 *   - setSize(width, height)  : Set size / 设置大小
 *   - setTankIndex(index)     : Set fluid tank index / 设置流体槽索引
 *   - setCapacity(mb)         : Set tank capacity in mB / 设置槽容量（毫桶）
 *
 * Texture Paths / 纹理路径:
 *   Format: "modid:path/to/texture"
 *   Example: "prototypemachinery:gui/gui_controller_a"
 *   The full path will be: assets/modid/textures/path/to/texture.png
 * 
 * Action Keys / 动作键:
 *   Action keys are string identifiers that trigger server-side handlers.
 *   动作键是触发服务器端处理程序的字符串标识符。
 *   Implement handlers in your machine's component logic.
 *   在机器的组件逻辑中实现处理程序。
 */
