# 08 从 MMCE 迁移到 PM：对照表 + 迁移套路 + 踩坑

这一章面向“已经有一套 MMCE 脚本工程”的整合包作者。

目标：把你现在的 `scripts/modularmachinery/...` 迁移到 PM 的脚本与结构体系。

## 0. 先说结论：迁移的主战场在哪里

从你提供的 NovaEng-CRL 脚本看，MMCE 侧大量使用：

- `MachineBuilder.registerMachine(...)`
- `RecipeBuilder.newBuilder(...).addXXX().build()`
- `MachineModifier.setInternalParallelism / setMaxParallelism`
- 环境条件：`setAltitude / setTime / setWeather`
- 研究/算力：`requireResearch / requireComputationPoint`
- 额外提示：`addRecipeTooltip`

PM 侧目前“现成可用”的对应能力主要是：

- 机器注册：`MachineRegistry.create(...).structure(...).addRecipeGroup(...).addComponentType(...).register(...)`
- 配方注册：`PMRecipeBuilder + PMRecipeRequirement`
- 高级需求语义：chance / fuzzy_inputs / random_outputs / dynamic matcher
- UI：`PMUI + UIRegistry`（或 Web Editor runtime-json）
- JEI：`PMJEI + LayoutRegistry`（脚本化布局）

而 **环境条件/研究/算力/配方 tooltip** 这类“MMCE 专有玩法层能力”，PM 本体目前不打算一次性全部做成“内置 requirement”。

结合当前规划：

- **算力玩法**：未来会从 PM 本体移除/不实现，转为**附属模组**提供。
- **环境条件**：倾向先提供“检查函数 API”（纯查询），再由脚本/附属模组/组件系统组合成具体玩法（避免脚本每 tick 回调）。
- **Recipe Tooltip**：倾向内建提供，但做成 **provider function**（由 provider 按 recipe/context 动态生成 tooltip 行）。

因此迁移建议是：

1) 先把“结构 + 机器 + 配方基本输入输出”迁移跑通
2) 再逐个补“玩法条件”（必要时新增 PM requirement 类型或 validator）

## 1. 目录迁移：先把脚本拆成三类

从你的现有结构建议拆成：

- `scripts/prototypemachinery/00_machines_preinit.zs`：只注册机器（preinit）
- `scripts/prototypemachinery/10_recipes_reloadable.zs`：注册配方（reloadable）
- `scripts/prototypemachinery/20_jei_layouts_reloadable.zs`：JEI 布局（reloadable）

这样迁移过程中，任何一步出错都好定位。

## 2. 配方迁移示例（真实复刻：gas_generator）

### A. MMCE（现有）

你的脚本里（简化后）是：

- `RecipeBuilder.newBuilder("gas_generator", "gas_generator", 20)`
- `.addFluidPerTickInput(water)`
- `.addFluidPerTickOutput(steam)`
- `.addEnergyPerTickOutput(40000)`

### B. PM（等价写法）

```zenscript
#loader crafttweaker reloadable

import mods.prototypemachinery.recipe.PMRecipeBuilder;
import mods.prototypemachinery.recipe.PMRecipeRequirement;

val GROUP = "my_pack:gas_generator";

PMRecipeBuilder.create("my_pack:gas_generator/water_steam", 20)
    .addRecipeGroup(GROUP)
    .addRequirement(PMRecipeRequirement.fluidInputPerTick("water_in", <liquid:water> * 10000))
    .addRequirement(PMRecipeRequirement.fluidOutputPerTick("steam_out", <liquid:steam> * 10000))
    .addRequirement(PMRecipeRequirement.energyPerTick("eu", 0, 40000))
    .register();
```

### C. 迁移要点

- MMCE 的“machine name”绑定在 RecipeBuilder 里；PM 通过 **recipe group** 间接绑定。
- PM 推荐给每类机器一个 group（例如 `my_pack:gas_generator`）。

## 3. MMCE 的并行设置怎么迁移

你当前脚本大量使用：

- `MachineModifier.setInternalParallelism(machine, N)`
- `MachineModifier.setMaxParallelism(machine, N)`

并行在玩法语义上更像“倍率/批量处理修饰器”，而不是资源需求。

在当前实现中：

- **并行上限由机器侧的 `processParallelism`（属性/脚本 API）决定**。
- 扫描系统会在不超过该上限的前提下，结合 IO 约束计算“本次进程的有效并行倍数”。
- 配方侧的 `PMRecipeRequirement.parallelism("p", N)` 由于历史原因仍存在，但**不再作为上限/倍率声明的来源**（不推荐继续依赖它）。

迁移建议：

- 把 MMCE 的 `setMaxParallelism` / `setInternalParallelism` 迁移为机器注册时的 `m.processParallelism(N)`。

## 4. MMCE 的环境条件（高度/时间/天气）怎么迁移

你脚本里有大量：

- `setAltitude(min, max)`
- `setTime(start, end)`
- `setWeather("sunny"/"raining"/"snowing")`

PM 当前未发现对应的内置 requirement。

可选迁移路线：

- 路线 A（最快落地）：把这些条件做成“不同机器/不同配方组”，由玩家去选择（缺点：不够自动化）
- 路线 B（低到中成本，当前更推荐）：提供“环境检查函数 API”（高度/时间/天气等），并在不引入脚本每 tick 回调的前提下，由原生系统/附属模组把它们组合进玩法逻辑
- 路线 C（仍可能，但需要严格约束）：提供脚本回调式条件 API（需要考虑服务端安全、性能与可复现性）

额外建议：即便提供脚本回调，也应尽量**内建一些逻辑复杂的判断函数**（例如：跨维度/跨天气枚举/边界情况一致性），脚本侧只做组合与参数化，减少每 tick 自由逻辑。

> TODO：根据 NovaEng-CRL 的实际需求比例，选择 A/B/C，并给出实现优先级。

## 5. MMCE 的 requireResearch / requireComputationPoint

PM 当前未看到“研究”或“算力”对应的需求类型。

可选替代：

- 用物品/流体/能量需求模拟（例如消耗某种研究介质）
- 研究：计划由**附属模组**实现（PM 本体不承担该玩法）
- 算力：计划由**附属模组**实现（PM 本体不承担该玩法）

> TODO：先整理你整合包里 research/computation 的数据来源与 UI 展示方式，再决定哪些留在附属模组、哪些需要本体提供“最小扩展点”。

## 6. 迁移踩坑清单

- 机器注册必须尽量放在 `#loader preinit`
- 配方里写了 item/fluid/energy 的输入输出，结构里必须有对应 hatch
- 先跑通最小闭环，再做高级语义（概率/模糊/随机/动态匹配/JEI 布局）
