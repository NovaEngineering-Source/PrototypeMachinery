# UI：默认 ModularUI + 脚本 UIRegistry

本项目的 UI 分两条线：

1. **默认原生 UI**：基于 ModularUI，为机器/外设提供默认界面。
2. **脚本 UI**：CraftTweaker 侧通过 `UIRegistry` 注册 UI 定义，并可通过 `UIBindings` 做数据绑定。

此外还有一条偏“工具/调试”向的 UI：

3. **结构预览 UI（ModularUI，客户端只读）**：通过客户端命令 `/pm_preview_ui` 打开，用于结构材料/BOM + 3D/切片预览（可选 world scan 对比）。

## 代码位置

- 默认 UI（示例：Hatch）：
  - `src/main/kotlin/common/block/hatch/*/*GUI.kt`
  - `src/main/kotlin/client/gui/sync/*`
  - `src/main/kotlin/client/gui/widget/*`

- 脚本 UI：
  - `src/main/kotlin/integration/crafttweaker/zenclass/ui/PMUI.kt`
  - `src/main/kotlin/integration/crafttweaker/zenclass/ui/UIRegistry.kt`
  - `src/main/kotlin/integration/crafttweaker/zenclass/ui/UIBindings.kt`

- Runtime JSON（Machine UI Editor 导出）解释器与构建器：
  - `src/main/kotlin/impl/ui/runtime/MachineUiRuntimeJson.kt`
  - `src/main/kotlin/client/gui/builder/UIBindings.kt`
  - `src/main/kotlin/client/gui/builder/bindingexpr/UiBindingExpr.kt`
  - `src/main/kotlin/client/gui/builder/factory/LayoutWidgetFactory.kt`
  - `src/main/kotlin/client/gui/builder/factory/InteractiveWidgetFactory.kt`
  - `src/main/kotlin/client/gui/builder/factory/ConditionalWidgetFactory.kt`
  - `src/main/kotlin/client/gui/builder/factory/TabWidgetFactory.kt`

- UI 注册表实现：`src/main/kotlin/impl/ui/registry/MachineUIRegistryImpl.kt`

- 结构预览 UI（ModularUI，read-only）：
  - 命令：`src/main/kotlin/client/preview/ui/StructurePreviewUiClientCommand.kt`
  - 界面组装：`src/main/kotlin/client/preview/ui/StructurePreviewUiScreen.kt`
  - 3D 视图：`src/main/kotlin/client/preview/ui/widget/StructurePreview3DWidget.kt`
  - 宿主 gate（是否允许 world scan 等）：`src/main/kotlin/client/preview/ui/StructurePreviewUiHostConfig.kt`

## 同步原则（很重要）

- **权威数据在服务端**：ModularUI 的同步应以服务端为准。
- 客户端 UI 不应“自行重建”资源列表来覆盖服务端同步结果。

（这也是 ResourceStorage 增量同步能稳定工作的前提。）

## 脚本 UI：builders 与 runtime JSON 两条入口

目前脚本 UI 有两种主要方式：

1. **Builders（`PMUI`）**：在 ZenScript 中以 builder 的方式构建 UI（适合手写/可编程生成）。
2. **Runtime JSON（Machine UI Editor 导出）**：在 ZenScript 中直接注册 runtime JSON，由 Mod 在运行时解析并构建 ModularUI（适合可视化编辑器/工具链）。

Runtime JSON 的注册入口位于 `UIRegistry`（ZenScript）：

- `UIRegistry.registerRuntimeJson(...)`
- `UIRegistry.registerRuntimeJsonWithPriority(...)`

运行时解析与字段兼容策略详见：`impl/ui/runtime/MachineUiRuntimeJson.kt`。

> 重要：Runtime JSON 的“字段契约 / 支持的 widget type / tabs/条件/绑定表达式”等以实现为准。
> 建议把“对接契约”集中看 `docs/MachineUiEditorRuntime.md`，避免 UI.md 变成重复的大长篇。

## 绑定（UIBindings）与表达式 key

脚本 UI 可以通过 `UIBindings` 做数据绑定。

近期新增了“表达式 key”能力（用于 bool/double 绑定），语法形如：

- `not(key)`、`and(a;b)`、`or(a;b)`
- `norm(value;min;max)`、`clamp(value;min;max)`

表达式支持嵌套，参数使用 `;` 分隔。

实现位置：

- 表达式解析：`client/gui/builder/bindingexpr/UiBindingExpr.kt`
- 绑定创建与 syncKey 规则：`client/gui/builder/UIBindings.kt`

