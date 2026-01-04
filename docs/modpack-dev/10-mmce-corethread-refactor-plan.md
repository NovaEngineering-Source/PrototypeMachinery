# 10 重构规划：对标 MMCE coreThread 的“专有配方进程槽位”

这一章不是“怎么写脚本”，而是**给 PM 侧开发/维护者**的一份规划：

- MMCE 的 `coreThread`（核心配方线程）在整合包里经常被用来做什么？
- PM 当前的运行模型缺哪一块？
- 如果要补齐（或提供等价能力），推荐怎么重构，风险与阶段性里程碑是什么？

> 目标是：让后续实现时“有路标”，避免边写边改导致 API 漂移。

## 1. 背景：为什么整合包会依赖 coreThread

在很多 MMCE 整合包里，`coreThread` 通常被当作：

- 机器内部**固定存在的“工位/槽位”**（不是 OS 线程）
- 每个槽位都可以：
  - 长期维持自己的运行态（idle / working / blocked）
  - 绑定一组可运行的配方（白名单/配方集）
  - 具有“固定修正器/固定倍率”等永久数据
- 脚本侧可以拿到这个槽位对象（通过 controller/机器实例），做：
  - 查询状态、展示 UI
  - 在特定事件里动态调整允许的配方或倍率

这些能力的核心价值是：**可控、可观察、可脚本化**。

## 2. PM 当前模型（简述）与缺口

PM 当前的配方执行骨架（详见 `docs/MachineLogic.md`）大致是：

- 机器实例拥有 `FactoryRecipeProcessorComponent`
  - `activeProcesses: List<RecipeProcess>`
  - `maxConcurrentProcesses` 控制“最多同时跑多少个进程”
- `FactoryRecipeScanningSystem` 负责挑选候选配方并启动进程
- `FactoryRecipeProcessorSystem`/`RecipeExecutor` 负责 tick 进程，并通过**事务化 requirement system**推进 START/TICK/END

目前与“coreThread 槽位化”相比的主要缺口是：

1) **缺少命名/固定槽位**
   - 现在的 process 是“列表里动态增删”，不是“固定的 N 个槽位”。

2) **缺少槽位级约束**
   - 例如：slot A 只跑 A 组配方；slot B 只跑 B 组配方。

3) **缺少槽位级可观察对象（脚本可拿到）**
   - 整合包作者常希望：能直接查“第 i 个工位在跑什么、卡在哪”。

4) **缺少槽位级持久数据（NBT）**
   - 例如：永久 modifiers、上一次锁定配方、冷却/重试策略等。

## 3. 设计目标（PM 侧）

### 3.1 必须达成

- 提供一种“固定存在的配方执行工位/槽位”（下文称 **WorkSlot（配方工位）**）：
  - 有稳定的 `name`（用于脚本/GUI/日志）
  - 有清晰的 `status`（至少：IDLE/RUNNING/BLOCKED/FAILED）
  - 可以配置“可运行配方范围”（例如 recipe group 白名单）
  - 状态与配置能写入 NBT（世界存档可靠）

- 尽量复用现有的事务化 requirement 执行模型：
  - 不改变 requirement 的原子提交/回滚语义
  - 不引入脚本 tick 回调（除非明确要做，且有安全方案）

### 3.2 明确不做（non-goals）

- 不追求“真的多线程并行跑多个进程”这一点（那是调度器层面问题）。
  - 这里的槽位是“逻辑槽位”，不是 OS 线程。

- 不把 PM 改回 MMCE 的事件回调式执行器模型。
  - PM 的优势是事务一致性与可复现行为，规划必须守住这一点。

- **不考虑为配方对象提供“工位绑定字段”**（例如 `workSlotName` / `threadName` 之类）。
  - 这会改变“配方定义”的语义边界，并把“路由策略”硬塞进配方对象本身。
  - 迁移与实现策略应落在：
    - recipe group 设计（把配方按工位拆分 group）
    - WorkSlot 的 `allowedRecipeGroups` 白名单
    - 扫描/执行系统按 slot 逐个选配方（路线 A）

