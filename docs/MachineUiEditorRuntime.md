# Machine UI Editor：Runtime JSON 对接（现状 + 契约 + 限制）

本文件描述 **Web Editor（Machine UI Editor）导出的 Runtime JSON** 在 PrototypeMachinery 模组侧的解释与构建方式。

这份文档的目标是：

- 给出一个“可长期维护”的 **稳定契约**（字段名、默认值、兼容策略）
- 解释当前实现已经支持什么，以及“看起来像支持但其实不支持”的坑
- 把 WebEditor 的 IR 与 Mod 侧的 Kotlin 实现对齐（避免文档飘在实现之上）

## 已落地的实现（代码现状）

### Runtime JSON 注册入口（ZenScript）

- `mods.prototypemachinery.ui.UIRegistry.registerRuntimeJson(machineId, runtimeJson)`
- `mods.prototypemachinery.ui.UIRegistry.registerRuntimeJsonWithPriority(machineId, runtimeJson, priority)`

代码：`src/main/kotlin/integration/crafttweaker/zenclass/ui/UIRegistry.kt`

> 这意味着：Web Editor 生成的 ZenScript（runtime-json 形式）已经可以直接在游戏里注册 UI。

### Runtime JSON 解析器（Mod 侧）

代码：`src/main/kotlin/impl/ui/runtime/MachineUiRuntimeJson.kt`

当前解析器特性：

- **容错**：未知 widget type 会被忽略（不会崩 UI）
- **字段兼容**：多个同义字段名会被识别（例如 `w/width`、`h/height`）
- **Tabs**：支持 `options.tabs[]`（新）与 legacy `backgroundA/backgroundB`（旧）
- **条件字段**：支持 `visibleIf/enabledIf`（生成 `ConditionalDefinition` 包装）
- **容器递归**：支持 `panel/row/column/grid/scroll_container` 以及嵌套 children

### 条件包装器（visibleIf / enabledIf）

定义：`ConditionalDefinition`（`src/main/kotlin/api/ui/definition/WidgetDefinitions.kt`）

构建：`ConditionalWidgetFactory`（`src/main/kotlin/client/gui/builder/factory/ConditionalWidgetFactory.kt`）

当前语义（重要）：

- `visibleIf` 与 `enabledIf` 都会被解析为 **bool 绑定/表达式**
- 最终效果是：`wrapper.isEnabled = (visibleIf && enabledIf)`

也就是说：

- 目前 **没有“真正不渲染”的隐藏语义**；而是通过 enable/disable 走一条轻量路径
- `visibleIf` 与 `enabledIf` 在当前实现里不会产生“不同的 UI 效果”（都等价于 enable gate）

### Tabs（多 Tab / 分页）

定义：`TabDefinition` / `TabContainerDefinition`（`WidgetDefinitions.kt`）

构建：`TabWidgetFactory`（`src/main/kotlin/client/gui/builder/factory/TabWidgetFactory.kt`）

实现要点：

- **切换是纯客户端行为**：不需要服务端同步
- 当 `tabPosition == "LEFT"` 时使用 `PagedWidget + PageButton`：
  - 非激活页不会渲染（性能与“所见即所得”更接近）
  - 前两个 tab 使用 DefaultMachineUI 风格图标贴图
- 其他位置（TOP/BOTTOM/RIGHT）目前走简单 fallback：按钮 + enable/disable 内容

### 变量绑定与表达式（Binding Expr）

绑定系统入口：

- ZenScript 注册：`src/main/kotlin/integration/crafttweaker/zenclass/ui/UIBindings.kt`
- 运行时创建 SyncValue：`src/main/kotlin/client/gui/builder/UIBindings.kt`

表达式实现：`src/main/kotlin/client/gui/builder/bindingexpr/UiBindingExpr.kt`

当前已经支持：

- **bool**：`not(...)`、`and(...)`、`or(...)`（支持嵌套）
- **double**：`clamp(...)`、`norm(...)`（支持嵌套），并支持数值字面量（如 `0.5`）

并且：表达式是 **只读**（不会产生 setter）。

## Runtime JSON 稳定契约（建议按此长期维护）

### 顶层结构

Web Editor 的 runtime 导出（`web-editor/src/machine-ui/exporters/jsonExporter.ts`）输出结构：

- `schemaVersion: 1`
- `name: string`
- `canvas: { width, height }`
- `options?: { ... }`
- `widgets?: Widget[]`

其中 `guides/gridSize/showGuides` 等 editor-only 字段会被剥离。

### Tabs：options 字段

推荐使用新结构：

- `options.tabs[]: { id: string, label?: string, texturePath?: string }`
- `options.activeTabId?: string`

兼容旧结构（仍会被识别）：

- `options.backgroundA.texturePath` / `options.backgroundB.texturePath`
- `options.activeBackground: "A" | "B"`

运行时选择初始 tab 的顺序（实现一致）：

