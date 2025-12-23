# JEI / HEI 集成（配方索引 + 默认 UI + Addon 扩展）

> English translation: [docs/en/JEI.md](./en/JEI.md)

本文档介绍 PrototypeMachinery 的 JEI/HEI（JEI 4.16 / MC 1.12.2）集成工作原理，并给出 addon 接入“VanillaTypes 之外 ingredient（气体/源质等）”的完整思路与代码示例。

> 关键词：
> - **索引（Indexing）**：决定 JEI 的 U/R 查询能不能搜到你的配方。
> - **展示（UI）**：决定 JEI 配方页面如何摆放/渲染槽位与控件。
> - 本项目把「索引」与「展示」解耦：UI 由 renderer/layout 控制，索引由 wrapper + provider 控制。

---

## 总览：这套集成解决了什么问题？

对每个 PrototypeMachinery 配方页面，JEI 需要两件事：

1. **IIngredients（索引）**：告诉 JEI 这个配方的 inputs/outputs 是什么（支持多 ingredient 类型）。
2. **IRecipeLayout（展示）**：告诉 JEI 页面上有哪些 slot、每个 slot 的位置/大小，以及每个 slot 展示哪些 values。

本项目的关键设计：

- `PMMachineRecipeWrapper#getIngredients(IIngredients)` 负责写入 **索引数据**。
- `PMMachineRecipeCategory#setRecipe(IRecipeLayout, wrapper, IIngredients)` 负责构建 **UI runtime** 并把 runtime 的 slot 声明转成 JEI groups。
- requirement renderer 负责“一个 requirement 怎么拆成 nodes、nodes 有哪些 variants、如何声明 slots/控件”。
- addon 通过注册表把自己的新类型（kind / provider / handler / renderer / layout）接进来，不需要改 core category。

---

## 核心链路（工作原理）

### 1) 从配方 requirements 到 “renderable nodes”

- 配方：`MachineRecipe.requirements: Map<RecipeRequirementType<*>, List<RecipeRequirementComponent>>`
- renderer：`PMJeiRequirementRenderer<C>`
  - `split(ctx, component)`：一个 component 可以拆成多个 `PMJeiRequirementNode<C>`
  - `variants/defaultVariant`：节点可有多种展示尺寸/样式
  - `declareJeiSlots`：声明 JEI slot（位置/大小/kind/index/role）
  - `buildWidgets`：构建 ModularUI widgets（比如边框、文字、进度箭头等非 ingredient 元素）

注册点：`JeiRequirementRendererRegistry`。

### 2) layout 决定 nodes 放哪里

- layout：`PMJeiMachineLayoutDefinition`
- 内置默认布局：`integration/jei/layout/DefaultJeiMachineLayout.kt`
  - 物品左输入右输出
  - 流体左右各 1 个大 tank + per-tick 小 tank
  - 能量/并行度等文本放中间
  - **fallback**：把“未被显式摆放的 nodes（通常来自 addon requirement）”自动网格摆放（依赖 renderer.defaultVariant）

注册点：`JeiMachineLayoutRegistry`
- `setDefault(DefaultJeiMachineLayout)` 在 `PMJeiPlugin` 中完成
- 机器专用 layout 可通过 `JeiMachineLayoutRegistry.register(machineTypeId, layout)` 覆盖

### 3) slot kind：把 slot 映射到“JEI ingredient 类型”

- slot kind：`JeiSlotKind`（可扩展，核心只有一个字段：`id: ResourceLocation`）
- slot 声明：`JeiSlot(kind, nodeId, index, role, x, y, width, height)`

对于 Vanilla：
- `JeiSlotKinds.ITEM` → `VanillaItemKindHandler`（使用 `recipeLayout.itemStacks`）
- `JeiSlotKinds.FLUID` → `VanillaFluidKindHandler`（使用 `recipeLayout.fluidStacks`）

对于 addon 自定义 ingredient：
- 你定义一个新的 `JeiSlotKind`（例如 `myaddon:ingredient/gas`）
- 同时注册一个 `PMJeiIngredientKindHandler<GasStack>`，负责：
  - `init(...)`：初始化 `IRecipeLayout` 中对应 ingredient group 的 slot
  - `set(...)`：把 values 塞进这个 slot

注册点：`JeiIngredientKindRegistry`