## 4. 推荐术语：用“工位/槽位”，而不是 thread

为了避免误解，PM 侧建议把“对标 coreThread 的对象”命名为：

- `WorkSlot` / `RecipeWorkSlot`（中文建议：**配方工位** / **核心工位**）

理由：

- “thread/线程”容易让人以为它是“可有可无的并发加速功能”。
- 但在 MMCE 的真实整合包用法里，它更像“机器天生就有的几个工位/端口”，属于机器的重要组成。

并在文档/脚本里强调：**工位（slot）不是线程**。

## 5. 两条实现路线（对比）

### 5.1 路线 A：扩展现有 `FactoryRecipeProcessorComponent`（推荐）

做法（高层）：

- 在 `FactoryRecipeProcessorComponent` 内新增 `slots: List<ProcessSlotState>`
  - slot 数量与定义来自 machine type（脚本/配置）
- 扫描系统按 slot 逐个尝试启动：
  - 对每个 slot 生成候选配方集合（根据 slot 白名单/组）
  - 为该 slot 启动一个 `RecipeProcess`
  - 把 process 与 slot 绑定（slot.currentProcess = process）
- 执行器 tick 时：按 slot 驱动其 process（仍旧调用现有 requirement systems）

优点：

- 复用最大：现有组件/系统/事务执行几乎不动
- 兼容性好：与 `maxConcurrentProcesses` 的语义能自然对齐

缺点：

- `FactoryRecipeProcessorComponent` 会变得更复杂（需要定义 slot 状态机）

### 5.2 路线 B：新建一套“槽位配方处理器组件”

做法（高层）：

- 新增 `RecipeSlotProcessorComponent`（与 Factory 处理器并列）
- 新增对应扫描系统与执行系统

优点：

- 与现有 Factory 模型解耦，代码更“干净”

缺点：

- 需要复刻/桥接大量现有行为（索引、扫描、事务执行、UI/JEI 对接），总体成本更高

结论：优先路线 A，把“槽位化能力”当作 FactoryRecipeProcessor 的一次增强。

## 6. 数据模型草案

### 6.1 MachineType 侧（静态定义）

每个 slot 需要至少这些字段：

- `name: String`：稳定标识
- `isCore: Boolean`：是否“始终存在”（默认 true）
- `allowedRecipeGroups: Set<ResourceLocation>`（或 allowedRecipes 白名单）
- `restartPolicy`（可选）：
  - `LOCKED_RETRY`：卡住就一直等
  - `RESCAN`：卡住 N tick 后重新选配方

> 说明：allowedRecipeGroups 是最贴合 PM 的绑定方式（PM 本来就强调 recipe group）。

### 6.2 MachineInstance 侧（运行态）

每个 slot 持有：

- `status`
- `currentRecipeId`（可选）
- `processNbt` 或 `RecipeProcess` 引用
- `blockedReason`（可选，便于 debug/GUI）
- `cooldownTicks`（可选）
- `persistentAttributeOverrides`（可选，见下一节）

## 7. “永久 modifiers / 固定倍率”在 PM 的落点

MMCE 常见的“thread permanent modifiers”，在 PM 侧更适合映射为：

- **slot 级 attribute overlay（持久化）**

也就是：

- slot 自己带一个 `MachineAttributeMap`（或一份简单的 type->base 映射）
- 当 slot 创建 `RecipeProcess` 时，把这些 base 应用到 process 的 attribute overlay

这样可以：

- 不破坏 requirement 的事务模型
- 让倍率逻辑继续走 PM 的属性系统（可测试、可缓存）

## 8. 调度（ExecutionMode）与线程安全

PM 已支持机器级 `ExecutionMode`（`MAIN_THREAD` / `CONCURRENT`）。

对“槽位化进程”而言：

- 默认继承机器的 `ExecutionMode`
- 不建议第一版就做 slot 级 ExecutionMode（复杂度高且容易误用）

## 9. 脚本 API 草案（先规划，不承诺立即实现）

### 9.1 机器注册期（MachineTypeBuilder）

建议新增（示意）：

