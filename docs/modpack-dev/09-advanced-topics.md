# 09 高级主题：选择器/校验器/动态匹配/渲染绑定

这一章是“你已经能注册机器与配方了，但还想更像整合包那样做玩法”的进阶内容。

> 说明：这里会比前几章多一点专用名词，但每个都会配一句人话解释。

## 1. 动态物品匹配（Dynamic Item Input）

用途：

- 你想要“输入一个带 NBT 的物品”，但又不想把 NBT 写死成某一个值
- 或者你想做“模糊匹配：只要满足某个 matcher 规则就行”

PM 提供：

- `PMRecipeRequirement.itemDynamicInput(...)`
- 以及 `ItemMatchers`（脚本里定义 matcher）

> TODO：补一段来自 PM 自带示例 `item_dynamic_matcher_demo.zs` 的“最小可运行”版本，并解释 matcherId/preview 的意义。

## 2. 结构校验器（Validator）：让结构不只是“搭对就行”

用途：

- 你希望结构满足额外规则，例如：
  - 必须在某种生物群系
  - 必须在某个高度范围
  - 必须有某个方块实体带特定数据

PM 有 `StructureValidator` 的注册体系，并提供 ZenScript 包装：

- `ZenStructureValidatorRegistry`

> TODO：把 validator 的脚本入口整理成一份“面向整合包作者”的例子（尽量不让你写太多 Java/Kotlin）。

## 3. 给组件传参（pmcomponent 参数/数据）：两种更稳的做法

很多整合包作者会想要类似 MMCE 的体验：

- “我把一个组件加到机器上，同时把它的配置（参数）也写进去”。

但目前 `addComponentType(<pmcomponent:...>)` 更像“声明启用某组件类型”，并不适合直接塞一大坨 NBT 参数。

更推荐的两种方案（规划方向）：

### A. 组件专用的强类型 builder 方法（最直观）

把常用参数做成 `MachineTypeBuilder` 的方法（你在第 03 章已经见过一些）：

- `m.maxConcurrentProcesses(n)`
- `m.processParallelism(limit)`
- `m.executionMode("CONCURRENT")`

优点：类型安全、易读、未来存档/同步好维护。

### B. ZenScript 扩展/DSL（最灵活但需要维护规范）

为每个组件类型做一个对应的扩展方法，把参数写入 machine type（示意）：

- `m.factoryRecipeProcessor(function(cfg){ ... })`
- `m.workSlots(function(cfg){ ... })`

优点：脚本侧写起来像“配置块”，可读性强；缺点是 Kotlin 侧需要维护一套稳定的配置对象与默认值。

> 结论：整合包侧请期待“强类型字段/DSL”，而不是“给 pmcomponent 直接塞 NBT”。

## 4. 选择性修饰（Selective）：让同一条配方在不同情况下“变一变”

用途：

- 同一条配方，根据机器上的某个升级/模式切换，改变消耗或产出

PM 的配方系统支持“overlay/事务”，可以实现这种玩法。

> TODO：补一个“根据开关按钮切换配方倍率”的可运行例子（需要 UI + 选择性修饰 + 配方 overlay）。

## 5. 特殊条件判断：检查函数 +（可选）脚本回调 + 内建复杂函数

你可能需要一些“很难用纯输入输出表达”的条件，例如：

- 高度/时间/天气/维度/生物群系
- 某个方块实体的内部状态
- 世界里某个结构是否存在

当前更推荐的总体思路是分层：

1) **内建复杂判断函数**（Kotlin 侧实现，脚本只传参数）
  - 把边界条件、跨模组兼容、枚举值映射等复杂度收敛到内建函数里。
2) **检查函数 API（纯查询）**
  - 让脚本能读“当前环境是什么”，但不允许脚本每 tick 随意跑复杂逻辑。
3) **脚本回调式条件 API（仍可能提供，但必须严格约束）**
  - 适合极少数确实需要“自定义逻辑”的整合包；需要考虑：性能、线程安全、可复现性、以及服务器滥用风险。

它和 Validator 的区别：

- Validator 更适合“成型时就能判定”的条件（结构搭建阶段）。
- 特殊条件更偏向“运行期每次开工/每 tick 是否允许继续”的条件。

> TODO：给出一个清晰的推荐优先级：能用 Validator 就别用运行期条件；能用内建函数就别用脚本回调。

## 6. 渲染绑定（Render Bindings）：更花哨的机器外观

用途：

- 机器不同状态显示不同模型/动画

PM 提供了一套渲染绑定的脚本入口（`zenclass/render/*`）。

> TODO：整理最小示例（例如绑定 gecko 模型到某个 machineId），并说明它对整合包是不是必需。
