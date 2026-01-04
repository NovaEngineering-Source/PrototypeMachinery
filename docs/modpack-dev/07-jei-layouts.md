# 07 JEI 布局：用脚本控制配方页面怎么摆

这一章讲 JEI 展示（配方页面）怎么“摆放”。

你可以把 JEI 布局理解成：

- 输入/输出槽位在页面哪里
- 背景图用哪张
- 是否放箭头、进度条、持续时间等装饰

## 1. 你会用到的入口

- `mods.prototypemachinery.jei.PMJEI`：创建 LayoutBuilder、改全局渲染选项
- `mods.prototypemachinery.jei.LayoutRegistry`：把 layout 绑定到某个机器

## 2. 最小布局：让它先能显示

如果你不注册布局，PM 会用默认布局。

建议调试顺序：

1) 不写布局，确认 JEI 能看到配方
2) 再加布局脚本

## 3. 一个常见布局：把输入/输出摆成网格

```zenscript
#loader crafttweaker reloadable

import mods.prototypemachinery.jei.PMJEI;
import mods.prototypemachinery.jei.LayoutRegistry;

val MACHINE = "my_pack:gas_generator";
val TYPE_FLUID = "prototypemachinery:fluid";
val TYPE_ENERGY = "prototypemachinery:energy";

val layout = PMJEI.createLayoutSized(176, 90)
    .setBackgroundNineSlice("jei_base.png")
    // 放一个流体输入
    .placeFirst(TYPE_FLUID, "INPUT", 10, 10)
    // 放一个流体输出
    .placeFirst(TYPE_FLUID, "OUTPUT", 40, 10)
    // 放一个能量输出
    .placeFirst(TYPE_ENERGY, "OUTPUT", 70, 10)
    // 如果还有剩余节点，自动往下摆
    .autoPlaceRemaining(10, 40, 18, 18);

LayoutRegistry.register(MACHINE, layout);
```

> 说明：`typeId` 是“需求类型”，例如 item/fluid/energy。

## 4. 两个很好用的开关

### A. 概率角标（chance overlay）

- 全局：`PMJEI.setChanceOverlayEnabled(true/false)`
- 布局级：`layout.setChanceOverlayEnabled(true/false)`

### B. 候选展示模式（模糊输入/随机输出）

- `alternatives`：一个槽位轮播候选（默认）
- `expanded`：拆成多个槽位，每个固定显示一个候选

```zenscript
PMJEI.setCandidateSlotRenderMode("expanded");
```

## 5. TODO：补“NovaEng 风格”的实战 JEI 页面

我会基于你整合包里 MMCE 的某个机器（例如 gas_generator 或 precision_assembler），
复刻一个“输入区/输出区/装饰区”布局：

- 背景 nine-slice
- 进度模块 decorator
- 持续时间 decorator
- 输入输出按行对齐

并解释每条 rule 在做什么。
