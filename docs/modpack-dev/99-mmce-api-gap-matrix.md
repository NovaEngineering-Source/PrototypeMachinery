# 99 MMCE 专有 API 对照与实现成本（PM 侧）

这一章不教你写脚本，只做一件事：

- 把 MMCE 脚本里常用的“专有能力”列出来
- 说明在 PM 里：
  - 是否已有等价能力
  - 如果没有，能否用“绕路”实现
  - 如果要在 PM 里补齐，大概需要改哪些代码、成本如何

> 说明：这里的“成本”是工程实现成本（需要改 Kotlin/系统），不是你写脚本的成本。

## A. 配方/机器层能力

| MMCE 能力 | 你脚本里的典型用法 | PM 现状 | 替代做法（不改 PM） | 在 PM 补齐的成本（粗评） |
|---|---|---|---|---|
| machine 内部并行（internal parallelism） | `MachineModifier.setInternalParallelism(name, n)` | 现阶段以机器侧 `processParallelism` 为并行上限；扫描会按 IO 约束选择有效倍数 | 迁移期：把并行控制集中到机器注册（`m.processParallelism(n)`），不要依赖配方侧 `parallelism` requirement | 中：未来若要支持“按配方声明倍率”，应新增 recipe 字段或 overlay/modifier API（而不是 requirement）；并统一与多工位/多进程交互 |
| max parallelism 上限 | `MachineModifier.setMaxParallelism(name, n)` | 同上（机器侧上限） | 同上 | 中：同上 |
| coreThread / 核心工位（固定配方工位） | `FactoryRecipeThread.createCoreThread("...")` / `controller.recipeThreadList` | PM 当前只有“动态进程列表”，缺少“命名固定工位 + 白名单 + 持久状态（含 statusText）+ 工位级动态 modifier/overlay” | 用 `maxConcurrentProcesses` + 拆 recipe group 模拟（不够可控/不可观察） | 高：需要引入 WorkSlot/工位概念并改扫描/执行/存档/同步；规划见第 10 章 |
| 配方优先级/排序 | `RecipeBuilder.newBuilder(..., priority, ...)` | 未见公开 API | 拆 group 或拆机器（不推荐） | 中：recipe manager 需要排序字段 + JEI/扫描一致 |
| voidPerTickFailure | `doesVoidPerTick` | 未见公开 API | 用事务/输出满阻塞语义替代（可能不等价） | 中到高：涉及事务与回滚策略 |
| recipe tooltip 文本 | `addRecipeTooltip("...")` | 未见通用入口（目前 tooltip 主要来自 requirement 语义） | 用 JEI decorator + UI 文本替代 | 中：内建 Tooltip Provider 注册点（provider function），由 provider 按 recipe/context 生成 tooltip 行；JEI/GUI 统一消费 |

## B. 世界环境条件

| MMCE 能力 | 例子 | PM 现状 | 替代做法 | 补齐成本 |
|---|---|---|---|---|
| 高度限制 | `setAltitude(0, 32)` | 未见内置 requirement | 做成独立机器/独立配方组；或用结构 validator 限制成型（注意：validator 是“成型时”，不是每 tick） | 低到中：优先提供“环境检查函数 API”（高度/时间/天气等纯查询）；由脚本/附属模组在不引入脚本 tick 回调的前提下组合成玩法 |
| 时间限制 | `setTime(10, 12999)` | 未见内置 requirement | 同上（拆配方/拆机器） | 低到中：同上 |
| 天气限制 | `setWeather("sunny")` | 未见内置 requirement | 同上 | 低到中：同上 |

## C. 研究/算力玩法

| MMCE 能力 | 例子 | PM 现状 | 替代做法 | 补齐成本 |
|---|---|---|---|---|
| requireResearch | `requireResearch("...")` | 未见 | 用物品/能量/流体模拟研究消耗；或用玩家进度（若有） | 低（本体）：研究系统计划由附属模组实现（本体只保留必要的扩展点/查询 API） |
| requireComputationPoint | `requireComputationPoint(6000F)` | 未见 | 用能量/物品做“算力卡”替代 | 低（本体）：算力玩法计划从 PM 本体移除/不实现，未来以附属模组形式提供（本体只保留必要的扩展点/查询 API） |

## D. 事件/适配器（Adapter）系统

MMCE 有 RecipeAdapterBuilder、各种事件（RecipeTickEvent/CheckEvent）。

PM 当前更偏向“组件 + 系统（ECS）+ 事务化 requirement”。

- 若你需要 MMCE 那种“脚本回调式 tick 事件”，实现成本会比较高，并且要考虑服务器安全与性能。

> TODO：基于 NovaEng-CRL 实际脚本使用情况，统计有多少配方依赖 adapter/event（如果很少，优先用 PM 的现有语义替代；如果很多，再考虑做 API）。
