# JEI 集成：维护文档（Internals）

> English translation: [docs/en/JEI-Internals.md](./en/JEI-Internals.md)

本文档面向维护者/二次开发者，覆盖 `src/main/kotlin/integration/jei` 包下的所有源码文件，并用中英对照的方式解释其职责与整体数据流。

> 目标：当你想新增一种 requirement（自定义 ingredient type）、新增 renderer/handler、或排查 JEI UI/索引问题时，可以先从这里定位入口。

---

## 总体数据流（High-level data flow）

- **索引（Indexing）**：配方 wrapper 把 inputs/outputs 写入 JEI 的 `IIngredients`，从而支持 JEI 搜索与按物品/流体反查。
- **展示（Rendering）**：category 根据“布局（layout）”声明槽位，并用 provider/renderer 把 node 对应的 values 填进 JEI ingredient groups。
- **扩展点（Extensibility points）**：
  - Ingredient kind（某种 ingredient 类型，如 item/fluid/energy/自定义）
  - Node ingredient provider（把 requirement node 转换为可展示/可索引 values）
  - Requirement renderer（声明 slot / variant / widget / decorators）
  - Machine layout（把 nodes 摆放到 UI 上）
  - Decorator（额外 UI 元素：进度、耗时文本等）
  - Fixed slot providers（不绑定 node 的固定值槽位）

---

## 核心概念速查（Concepts）

### Requirement node vs JEI slot

- **Requirement node**：来自配方（recipe）本身的“需求节点”（输入/输出/每 tick 等）。
- **JEI slot**：最终在 JEI GUI 中出现的一个槽位（点击/聚焦/tooltip 的交互单位）。
  - 多数 slot 对应一个 node。
  - 也允许 **node-less 的固定槽位**（见 Fixed Slot Providers）。

### Ingredient kind / handler / provider / renderer

- **Ingredient kind（种类）**：抽象的 ingredient 类型（不仅限于 VanillaTypes）。
- **Kind handler**：把布局声明的 slot 映射到 JEI 的 ingredient group（init/set 的那套逻辑）。
- **Node ingredient provider**：把“某个 node”转换成 values（例如把 item requirement node → `List<ItemStack>`）。
- **Requirement renderer**：声明 slot 的尺寸/variant，以及额外 widgets/decorators。

### Fixed Slot Providers（固定值槽位）

- 适用于催化剂、模具提示等“不属于配方 requirement，但希望在 JEI 页显示/可点击”的元素。
- Provider 负责返回固定显示值；布局只引用 providerId。

---

## 文件索引（File index）

> 路径均相对项目根目录。

### 入口（Plugin entry）

- `src/main/kotlin/integration/jei/PMJeiPlugin.kt`
  - EN: JEI plugin entry point (category/wrapper/ingredient type registrations).
  - CN: JEI 集成入口（注册 categories/wrappers/ingredient types/各种 registry）。

### Runtime（把布局计划物化为可渲染结构）

- `src/main/kotlin/integration/jei/runtime/JeiPanelRuntime.kt`
  - EN: Build runtime slots/widgets from layout plan; caches fixed-slot values.
  - CN: 将 layout plan 物化为 runtime（slots/widgets），并缓存固定槽位 values。

- `src/main/kotlin/integration/jei/runtime/JeiModularPanelRuntime.kt`
  - EN: ModularUI panel runtime adapter for JEI usage.
  - CN: 在 JEI 场景下复用/适配 ModularUI 面板运行时。

- `src/main/kotlin/integration/jei/runtime/JeiEmbeddedModularUiInputBridge.kt`
  - EN: Input bridge for embedded ModularUI screens inside JEI.
  - CN: JEI 内嵌 ModularUI 界面时的输入桥接（鼠标/键盘/滚轮/tick）。

### Category / Wrapper（JEI API glue）

- `src/main/kotlin/integration/jei/category/PMMachineRecipeCategory.kt`
  - EN: Main recipe category; init ingredient groups and populate values.
  - CN: 机器配方页面 category：初始化 group 并填充 values（含固定槽位）。

- `src/main/kotlin/integration/jei/category/PMStructurePreviewCategory.kt`
  - EN: Category for structure preview recipes.
  - CN: 结构预览的 category。

