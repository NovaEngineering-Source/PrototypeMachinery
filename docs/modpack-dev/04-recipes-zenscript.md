# 04 写配方：物品/流体/能量 + 概率/模糊输入/随机输出/并行

这一章讲如何用 ZenScript 注册 PM 配方。

你可以把 PM 的配方理解成：

- 有一个持续时间（ticks）
- 由一组“需求”（requirements）组成
  - 需求就是：需要消耗什么 / 需要产出什么 / 每 tick 需要多少 / 有概率 / 候选集合 等

> 重要补充：
>
> - **并行（parallelism）理论上不应被理解为“需求”**，它更像“倍率/批量处理修饰器”。
> - 当前实现中，并行的有效倍数由扫描系统根据机器侧 `processParallelism` 上限与 IO 约束自动选择。
> - 脚本层的 `PMRecipeRequirement.parallelism(...)` 因历史原因仍存在，但目前**不作为并行配置/上限来源**（若仍写，主要是占位/展示用途）。

## 1. 你会用到的入口

- `mods.prototypemachinery.recipe.PMRecipeBuilder`
- `mods.prototypemachinery.recipe.PMRecipeRequirement`

## 2. 最小配方（只用于验证机器能扫描/启动）

用于验证机器扫描/启动流程：

```zenscript
#loader crafttweaker reloadable

import mods.prototypemachinery.recipe.PMRecipeBuilder;

val GROUP = "my_pack:gas_generator";

PMRecipeBuilder.create("my_pack:dummy", 40)
    .addRecipeGroup(GROUP)
    .register();

这条配方没有任何 IO，只是为了确认：机器能扫描到 group 并启动进程。
```

## 3. 物品输入/输出

```zenscript
PMRecipeBuilder.create("my_pack:iron_to_gold", 100)
    .addRecipeGroup("my_pack:example")
    .addRequirement(PMRecipeRequirement.itemInput("in", <minecraft:iron_ingot> * 1))
    .addRequirement(PMRecipeRequirement.itemOutput("out", <minecraft:gold_ingot> * 1))
    .register();
```

> 注意：如果你写了 itemInput/itemOutput，你的结构里必须有对应的物品仓（hatch），否则机器没法完成这条配方。

## 4. 流体输入/输出（一次性）

```zenscript
PMRecipeBuilder.create("my_pack:water_to_steam", 200)
    .addRecipeGroup("my_pack:boiler")
    .addRequirement(PMRecipeRequirement.fluidInput("in", <liquid:water> * 1000))
    .addRequirement(PMRecipeRequirement.fluidOutput("out", <liquid:steam> * 1000))
    .register();
```

## 5. 每 tick 输入/输出（流体/能量）

这类配方非常适合“发电机/锅炉/泵”。

### A. 复刻一个 MMCE 示例：gas_generator

你整合包里 MMCE 版本是类似这样的：

- 每 tick 吃水
- 每 tick 出蒸汽
- 每 tick 发电

PM 版本可以写成：

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

## 6. 概率（chance）

物品/流体的输入输出都支持 chance。

```zenscript
PMRecipeBuilder.create("my_pack:chance_demo", 80)
    .addRecipeGroup("my_pack:example")
    .addRequirement(PMRecipeRequirement.itemOutputChance("out", <minecraft:diamond> * 1, 12.5))
    .register();
```

> chance 以“百分比”表示：12.5 表示 12.5%。

## 7. 模糊输入（Fuzzy Input）：一堆东西里任意挑一种

适合做：

- 任意木板
- 任意铜锭
- 任意“可替代材料”

示例：

```zenscript
PMRecipeBuilder.create("my_pack:fuzzy_demo", 60)
    .addRecipeGroup("my_pack:example")
    .addRequirement(PMRecipeRequirement.itemOreDictFuzzyInput("any_copper", 2, "ingotCopper"))
    .register();
```

## 8. 随机输出（Random Output）：从候选里抽奖

示例：从 3 个候选里抽 1 个：

```zenscript
PMRecipeBuilder.create("my_pack:random_demo", 40)
    .addRecipeGroup("my_pack:example")
    .addRequirement(PMRecipeRequirement.itemRandomOutput(
        "loot",
        1,
        [<minecraft:iron_ingot>, <minecraft:gold_ingot>, <minecraft:diamond>],
        [80, 19, 1]
    ))
    .register();
```

## 9. 并行（Parallelism）：一台机器一次跑几份

并行更像“倍产/批量处理”的**倍率**，不是资源类需求。

当前实现中，**有效并行倍数由扫描系统决定**：在不超过机器的 `processParallelism` 上限的前提下，结合输入/输出可用量等约束，选择能跑的最大倍数。

> 注意：脚本层的 `PMRecipeRequirement.parallelism("p", n)` 由于历史原因仍存在，但**不再作为扫描上限/倍率声明的来源**（如果你仍写它，目前主要只起到“占位/展示”的作用）。

这意味着通常你只需要关心机器侧的并行上限（`processParallelism`），并把配方的 IO 设计好即可。

这里有三层概念，建议你按这个顺序理解：

### B. 机器层（单进程）：`processParallelism(limit)`

这是“机器对**单个进程**允许的并行倍数上限”。

- 常用来做整合包平衡：单个进程最多一次做多少份
- 实际有效并行数会受 IO 约束影响（不够就会自动降下来）

你可以在注册机器类型时设置：

```zenscript
#loader preinit

import mods.prototypemachinery.MachineRegistry;

val m = MachineRegistry.create("my_pack", "example_machine");
m.structure("my_pack:example_machine");
m.addRecipeGroup("my_pack:example");
m.addComponentType(<pmcomponent:factory_recipe_processor>);

// 机器层：单个进程最多并行 8 份
m.processParallelism(8);

MachineRegistry.register(m);
```

### C. 机器层（多进程）：`maxConcurrentProcesses(max)`

这是“机器允许**同时跑几个配方进程**”。

- `maxConcurrentProcesses(1)`：一次只跑 1 个进程（默认）
- `maxConcurrentProcesses(2)`：可以同时跑 2 个进程（可能是两条不同配方，也可能是同配方的两个进程，取决于扫描/调度）

多进程与并行（parallelism）不是一回事：

- 并行：一个进程里做多份
- 多进程：同时开多个进程

通常你只需要其中一种：

- “更像倍产/批量处理”的机器：用并行（processParallelism）
- “像流水线/多工位”的机器：用多进程（maxConcurrentProcesses）
