# 01 快速开始：脚本放哪、何时加载、怎么排错

这一章只解决三件事：

1) 脚本/结构文件放哪里；2) 机器/配方/JEI/UI 分别应该在什么时候加载；3) 出错了怎么定位。

## 1. 目录：你真正需要关心的就这两个

### A. 脚本目录（ZenScript）

- 位置：`<游戏目录>/scripts/`
- 建议你给 PM 单独开子目录：`scripts/prototypemachinery/`

典型结构（示例）：

- `scripts/prototypemachinery/machines.zs`（注册机器）
- `scripts/prototypemachinery/recipes.zs`（注册配方）
- `scripts/prototypemachinery/ui.zs`（注册机器 GUI）
- `scripts/prototypemachinery/jei_layouts.zs`（注册 JEI 布局）

### B. 结构目录（JSON）

- 位置：`<游戏目录>/config/prototypemachinery/structures/`
- 会递归扫描子文件夹（你可以按模块拆分）

例如：

- `config/prototypemachinery/structures/power/gas_generator.json`
- `config/prototypemachinery/structures/production/precision_assembler.json`

> 首次运行时，如果这个目录是空的，PM 会自动复制一批示例结构进去，方便你从模板开始改。

## 2. 加载顺序：为什么一定要区分 #loader

一句话：

- **机器注册**要尽量早（否则控制器方块可能来不及注册）。
- **配方/JEI/UI**可以晚一点，而且最好能热重载。

### A. 机器注册（推荐 `#loader preinit`）

示例：

- 文件：`scripts/prototypemachinery/machines.zs`

```zenscript
#loader preinit

import mods.prototypemachinery.MachineRegistry;

val m = MachineRegistry.create("my_pack", "gas_generator");
m.name("Gas Generator");
// 结构 ID 对应结构 JSON 里的 id
m.structure("my_pack:gas_generator");

// 允许这台机器扫描哪些“配方组”（后面配方会用到）
m.addRecipeGroup("my_pack:gas_generator");

// 添加“配方处理器”组件（没有它就不会跑配方）
m.addComponentType(<pmcomponent:factory_recipe_processor>);

MachineRegistry.register(m);
```

### B. 配方/JEI/UI（推荐 `#loader crafttweaker reloadable`）

这样你改脚本后可以在游戏里重载。

本整合环境使用：

- 重载命令：`/ct reload`
- 依赖：需要额外安装 **Zen Utils** 模组（否则该命令不可用/不可热重载）

> 小提示：为了减少“注册顺序”踩坑，建议：
> - 机器注册仍用 `#loader preinit`（不热重载）
> - 配方/JEI/UI 用 `#loader crafttweaker reloadable`（热重载）

## 3. 排错：从“看不见机器”到“机器不跑”

### A. 看不见控制器方块 / 机器没注册

常见原因：

- 机器注册脚本没用 `#loader preinit`
- 机器 id 冲突（重复注册）
- 结构 id 写错

建议做法：

1) 先把脚本拆成两份：`machines_preinit.zs`（只注册机器）+ 其他脚本（配方/JEI/UI）
2) 用最小的机器定义先跑通（只加 recipe processor + recipe group）

### B. 机器能成型但不跑配方

最常见的两点：

- 机器没有 `addRecipeGroup(...)`
- 机器没有 `addComponentType(<pmcomponent:factory_recipe_processor>)`

还有一种：配方写了输入/输出，但结构里没有对应的仓室（hatch）。

### C. JEI 不显示 / 布局不对

- 先不写 layout，让它走默认布局（确认配方本身能被 JEI 索引到）
- 再逐步加 LayoutRegistry 脚本

### D. 结构加载失败

结构 JSON 是从 `config/prototypemachinery/structures/` 读的。

- JSON 语法错误：看日志
- 方块/方块状态写错：通常会在 PostInit 解析阶段报错

> 小技巧：结构先用“全是铁块”的版本跑通，再逐步替换成复杂方块。