- `m.addWorkSlot("main")`
- `m.addWorkSlot("aux")`
- `m.slot("main").allowRecipeGroup("my_pack:group_a")`

### 9.2 运行期（MachineInstance/Controller 可查询）

建议新增一个只读视图（示意）：

- `controller.getWorkSlots()` -> `WorkSlot[]`
- `slot.isIdle / isWorking / isBlocked`
- `slot.currentRecipeId`

> 重要：运行期 API 一旦暴露，就要考虑客户端同步与存档兼容，因此必须分阶段做。

## 10. 分阶段里程碑（建议）

### Milestone 0：只写迁移建议（已完成一部分）

- 用 `maxConcurrentProcesses` + recipe group 拆分，模拟“多个工位”的玩法（但没有命名槽位与白名单）。

### Milestone 1：最小槽位化（只做 server 逻辑 + NBT）

- MachineType 能定义 slots（name + allowedRecipeGroups）
- MachineInstance 生成 slots，能序列化
- 扫描/执行器按 slot 驱动
- **不做脚本运行期对象**，先在日志/调试信息里可见

验收标准：

- 世界存档后再进，slot 状态不会丢
- 每个 slot 只会跑允许范围内的配方

### Milestone 2：脚本可观察（只读）

- 暴露 `getWorkSlots()` 只读查询
- 同步必要字段到客户端（用于 GUI/overlay）

### Milestone 3：永久 modifiers（slot attribute overlay）

- slot 可持久化一些属性 base
- process 创建时应用

### Milestone 4：更复杂策略

- slot 的 rescan/retry 策略
- slot 的“锁定配方直到完成”的策略
- 性能优化（slot 内候选缓存/索引）

## 11. 风险与兼容性清单

- 存档兼容：
  - slot NBT schema 一旦上线就要版本化
- 客户端同步：
  - 若暴露 GUI，需要设计增量同步字段，避免网络包过大
- 行为一致性：
  - 必须保持 requirement 事务阶段的原子性
- 并发/线程安全：
  - `CONCURRENT` 下要确保 slot 状态变更不产生竞态（通常机器 tick 还是串行，但要明确约束）

## 12. 下一步要补的证据与决定（TODO）

### 12.1 来自 NovaEng-CRL 的真实脚本证据

这部分不是“讲思路”，而是把**真实脚本对能力的硬需求**写出来，避免我们把 coreThread 误当成“并发功能”。

#### 12.1.1 `draconic_reactor.zs`：核心工位 + UI/逻辑强耦合

以 `draconic_reactor.zs` 为例，coreThread 的用法非常“工位化”，并且对迁移方案有直接约束：

1) **机器启动时就声明固定工位，且禁用额外线程**
   - 脚本会把 `maxThreads` 设为 0，然后添加多个 coreThread。
   - 语义上是：只有这些“固定工位”存在，不允许动态创建“额外工位”。

2) **配方通过名字绑定到某个工位**
   - 每条配方会设置 `threadName`（例如“聚变反应核心 / 能源接入端口 / 能源输出端口 / 燃料装填端口”）。
   - 这意味着：仅靠“拆 recipe group + 机器侧白名单”虽然能绕路，但迁移成本较高。
   - 规划建议（按最新决策不做“配方级工位绑定”）：
     - 迁移时把不同工位拆成不同 recipe group（例如 `my_pack:reactor/core`、`my_pack:reactor/input`、`my_pack:reactor/output`）。
     - 在 machine type 的 WorkSlot 定义里，用 `allowedRecipeGroups` 把各组配方路由到对应工位。

3) **脚本会按索引读取工位列表并据此驱动逻辑/GUI**
   - 会出现 `recipeThreadList[0]` 这种用法：
     - 用于 GUI 展示“核心工位是否在工作”
     - 用于其他逻辑判断（例如输出端口的逻辑依赖核心工位是否有 activeRecipe）
   - 规划建议：
     - 运行期查询 API 必须支持：
       - 稳定顺序（定义顺序即对外顺序）
       - 按名称查找（比按索引更稳）

