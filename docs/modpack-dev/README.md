# PrototypeMachinery 整合包开发者文档（面向脚本作者）

这套文档专门写给“整合包作者 / 脚本作者”。

- 目标：让你**不需要读 Kotlin 源码**，也能用 CraftTweaker（ZenScript）把机器、配方、JEI 展示、GUI 做出来。
- 风格：从简单到高级，尽量少用专用名词；不得不出现的新词会给一句“人话解释”。
- 范围：以 **PrototypeMachinery（PM）** 当前仓库实现为准；如果某个需求在 PM 里暂时没有，我们会用 **TODO** 标出来，并给出“替代做法/实现成本评估”。

## 快速导航（建议按顺序读）

1. [01 快速开始：脚本放哪、何时加载、怎么排错](./01-quickstart.md)
2. [02 结构 JSON：机器长什么样（从方块堆到可识别结构）](./02-structures-json.md)
3. [03 注册机器：把“结构 + 功能”变成一个可放置的控制器](./03-machines-zenscript.md)
4. [04 写配方：物品/流体/能量 + 概率/模糊输入/随机输出/并行](./04-recipes-zenscript.md)
5. [05 仓室（Hatches）：输入输出口与容量平衡](./05-hatches-and-balancing.md)
6. [06 机器 GUI：用 PMUI 搭面板（含 Web Editor 导出）](./06-ui-builders.md)
7. [07 JEI 布局：用脚本控制配方页面怎么摆](./07-jei-layouts.md)
8. [08 从 MMCE 迁移到 PM：对照表 + 迁移套路 + 踩坑](./08-migrate-from-mmce.md)
9. [09 高级主题：选择器/校验器/动态匹配/渲染绑定](./09-advanced-topics.md)
10. [10 重构规划：对标 MMCE coreThread 的“专有配方进程槽位”](./10-mmce-corethread-refactor-plan.md)
99. [99 MMCE 专有 API 对照与实现成本](./99-mmce-api-gap-matrix.md)

## 你需要准备什么

- 你会写 CraftTweaker 的 `.zs` 脚本（会复制粘贴也行）。
- 知道如何找到 Minecraft 目录下的：
  - `scripts/`（放 ZenScript）
  - `config/`（放结构 JSON）

## PM 自带示例在哪里

PM 会在首次运行时把示例脚本复制到：

- `scripts/prototypemachinery/examples/`

示例包括：注册机器、配方处理器闭环、带仓室的配方、JEI 布局演示、UI 示例、动态匹配等。

> 提示：整合包开发时，建议把机器注册脚本用 `#loader preinit`，配方和 JEI/UI 用 `#loader crafttweaker reloadable`，这样可以在游戏里重载脚本，迭代更快。