> 注意：JEI 4.16（1.12）使用的是 `mezz.jei.api.recipe.IIngredientType<T>`（不是 `mezz.jei.api.ingredients.*`）。

### 4) node provider：决定“索引里写什么”

renderer 只关心 UI 声明，并不负责索引。

索引由 provider 决定：`PMJeiNodeIngredientProvider<C, T>`
- 输入/输出的 role 来自 node（`PMJeiRequirementRole`）
- provider 只需要把某个 node 变成 JEI 可索引的 values（支持多备选）

注册点：`JeiNodeIngredientProviderRegistry`

### 5) wrapper：真正写入 JEI 的 IIngredients

`integration/jei/wrapper/PMMachineRecipeWrapper.kt`：
- 遍历 `recipe.requirements`
- 使用 renderer.split 拆 nodes
- 使用 provider.getDisplayed 拿 values
- 使用 kind handler 的 `ingredientType` 分组
- 写入：
  - `ingredients.setInputLists(type, lists)`
  - `ingredients.setOutputLists(type, lists)`

这一步决定 JEI 是否能通过 U/R 搜索到你的配方。

---

## Addon 接入：最小实现清单（按顺序）

下面按“你需要实现/注册哪些东西”来写，顺序基本就是你在 addon 里落地时的顺序。

### A. 先完成 JEI 自定义 ingredient 类型注册（addon 自己负责）

PrototypeMachinery 这边的 registries **不会帮你把新 ingredient 类型注册到 JEI 本体**。

你需要在 addon 的 JEI plugin 中按 JEI API 完成以下之一（取决于 JEI 版本/接口）：
- 声明 `IIngredientType<GasStack>`
- 注册对应的 helper/renderer（让 JEI 知道如何渲染/比较/显示名字/tooltip）

**只有 JEI 认识你的 ingredient 类型后**，下面的 handler/provider 才有意义。

> 如果你只注册了 PM 的 handler/provider，但 JEI 没注册自定义 ingredient 类型：
> - 你可能拿不到 `IRecipeLayout.getIngredientsGroup(type)`
> - 或 JEI 无法正确渲染/比较你的 values

### B. 定义一个新的 JeiSlotKind

```kotlin
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotKind
import net.minecraft.util.ResourceLocation

public object GasJeiSlotKind : JeiSlotKind {
    override val id: ResourceLocation = ResourceLocation("myaddon", "ingredient/gas")
}
```

### C. 实现并注册 PMJeiIngredientKindHandler<GasStack>

> 推荐：绝大多数自定义 ingredient（气体/源质/mana 等）都属于标准的 `recipeLayout.getIngredientsGroup(type)` 路线。
> 这种情况下请**优先使用** PrototypeMachinery 提供的通用 adapter：
>
> - `integration/jei/api/ingredient/IngredientsGroupKindHandlerAdapter.kt`
>
> 它把 `getIngredientsGroup + init + set` 的样板代码封装起来，并自动处理 INPUT/OUTPUT（以及 CATALYST 作为 input）判定。
> 如果你提供 `IIngredientRenderer<T>`，adapter 会使用 JEI 的 **full init overload**（包含 width/height），交互命中区域更准确。

```kotlin
import github.kasuminova.prototypemachinery.integration.jei.api.ingredient.IngredientsGroupKindHandlerAdapter
import github.kasuminova.prototypemachinery.integration.jei.api.ingredient.PMJeiIngredientKindHandler
import mezz.jei.api.recipe.IIngredientType
import mezz.jei.api.ingredients.IIngredientRenderer

// 你的自定义类型（示例）
public data class GasStack(val id: String, val amount: Int)

public object MyJeiTypes {
    // 这里的 GAS 需要由 addon 按 JEI API 正确创建/注册
    public lateinit var GAS: IIngredientType<GasStack>
}

// 推荐：直接使用 adapter
public object GasKindHandler : IngredientsGroupKindHandlerAdapter<GasStack>(
    kind = GasJeiSlotKind,
    ingredientType = MyJeiTypes.GAS,
    ingredientRenderer = null, // 可选：传入 IIngredientRenderer<GasStack> 以使用 full init overload
)

// 如果你有 renderer，可以这样传入（示例：renderer 的具体实现由 addon 自己提供/注册）
public object GasKindHandlerWithRenderer : IngredientsGroupKindHandlerAdapter<GasStack>(
    kind = GasJeiSlotKind,
    ingredientType = MyJeiTypes.GAS,
    ingredientRenderer = IIngredientRenderer { _, _, _, _ ->
        // render(Minecraft, x, y, ingredient)；这里仅示意
    },
)
```