- `src/main/kotlin/integration/jei/wrapper/PMMachineRecipeWrapper.kt`
  - EN: Writes ingredients into JEI indexing (`IIngredients`).
  - CN: 机器配方 wrapper：负责把 inputs/outputs 写入 JEI 索引。

- `src/main/kotlin/integration/jei/wrapper/PMStructurePreviewWrapper.kt`
  - EN: Wrapper for structure preview display.
  - CN: 结构预览 wrapper。

### Layout（如何摆放 nodes/slots）

- `src/main/kotlin/integration/jei/layout/DefaultJeiMachineLayout.kt`
  - EN: Default layout with auto-placement fallback.
  - CN: 默认布局（支持“未摆放节点”自动摆放）。

- `src/main/kotlin/integration/jei/layout/ExampleRecipeProcessorHatchesJeiLayout.kt`
  - EN: Example layout for hatch-based recipe processor.
  - CN: 示例布局：带仓室（hatches）的配方处理器。

#### Script-driven layout（脚本驱动布局）

- `src/main/kotlin/integration/jei/layout/script/ScriptJeiLayoutSpec.kt`
  - EN: Immutable, data-driven layout spec and rules.
  - CN: 不依赖 JEI API 的布局 spec（规则/条件/固定槽位规则等）。

- `src/main/kotlin/integration/jei/layout/script/ScriptJeiMachineLayoutDefinition.kt`
  - EN: Executes ScriptJeiLayoutSpec to a real layout builder.
  - CN: 执行器：把 spec 规则按顺序应用到真正的 layout builder。

### Registries（可扩展注册表）

- `src/main/kotlin/integration/jei/registry/JeiIngredientKindRegistry.kt`
  - EN: Registry of ingredient kinds used by slots/handlers.
  - CN: ingredient kind 注册表。

- `src/main/kotlin/integration/jei/registry/JeiNodeIngredientProviderRegistry.kt`
  - EN: Registry mapping requirement type → node ingredient provider.
  - CN: requirement type → node provider 注册表。

- `src/main/kotlin/integration/jei/registry/JeiRequirementRendererRegistry.kt`
  - EN: Registry mapping requirement type → renderer.
  - CN: requirement type → renderer 注册表。

- `src/main/kotlin/integration/jei/registry/JeiMachineLayoutRegistry.kt`
  - EN: Registry mapping machineId → layout definition.
  - CN: machineId → layout 注册表（脚本布局也会走这里）。

- `src/main/kotlin/integration/jei/registry/JeiDecoratorRegistry.kt`
  - EN: Registry for decorators (progress arrow, duration text, etc).
  - CN: decorator 注册表。

- `src/main/kotlin/integration/jei/registry/JeiFixedSlotProviderRegistry.kt`
  - EN: Provider registry for fixed (node-less) slots.
  - CN: 固定值槽位 provider 注册表（providerId → values/kind）。

### Builtins（内置实现）

- `src/main/kotlin/integration/jei/builtin/PMJeiBuiltins.kt`
  - EN: Built-in registrations for vanilla item/fluid/energy/etc.
  - CN: 内置注册：默认 kind/provider/renderer/decorator 等。

#### Builtin ingredient providers

- `src/main/kotlin/integration/jei/builtin/ingredient/VanillaItemNodeIngredientProvider.kt`
  - EN: Node → ItemStack values.
  - CN: 物品 node provider。

- `src/main/kotlin/integration/jei/builtin/ingredient/VanillaFluidNodeIngredientProvider.kt`
  - EN: Node → FluidStack values.
  - CN: 流体 node provider。

#### Builtin kind handlers

- `src/main/kotlin/integration/jei/builtin/ingredient/VanillaItemKindHandler.kt`
  - EN: Handler for item ingredient group init/set.
  - CN: 物品 kind handler（对接 JEI ingredient group）。

- `src/main/kotlin/integration/jei/builtin/ingredient/VanillaFluidKindHandler.kt`
  - EN: Handler for fluid ingredient group init/set.
  - CN: 流体 kind handler。

#### Builtin requirement renderers

- `src/main/kotlin/integration/jei/builtin/requirement/ItemRequirementJeiRenderer.kt`
  - EN: Renderer for item requirements.
  - CN: 物品 requirement renderer。