注意：

- 表达式绑定目前是**只读**的（本质是组合/映射已有 binding）。
- ModularUI 的同步 key 在内部按类型隔离（bool/double/string 分开），避免不同类型复用同名 key 导致冲突。

## Conditional（visibleIf/enabledIf）与 Tabs

Runtime JSON 与 builders 都支持：

- `visibleIf` / `enabledIf`
- tabs（`tabId` + `options.tabs` / legacy A/B 背景）

但请注意当前实现语义：

- `visibleIf` 与 `enabledIf` 最终都会落到 enable gate（`isEnabled = visible && enabled`）。
  - 也就是说：目前它们都更接近“禁用/不可交互”的语义，并不保证严格的“不可见且不渲染”。
- tabs 在运行时会构建为 `TabContainer` + 每个 tab 的内容 Panel；嵌套容器内部的 `tabId` 会被导出/解析阶段剥离以避免复杂嵌套语义。

实现位置：

- 条件包装：`client/gui/builder/factory/ConditionalWidgetFactory.kt`
- tabs 构建：`client/gui/builder/factory/TabWidgetFactory.kt`
- runtime JSON tabs/legacy 兼容：`impl/ui/runtime/MachineUiRuntimeJson.kt`

## 常见踩坑：默认 Panel 背景“透出”（MC_BACKGROUND）

### 现象

- 你的 UI 明明只渲染了自己的 PNG，但在 PNG 的透明区域（常见：左侧 Tab 条/圆角/挖空区）会看到一块**不透明、瓷砖状**的“原版风格背景”。
- 这块背景**不是** Minecraft 原版容器常见的半透明黑色遮罩效果（也不是你自家贴图的一部分），看起来更像 options/menu 那种平铺纹理。

### 根因（为什么会发生）

ModularUI 的 `ModularPanel` 会使用 `PANEL` 主题，其默认背景在 ModularUI 侧被设为：

- `IThemeApi.PANEL` 的默认 theme background = `GuiTextures.MC_BACKGROUND`
- `GuiTextures.MC_BACKGROUND` 对应 `modularui:gui/background/vanilla_background`（平铺纹理）

当我们构建脚本 UI 时，如果某个 `PanelDefinition` **没有指定** `backgroundTexture`（例如脚本里 root panel 只 `setSize()`，不 `setBackground()`），那么最终生成的 `ModularPanel` 会回退到主题默认背景，于是你会在任何透明区域看到这层背景“透出”。

> 容易误判点：
> - 一开始可能会以为是 Minecraft 的 `drawDefaultBackground()`（半透明黑色/渐变），但这里看到的通常是 **ModularUI 的 panel 主题背景纹理**。
> - 也可能误以为是自己 PNG 的边缘/透明像素导致“脏边”，其实底下垫着的是 `MC_BACKGROUND`。

### 定位线索（以后再遇到怎么快速确认）

- 在 ModularUI 工程里看：`IThemeApi.PANEL` / `GuiTextures.MC_BACKGROUND`
- 看到 `vanilla_background` 基本就可以锁定是 Panel Theme 在兜底渲染。

### 本项目的修复策略（不改 ModularUI 源码）

我们选择在**构建期**明确地“关闭默认兜底背景”，让“没有背景”就真的什么都不画（而不是画 ModularUI 的默认 panel 背景）。

做法：当 `PanelDefinition.backgroundTexture` 为空时，显式设置：

- `background(IDrawable.EMPTY)`：覆盖主题背景，但不渲染任何内容。

对应修复点：

- `src/main/kotlin/client/gui/UIBuilderHelper.kt`
  - `buildPanel(...)`：`bgPath == null` 时 `panel.background(IDrawable.EMPTY)`
- `src/main/kotlin/client/gui/builder/factory/LayoutWidgetFactory.kt`
  - `buildNestedPanel(...)`：嵌套 panel 同样在无背景时设置 `IDrawable.EMPTY`，避免父 panel 主题背景透过子 panel 的透明区域显示。

### 注意事项

- 这是一个**行为变更**：以前“不设置背景”会显示 ModularUI 的默认 panel 背景（`MC_BACKGROUND`），现在会变成“完全透明/不画背景”。
  - 如果某些 UI 需要那层默认背景，请在脚本或构建逻辑里显式设置背景（不要依赖兜底）。
- `IDrawable.EMPTY` 与 `IDrawable.NONE` 含义不同：
  - `EMPTY`：明确覆盖并“不画”；
  - `NONE`：通常用于 hover/overlay 等语义为“不要用 hover 特效，退回普通逻辑”。