注册：

```kotlin
import github.kasuminova.prototypemachinery.integration.jei.registry.JeiIngredientKindRegistry

JeiIngredientKindRegistry.register(GasKindHandler)
```

### D. 实现并注册 requirement renderer（决定 UI 的 slots/widgets）

你的 requirement 类型需要一个 renderer，否则：
- runtime 无法生成 node
- 默认布局也无法 fallback 自动摆放

示例（仅展示 slot 声明部分）：

```kotlin
import github.kasuminova.prototypemachinery.integration.jei.api.render.*
import github.kasuminova.prototypemachinery.integration.jei.api.JeiRecipeContext
import github.kasuminova.prototypemachinery.integration.jei.api.ui.PMJeiWidgetCollector

public class GasRequirementComponent(/* ... */)

public object GasRequirementType /* : RecipeRequirementType<GasRequirementComponent> */

public object GasRequirementJeiRenderer : PMJeiRequirementRenderer<GasRequirementComponent> {
    override val type = GasRequirementType

    override fun split(ctx: JeiRecipeContext, component: GasRequirementComponent): List<PMJeiRequirementNode<GasRequirementComponent>> {
        // 你可以把一个 component 拆成多个 node（比如多输入），这里略
        TODO("split")
    }

    override fun variants(ctx: JeiRecipeContext, node: PMJeiRequirementNode<GasRequirementComponent>): List<PMJeiRendererVariant> {
        return listOf(defaultVariant(ctx, node))
    }

    override fun defaultVariant(ctx: JeiRecipeContext, node: PMJeiRequirementNode<GasRequirementComponent>): PMJeiRendererVariant {
        return object : PMJeiRendererVariant {
            override val id = net.minecraft.util.ResourceLocation("myaddon", "gas/slot_18")
            override val width = 18
            override val height = 18
        }
    }

    override fun declareJeiSlots(
        ctx: JeiRecipeContext,
        node: PMJeiRequirementNode<GasRequirementComponent>,
        variant: PMJeiRendererVariant,
        x: Int,
        y: Int,
        out: JeiSlotCollector,
    ) {
        val idx = out.nextIndex(GasJeiSlotKind)
        out.add(
            JeiSlot(
                kind = GasJeiSlotKind,
                nodeId = node.nodeId,
                index = idx,
                role = when (node.role) {
                    github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiRequirementRole.INPUT,
                    github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiRequirementRole.INPUT_PER_TICK -> JeiSlotRole.INPUT
                    github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiRequirementRole.OUTPUT,
                    github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiRequirementRole.OUTPUT_PER_TICK -> JeiSlotRole.OUTPUT
                    else -> JeiSlotRole.CATALYST
                },
                x = x,
                y = y,
                width = variant.width,
                height = variant.height,
            )
        )
    }

    override fun buildWidgets(
        ctx: JeiRecipeContext,
        node: PMJeiRequirementNode<GasRequirementComponent>,
        variant: PMJeiRendererVariant,
        x: Int,
        y: Int,
        out: PMJeiWidgetCollector,
    ) {
        // 可选：加边框/图标/文字等
    }
}
```

注册：

```kotlin
import github.kasuminova.prototypemachinery.integration.jei.registry.JeiRequirementRendererRegistry

JeiRequirementRendererRegistry.register(GasRequirementType, GasRequirementJeiRenderer)
```

### E. 实现并注册 node ingredient provider（决定索引 values）

没有 provider 时：UI 可能有槽位，但 **JEI 索引不会包含你的 gas**（U/R 搜不到）。

```kotlin
import github.kasuminova.prototypemachinery.integration.jei.api.JeiRecipeContext
import github.kasuminova.prototypemachinery.integration.jei.api.ingredient.PMJeiNodeIngredientProvider
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementNode

public object GasNodeIngredientProvider : PMJeiNodeIngredientProvider<GasRequirementComponent, GasStack> {
    override val type = GasRequirementType
    override val kind = GasJeiSlotKind

    override fun getDisplayed(ctx: JeiRecipeContext, node: PMJeiRequirementNode<GasRequirementComponent>): List<GasStack> {
        // 返回可索引/可轮换展示的备选项
        // 例如：tag/ore-like gas 输入可返回多个 GasStack
        TODO("extract displayed gas values")
    }
}
```