4) **工位对象在回调中可被当作“运行上下文”使用**
   - Tick/Start/Finish 回调能拿到“当前工位”，并访问其 activeRecipe。
   - 甚至会直接修改 activeRecipe 的字段（例如 tick / parallelism 相关）。

这条证据也提示：

- “coreThread 工位化”本身只是迁移的一部分。
- 该脚本还大量依赖 MMCE 的事件回调式执行（每 tick 自定义逻辑、动态 modifier、对世界方块做同步任务）。
  - 若 PM 侧坚持不做脚本 tick 回调（本规划的 non-goals），则需要提供等价的**原生组件/系统扩展点**，否则这种机器很难只靠“requirements”复刻。

#### 12.1.2 `nova-eco-y-series.zs`（eco_y7）：多工位 + 工位级动态 modifier + 工位状态文本

  以 `eco_y7` 为例，脚本同样把 coreThread 当作“机器固有工位”，但它还额外暴露出两类在迁移时很关键的细节：

  1) **多个核心工位（端口/岗位）并存，且明确禁用额外线程**
     - 典型写法：
       - `MachineModifier.setMaxThreads("eco_y7", 0)`
       - `MachineModifier.addCoreThread("eco_y7", FactoryRecipeThread.createCoreThread("能量流控制器"))`
       - `MachineModifier.addCoreThread("eco_y7", FactoryRecipeThread.createCoreThread("机械维护控制器"))`
     - 语义上仍然是：只有这些固定工位存在。

  2) **配方按“工位名”路由（不是按并发数）**
     - 大量配方通过 `.setThreadName("能量流控制器")` / `.setThreadName("机械维护控制器")` 绑定到指定工位。
     - 迁移含义：如果 PM 只提供“slot.allowedRecipeGroups”，整合包侧通常要先把配方按工位拆 recipe group，再配置白名单，迁移成本会明显上升。
     - 由于本规划明确**不做配方级工位绑定**，因此迁移推荐做法是：
       - 把配方按工位拆成不同 recipe group；
       - 用 WorkSlot 的 `allowedRecipeGroups` 把组路由到对应工位；
       - 并在脚本文档里提供一个“threadName -> 推荐 group 命名”的对照套路，降低迁移心智负担。

  3) **工位在运行期会被频繁写入“状态文本”（用于 GUI 反馈）**
     - 在 tick 回调里，脚本会直接做：`thread.setStatusInfo("充能中...")` / `thread.setStatusInfo("正在更换电容...")`。
     - 这意味着：除了 IDLE/RUNNING 这种枚举状态，整合包作者还需要一个“短文本状态”做玩家可读提示。
     - 迁移含义：
       - PM 的 WorkSlot（或 process）最好有一个轻量的 `statusText`（可选字段），并且能同步到客户端 UI。
       - 注意：这不等于允许脚本每 tick 执行；更推荐由**原生系统**写入（脚本只配置策略/字段）。

  4) **工位级动态 modifier（甚至每 tick 变化）**
     - `eco_y7` 在 `FactoryRecipeStartEvent` 与 `FactoryRecipeTickEvent` 里会对当前 thread 反复 `addModifier(...)`：
       - 动态调整能量输入倍率（例如 key 为 `"energyInput"`）
       - 动态调整流体输出倍率/空输出（例如 key 为 `"crystalloidOutput"`）
     - 迁移含义：
       - “只有固定工位”还不够；还需要一个对标能力：
         - **process/工位级的动态 attribute/requirement overlay**（在 tick 阶段可重算，但必须保持确定性与事务一致性）。
       - 在 PM 里更合理的落点是：
         - 原生系统在 `start/acquireTickTransaction/onEnd` 周边，按照机器 NBT/属性计算 overlay，并通过 PM 的 overlay 机制注入。
         - 避免让脚本直接在 tick 回调里改执行器状态（保持 non-goals）。

### 12.2 仍需决定的点

- 明确 PM 侧“slot 白名单”粒度：
  - 用 recipe group 够不够？是否需要 recipeId 白名单？
- 现有 `FactoryRecipeScanningSystem` 的“选配方策略”是否能自然抽象成“按 slot 选一次”？