- `src/main/kotlin/integration/jei/builtin/requirement/FluidRequirementJeiRenderer.kt`
  - EN: Renderer for fluid requirements.
  - CN: 流体 requirement renderer。

- `src/main/kotlin/integration/jei/builtin/requirement/EnergyRequirementJeiRenderer.kt`
  - EN: Renderer for energy requirements.
  - CN: 能量 requirement renderer。

- `src/main/kotlin/integration/jei/builtin/requirement/ParallelismRequirementJeiRenderer.kt`
  - EN: Renderer for parallelism (text) requirement.
  - CN: 并行度（文本）renderer。

#### Builtin decorators

- `src/main/kotlin/integration/jei/builtin/decorator/ProgressArrowJeiDecorator.kt`
  - EN: Progress arrow/cycle decorator.
  - CN: 进度箭头/循环动画 decorator。

- `src/main/kotlin/integration/jei/builtin/decorator/RecipeDurationTextJeiDecorator.kt`
  - EN: Duration text decorator.
  - CN: 耗时文本 decorator。

### API（给 addon/扩展使用的抽象层）

- `src/main/kotlin/integration/jei/api/JeiRecipeContext.kt`
  - EN: Runtime context for provider/renderer decisions.
  - CN: provider/renderer 的运行时上下文（机器、配方等信息）。

- `src/main/kotlin/integration/jei/api/decorator/PMJeiDecorator.kt`
  - EN: Decorator interface.
  - CN: decorator 接口。

- `src/main/kotlin/integration/jei/api/ingredient/PMJeiIngredientKindHandler.kt`
  - EN: Contract for JEI ingredient group operations.
  - CN: kind handler 接口（init/set）。

- `src/main/kotlin/integration/jei/api/ingredient/PMJeiNodeIngredientProvider.kt`
  - EN: Node → values provider interface.
  - CN: node provider 接口。

- `src/main/kotlin/integration/jei/api/ingredient/IngredientsGroupKindHandlerAdapter.kt`
  - EN: Adapter to reduce handler boilerplate.
  - CN: 通用 adapter：封装 getIngredientsGroup + init + set 样板代码。

- `src/main/kotlin/integration/jei/api/layout/PMJeiLayoutBuilder.kt`
  - EN: Layout builder abstraction; collects placements (including fixed slots).
  - CN: 布局构建器抽象（收集节点/固定槽位摆放信息）。

- `src/main/kotlin/integration/jei/api/layout/PMJeiMachineLayoutDefinition.kt`
  - EN: Machine layout definition interface.
  - CN: machine layout 定义接口。

- `src/main/kotlin/integration/jei/api/layout/PMJeiLayoutRequirementsView.kt`
  - EN: View of available requirement nodes for layout.
  - CN: 布局可见的 nodes 视图（用于按类型/role 取节点）。

- `src/main/kotlin/integration/jei/api/layout/PMJeiRequirementRole.kt`
  - EN: Requirement roles (input/output/per-tick/other).
  - CN: requirement role 枚举。

- `src/main/kotlin/integration/jei/api/render/PMJeiRequirementRenderer.kt`
  - EN: Renderer contract for requirement types.
  - CN: requirement renderer 接口。

- `src/main/kotlin/integration/jei/api/render/PMJeiRequirementNode.kt`
  - EN: Render-time representation of a requirement node.
  - CN: 渲染阶段的 node 表示。

- `src/main/kotlin/integration/jei/api/render/PMJeiRendererVariant.kt`
  - EN: Renderer variant metadata.
  - CN: renderer variant（尺寸/默认 variant 等）。

- `src/main/kotlin/integration/jei/api/render/JeiSlotCollector.kt`
  - EN: Slot declaration collector.
  - CN: slot 声明收集器（renderer 用来声明 slots）。

- `src/main/kotlin/integration/jei/api/ui/PMJeiWidgetCollector.kt`
  - EN: Widget collector for additional UI elements.
  - CN: widget 收集器（额外 UI 元素）。

### Util

- `src/main/kotlin/integration/jei/util/JeiControllerStack.kt`
  - EN: Utility for controller stack handling (R-click open, etc).
  - CN: controller stack 工具类（例如按 R 打开等交互支持）。