注册：

```kotlin
import github.kasuminova.prototypemachinery.integration.jei.registry.JeiNodeIngredientProviderRegistry

JeiNodeIngredientProviderRegistry.register(GasRequirementType, GasNodeIngredientProvider)
```

### F. 可选：机器专用 layout

如果你的 requirement 很特殊（需要固定位置/多行展示），可以给特定机器注册 layout：

```kotlin
import github.kasuminova.prototypemachinery.integration.jei.registry.JeiMachineLayoutRegistry

JeiMachineLayoutRegistry.register(machineTypeId, myLayout)
```

如果不注册：默认使用 `DefaultJeiMachineLayout`。
- 只要 renderer 能提供 `defaultVariant`，默认布局会把未摆放节点自动摆上去。

---

## ZenScript（CraftTweaker）自定义 JEI 布局（整合包作者用）

从 `vNext` 起（本仓库实现），你可以直接用 ZenScript 给某个 machineType 覆盖 JEI 布局，而无需写 Kotlin addon。

### 脚本入口

- 创建布局：`mods.prototypemachinery.jei.PMJEI`
  - `PMJEI.createLayout()`
  - `PMJEI.createLayoutSized(width, height)`
- 注册布局：`mods.prototypemachinery.jei.LayoutRegistry`
  - `LayoutRegistry.register(machineId, layout)`
  - `LayoutRegistry.registerReplace(machineId, layout, replace)`
  - `LayoutRegistry.setDefault(layout)`（可选：覆盖默认布局）

### 你需要知道的内置 ID（常用）

Requirement type id（用于 `placeFirst/placeGrid/placeAllLinear` 的第一个参数）：

- 物品：`prototypemachinery:item`
- 流体：`prototypemachinery:fluid`
- 能量：`prototypemachinery:energy`
- 并行度（文本）：`prototypemachinery:parallelism`

Role（第二个参数，大小写不敏感，传 `null` / `"ANY"` / `"*"` 表示不过滤）：

- `INPUT` / `OUTPUT`
- `INPUT_PER_TICK` / `OUTPUT_PER_TICK`
- `OTHER`

默认情况下 role 过滤是“精确匹配”的：

- `"INPUT"` 只匹配 `INPUT`
- `"INPUT_PER_TICK"` 只匹配 `INPUT_PER_TICK`

如果你希望把 `INPUT`/`OUTPUT` 视作“同组”，让它们也包含 per-tick role，可以在 builder 上启用：

```zenscript
val layout = PMJEI.createLayoutSized(176, 96)
    .mergePerTickRoles(true)
    // 此时："INPUT" 会同时匹配 INPUT + INPUT_PER_TICK
    .placeAllLinearWithVariant("prototypemachinery:fluid", "INPUT", 80, 6, 0, 60, "prototypemachinery:tank/16x58")
;
```

> 提示：即使启用了 `mergePerTickRoles(true)`，你仍然可以通过显式传 `"INPUT_PER_TICK"`/`"OUTPUT_PER_TICK"` 来只匹配 per-tick 节点。

Renderer variant id（可选，用于 `place*WithVariant`）：

- 物品 18x18：`prototypemachinery:slot/18`
- 流体 tank：
  - `prototypemachinery:tank/16x58`
  - `prototypemachinery:tank/16x34`
  - `prototypemachinery:tank/24x58`

Decorator id（用于 `addDecorator*`）：

- 进度箭头/循环：`prototypemachinery:decorator/progress`
- 耗时文本：`prototypemachinery:decorator/recipe_duration`

### 示例：复刻“多列物品 + 纵向 tank + 进度箭头 + 耗时文本”

> 这个例子有意写得“偏啰嗦”，方便复制后改参数。