1) `activeTabId`
2) `activeBackground`
3) `tabs[0]?.id`
4) fallback：`"A"`

### Widget 通用字段

绝大多数 widgets 支持以下通用字段：

- `type: string`
- `x, y: int`
- `w/h` 或 `width/height: int`
- `tabId?: string`：**仅在顶层 widgets 生效**
- `visibleIf?: string`
- `enabledIf?: string`

关于 `tabId`：

- `tabId` 为空/缺失：视为 **全局 widget**（所有 tab 可见）
- `tabId` 非空：视为归属到某个 tab，仅在该 tab 页里渲染
- **嵌套 children 不参与 tab 分区**：
  - Mod 侧解析器对嵌套 children 关闭 tabId 读取（`allowTabTag=false`）
  - WebEditor runtime 导出会删除 nested child 的 `tabId`（防止“跨 tab 嵌套”造成语义歧义）

### 条件字段：visibleIf / enabledIf

两者都是 **bool 绑定 key 或表达式**（详见下节语法）。

当前实现的“实际效果”是：

$$
  \mathrm{widgetEnabled} = (\text{visibleIf} \lor \text{true}) \land (\text{enabledIf} \lor \text{true})
$$

（实现里把缺失视为 `true`。）

## 绑定表达式语法（实现即文档）

表达式字符串形如：`func(arg0;arg1;...)`。

- 参数分隔符是 `;`（避免与逗号/JSON 冲突）
- 支持嵌套：`clamp(norm(foo;0;100);0;1)`
- 未识别的 `func(...)` 会被当作“普通 key”处理（不会报错，但也不会按函数求值）

### Bool 表达式

支持：

- `not(x)`
- `and(a;b;...)`
- `or(a;b;...)`

示例：

- `visibleIf: "not(formed)"`
- `enabledIf: "and(has_power;not(redstone_lock))"`

### Double 表达式

支持：

- 数值字面量：`0` / `0.5` / `-1`
- `clamp(x;min;max)`
- `norm(x;min;max)`

示例：

- `progressKey: "norm(energy;0;100000)"`
- `progressKey: "clamp(raw_progress;0;1)"`

### Sync key 规则（避免冲突）

ModularUI 要求 `(key,id)` 对应唯一 SyncHandler 类型。

因此 `client/gui/builder/UIBindings.kt` 把同一个逻辑 key 按类型做了命名空间隔离：

- `prototypemachinery:ui_binding:bool:<raw>`
- `prototypemachinery:ui_binding:double:<raw>`
- `prototypemachinery:ui_binding:string:<raw>`

这允许你在不同组件里用同一个“业务 key 名”，不会因为类型不同而冲突。

## 支持的 widget 类型（runtime JSON → Kotlin 定义）

以下是当前 `MachineUiRuntimeJson` 明确支持的 type（大小写不敏感，内部会 `lowercase()`）：

### 容器/布局（可递归）

- `panel`
- `row`
- `column`
- `grid`
- `scroll_container`（以及同义：`scrollcontainer/scroll/scrollarea`）

### 叶子组件

- `text`
- `progress`
- `slotGrid`（JSON 中常见写法；lowercase 后等价于 `slotgrid`）
- `image`
- `button`
- `toggle`（以及同义：`togglebutton/toggle_button`）
- `slider`
- `textField`（以及同义：`text_field/textfield/input_box/inputbox`）
- `playerInventory`（以及同义：`player_inventory`）

> 注意：这里的“支持”指 runtime JSON 解析器能产出对应 `WidgetDefinition`；最终是否能正确显示，还取决于对应 WidgetFactory 是否实现。

## 已知限制与待办（建议写在这里，避免散落）

### 1) visibleIf/enabledIf 目前等价

如果你需要“不可见但仍占位/可见但不可交互”等更细的语义，需要在 `ConditionalWidgetFactory` 做更精细的区分（例如只控制渲染/只控制交互）。

### 2) 表达式功能刻意保持最小

目前没有：

- `eq(...)` / 比较运算
- string 表达式（拼接、format）

建议策略：先用脚本端注册基础 binding key，把复杂逻辑放在脚本端；表达式只用于 UI 内的轻量“归一化/开关组合”。

### 3) TabContainer 目前只对 LEFT 做了精细实现

TOP/BOTTOM/RIGHT 仍是 fallback（按钮 + enable/disable）。如果 WebEditor 将来支持不同 tab bar 布局，建议在 `TabWidgetFactory` 里补齐。

### 4) 资源化管线（长期）

当前推荐的“落地姿势”仍是：WebEditor 导出 ZenScript（runtime-json），脚本里注册。

如果要做真正的“资源/数据包驱动”加载（不复制粘贴 JSON 字符串），需要新增：

- runtime JSON loader（从资源路径读取）
- schema 版本迁移
- 更友好的 parse 错误提示