## 翻译（i18n）与注释

默认 GUI 的文本主要来自两类来源：

1. **lang 文件（需要翻译）**：按钮 tooltip、固定文案等。
2. **运行时动态文本（不走本 mod 的 lang）**：例如流体名称（来自 `FluidStack.localizedName`）和存储数值（格式化后直接拼接）。

lang 文件位置：

- 英文：`src/main/resources/assets/prototypemachinery/lang/en_us.lang`
- 中文：`src/main/resources/assets/prototypemachinery/lang/zh_cn.lang`

### 结构投影预览（客户端）相关 i18n

结构投影预览功能（HUD/聊天提示/调试命令）使用以下命名空间：

- 调试命令 `/pm_preview`：`pm.preview.*`
  - 例如：`pm.preview.started` / `pm.preview.stopped` / `pm.preview.unknown_structure`

`/pm_preview_ui`（结构预览 GUI）在“找不到结构”等场景也会复用 `pm.preview.*`（例如 `pm.preview.unknown_structure`），以保持提示文案的一致性。
- 投影 HUD / 提示：`pm.projection.*`
  - 例如：`pm.projection.hud.orientation_status`、`pm.projection.chat.locked`
- 按键绑定名称：`key.pm.preview.*`
  - `key.pm.preview.lock_orientation`
  - `key.pm.preview.rotate_positive`
  - `key.pm.preview.rotate_negative`

> 备注：按键绑定的 key 若缺少翻译，会在“控制设置”里显示原始 key 字符串。为了可用性，建议中英文都补齐。

### Hatch 控件通用文案（新界面也会用到）

以下 key 被多个 Hatch GUI 共用（包括流体仓 `FluidHatchGUI`、流体 IO 仓 `FluidIOHatchGUI`）：

- `prototypemachinery.gui.hatch.auto_input`
  - 用途：自动输入按钮的 tooltip
  - 代码：`*HatchGUI.kt` 的控制按钮构建（`ToggleButton().tooltip { ... }`）

- `prototypemachinery.gui.hatch.auto_output`
  - 用途：自动输出按钮的 tooltip

- `prototypemachinery.gui.hatch.clear`
  - 用途：清空按钮的 tooltip（清空内部存储）

（可选/预留）

- `prototypemachinery.gui.hatch.input` / `prototypemachinery.gui.hatch.output`
  - 用途：如果某些 GUI 需要展示“输入/输出”固定标签，可复用这两个 key。

### 流体仓界面的动态文本（无需翻译）

流体仓/流体 IO 仓的“流体名称”和“数量（mB）”显示为动态文本：

- 流体名称：`FluidStack.localizedName`（来自流体本身或其来源 mod 的语言文件）
- 数量显示：由 `NumberFormatUtil` 格式化（label 为紧凑格式，tooltip 为带千分位的完整格式）

因此这里**不需要**为每种流体额外添加翻译 key，本 mod 也不会覆盖外部流体的名称翻译。

## See also

- [资源存储（ResourceStorage / EnergyStorage）](./Storage.md)
- [CraftTweaker（ZenScript）集成](./CraftTweaker.md)

- [Machine UI Editor Runtime JSON 契约](./MachineUiEditorRuntime.md)

- [结构预览（世界投影 / GUI）](./StructurePreview.md)

## GUI 贴图规范与切片/atlas 管线（结构预览相关）

结构预览 GUI 的贴图资源与规范文档在资源目录中：

- 贴图目录：`src/main/resources/assets/prototypemachinery/textures/gui/gui_structure_preview/`
- 规范文档（布局/交互/资源命名）：
  - `src/main/resources/assets/prototypemachinery/textures/gui/gui_structure_preview/gui_structure_preview.md`

如果需要把大量小贴图 stitch 成 TextureMap（降低纹理 bind 开销），项目还提供：

- 构建期切片工具：`src/main/kotlin/devtools/atlas/GuiSliceGenerator.kt`
- 切片 manifest：`src/main/resources/assets/prototypemachinery/pm_gui_slices/*.json`
- 运行时 atlas：`src/main/kotlin/client/atlas/PmGuiAtlas.kt`

## GUI 贴图规范（Machine UI / gui_states）

Machine UI Editor 与 runtime UI 中，一部分交互控件支持使用 `gui_states` 贴图模板（9-slice / 分隔线等规则）。

- 规范文档：`src/main/resources/assets/prototypemachinery/textures/gui/gui_states/gui_states.md`