```zenscript
#loader crafttweaker reloadable

import mods.prototypemachinery.jei.PMJEI;
import mods.prototypemachinery.jei.LayoutRegistry;

// 你的 machine type id
val MACHINE_ID = "prototypemachinery:example_recipe_processor";

val layout = PMJEI.createLayoutSized(176, 96)
    // 左侧：输入物品 2x3
    .placeGridWithVariant(
        "prototypemachinery:item", "INPUT",
        6, 6,
        2, 3,
        18, 18,
        "prototypemachinery:slot/18"
    )
    // 右侧：输出物品 2x3
    .placeGridWithVariant(
        "prototypemachinery:item", "OUTPUT",
        176 - 6 - 2 * 18 - 2, 6,
        2, 3,
        18, 18,
        "prototypemachinery:slot/18"
    )
    // 中间：输入流体（纵向 1 列多行 tank）
    // - 如果你的配方只有 1 个流体输入，也没关系：只会摆放 1 个
    .placeAllLinearWithVariant(
        "prototypemachinery:fluid", "INPUT",
        80, 6,
        0, 60,
        "prototypemachinery:tank/16x58"
    )
    // 中间：输出流体
    .placeAllLinearWithVariant(
        "prototypemachinery:fluid", "OUTPUT",
        100, 6,
        0, 60,
        "prototypemachinery:tank/16x58"
    )
    // 进度箭头
    .addDecoratorWithData(
        "prototypemachinery:decorator/progress",
        (176 - 20) / 2, 40,
        {
            "style": "arrow",
            "direction": "RIGHT",
            // 可选：覆盖动画周期；不填则默认按 recipe.durationTicks
            // "cycleTicks": 200
        }
    )
    // 耗时文本
    .addDecoratorWithData(
        "prototypemachinery:decorator/recipe_duration",
        (176 - 80) / 2, 62,
        {
            "width": 80,
            "height": 10,
            "align": "CENTER",
            "template": "{ticks} t ({seconds}s)",
            "shadow": true
        }
    )
    // 可选：把未被脚本显式摆放的 nodes 自动摆放到某个区域（用于 addon requirement）
    // .autoPlaceRemaining(6, 6)
;

LayoutRegistry.register(MACHINE_ID, layout);
```

### 节点 placement data：按元素覆盖渲染细节（例如能量条 LED yOffset）

部分内置 widget 支持从 layout 里读取“placement data”（一个 `Map<String, Any>`），用于做每个元素的细粒度调整。

目前支持：能量条顶部 IO LED 的 Y 偏移（像素）。key：`"energyLedYOffset"`。

```zenscript
#loader crafttweaker reloadable

import mods.prototypemachinery.jei.PMJEI;
import mods.prototypemachinery.jei.LayoutRegistry;

val MACHINE_ID = "prototypemachinery:example_recipe_processor";

val layout = PMJEI.createLayoutSized(176, 96)
    // 将能量条放在某处，并把 LED 往上挪 1 像素（-1）
    .placeFirstWithVariantAndData(
        "prototypemachinery:energy", "INPUT",
        80, 6,
        "prototypemachinery:energy/1x3",
        {"energyLedYOffset": -1}
    );

LayoutRegistry.register(MACHINE_ID, layout);
```

如果未提供该 key，则会使用配置文件 `config/prototypemachinery_jei.cfg` 中的 `ui.energyLedYOffset` 作为默认值。

### 条件化（动态显示）的最小能力

目前 ZenScript builder 支持基于“某类 node 数量”的条件：

```zenscript
layout.whenCountAtLeast("prototypemachinery:fluid", "INPUT", 2)
    .addDecorator("prototypemachinery:decorator/progress", 10, 10)
    .then();
```

如果你开启了 `mergePerTickRoles(true)`，那么这里的 `"INPUT"`/`"OUTPUT"` 统计同样会把 `*_PER_TICK` 合并计入。

后续可以在此基础上扩展更复杂的谓词（按机器 id、按是否存在某个 role、按 renderer variant 等）。

---

## 固定值槽位（Fixed Slot Providers）

有些 JEI UI 元素并不想绑定到某个配方 requirement node，例如：

- 催化剂（Catalyst）图标
- “必须携带某个工具/模具”的提示槽位
- 纯展示用的图标位（比如升级芯片、模组图标等）

这类槽位可以用 **固定值槽位** 实现：

1) 先注册一个 provider（`providerId` → 一组固定显示值）
2) 然后在布局里用 `placeFixedSlot(providerId, ...)` 放置一个“真 JEI 槽位”（可被 JEI focus/点击）

### ZenScript：注册 provider

入口：`mods.prototypemachinery.jei.FixedSlotProviders`

