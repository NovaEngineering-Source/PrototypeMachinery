# 05 仓室（Hatches）：输入输出口与容量平衡

这一章讲 PM 的“仓室”（hatch）。

你可以把 hatch 理解成：

- 机器结构上的**输入/输出口**
- 玩家把物品/流体/能量塞进去，机器从这里取

## 1. 你会用到的入口

- `mods.prototypemachinery.HatchRegistry`：查信息（有哪些等级、容量是多少）
- `mods.prototypemachinery.HatchConfig`：改配置（改容量、槽位数）

## 2. 查仓室信息（脚本里打印/用于计算平衡）

示例：

```zenscript
#loader crafttweaker reloadable

import mods.prototypemachinery.HatchRegistry;

val tiers = HatchRegistry.getTierNames();
// tiers 是字符串数组，例如 ["Lv1","Lv2",...]

val lv2ItemSlots = HatchRegistry.getItemSlotCount(2); // 2 = Lv2（以实际 tiers 为准）
val hvEnergyCap = HatchRegistry.getEnergyCapacity(4);
```

> 小提醒：tier 数字从 1 到 10。

## 3. 改仓室配置（整合包常用：整体调大/调小）

### A. 调物品仓：槽位数

```zenscript
#loader crafttweaker reloadable

import mods.prototypemachinery.HatchConfig;

// 把 5 级的物品仓改成 18 格
HatchConfig.modifyItemHatch(5, 18);
```

### B. 调流体仓：tank 数量 + 每个 tank 容量

```zenscript
// 把 4 级流体仓改成：4 个 tank，每个 64000mB
HatchConfig.modifyFluidHatch(4, 4, 64000);
```

### C. 调能量仓：容量 + 每 tick 最大传输

```zenscript
// 例如：6 级能量仓 500MFE，最大传输 2MFE/t
HatchConfig.modifyEnergyHatch(6, 500000000, 2000000);
```

## 4. 实战：让配方与仓室“对得上”

配方里写了：

- `itemInput(...)` / `itemOutput(...)`
- `fluidInput(...)` / `fluidOutput(...)`
- `energy(...)` / `energyPerTick(...)`

结构里就必须有对应仓室。

这里有一个非常重要的澄清：

> “定义仓室”不需要在结构 JSON 里写一个特殊字段。
> 你只需要在结构范围里 **摆放对应的 hatch 方块**，机器就会把它当成输入/输出口。

换句话说：hatch 本质上就是结构里的一块“功能方块”。

因此开发期最推荐的方式还是：

1) 先把 hatch 方块直接摆进你要的结构里
2) 用扫描器导出结构 JSON
3) 只在导出的 JSON 上做小改（避免手写漏规则导致 hatch 不被允许）

最容易踩的坑是：

- 写了配方，但结构里漏了输出仓 → 机器永远卡在输出满/无法输出

> TODO：补一份“扫描器导出的结构 JSON 里 hatch 对应的方块规则长什么样”的最小示例，并给出截图式坐标说明（来自 NovaEng-CRL 的实际机器）。