- 物品：
    - `FixedSlotProviders.registerItem(providerId, item, replace)`
    - `FixedSlotProviders.registerItems(providerId, items[], replace)`
- 流体：
    - `FixedSlotProviders.registerFluid(providerId, fluid, replace)`
    - `FixedSlotProviders.registerFluids(providerId, fluids[], replace)`
- 清理：
    - `FixedSlotProviders.clear(providerId)`
    - `FixedSlotProviders.clearAll()`

> 建议：如果你的脚本是 `#loader crafttweaker reloadable`，为了避免重载时重复注册，可以在脚本开头先 `clear(providerId)` 或 `clearAll()`。

### ZenScript：放置固定槽位

布局 DSL：`LayoutBuilder.placeFixedSlot(providerId, role, x, y, width, height)`

- `providerId`：你注册 provider 时用的 id（字符串，会转为 `ResourceLocation`）
- `role`：`INPUT` / `OUTPUT` / `CATALYST`（默认 `CATALYST`）
- `width/height`：用于 JEI 的渲染与鼠标交互区域（建议和 renderer 视觉尺寸一致）

---

---

## 高级用法：什么时候需要手写 PMJeiIngredientKindHandler？

大多数情况下 adapter 就够用；你可能在以下场景需要手写 handler：

- 需要根据 `node` 的数据动态计算 `init` 参数（例如容量/比例尺/特殊背景）
- 想对 `IGuiIngredientGroup` 添加 tooltip callback 或 focus override
- 你不想走 `getIngredientsGroup(type)`，而是需要直接操作 JEI 的专用 group（类似 itemStacks/fluidStacks 那种专用 API）

手写 handler 的核心逻辑仍然是：

1) `val group = recipeLayout.getIngredientsGroup(ingredientType)`
2) `group.init(...)`：
    - 简单模式：`init(index, isInput, x, y)`
    - 完整模式：`init(index, isInput, renderer, x, y, width, height, paddingX, paddingY)`
3) `group.set(index, values)`

---

## 推荐注册时机（重要）

- 这些 registry 属于 **客户端集成**，推荐在 addon 的 JEI plugin（`IModPlugin`）初始化阶段注册。
- 参考 PrototypeMachinery 的入口：`integration/jei/PMJeiPlugin.kt`。

一个简单原则：
- 在 JEI categories / wrappers 被实例化之前（通常是 `registerCategories` 阶段）完成注册。

---

## 常见坑位与排查

1) **UI 有槽位，但 JEI 搜索不到**
- 检查：是否实现并注册了 `PMJeiNodeIngredientProvider`。
- 检查：provider 返回的 values 是否为空。

2) **索引写进去了，但页面不显示/显示空**
- 检查：是否实现并注册了 `PMJeiRequirementRenderer`，并在 `declareJeiSlots` 里声明了 slot。
- 检查：kind handler 是否注册；slot.kind.id 是否能在 `JeiIngredientKindRegistry` 找到。

3) **自定义类型渲染/tooltip 崩溃或不显示**
- 这是 JEI 自定义 ingredient 类型本身没有注册好（helper/renderer），不属于 PM registry 能解决的范围。

---

## 关键源码索引（从这里跳代码）

- JEI plugin 入口：`src/main/kotlin/integration/jei/PMJeiPlugin.kt`
- 索引写入：`src/main/kotlin/integration/jei/wrapper/PMMachineRecipeWrapper.kt`
- 类别组装：`src/main/kotlin/integration/jei/category/PMMachineRecipeCategory.kt`
- 默认布局：`src/main/kotlin/integration/jei/layout/DefaultJeiMachineLayout.kt`
- Registries：
  - `src/main/kotlin/integration/jei/registry/JeiIngredientKindRegistry.kt`
  - `src/main/kotlin/integration/jei/registry/JeiNodeIngredientProviderRegistry.kt`
  - `src/main/kotlin/integration/jei/registry/JeiRequirementRendererRegistry.kt`
  - `src/main/kotlin/integration/jei/registry/JeiMachineLayoutRegistry.kt`
    - `src/main/kotlin/integration/jei/registry/JeiFixedSlotProviderRegistry.kt`

- ZenScript：
    - `src/main/kotlin/integration/crafttweaker/zenclass/jei/FixedSlotProviders.kt`
