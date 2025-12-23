# 机器逻辑与配方架构

English translation: [`docs/en/MachineLogic.md`](./en/MachineLogic.md)

本文描述 PrototypeMachinery 的“机器运行时骨架”与“配方执行”设计。

本文以当前代码实现为准；若你在阅读时发现文档与实现不一致，请优先相信实现（并欢迎顺手修文档）。

## 代码位置

- 类型与实例：
  - `src/main/kotlin/api/machine/*`
  - `src/main/kotlin/impl/machine/*`
  - 方块实体：`src/main/kotlin/common/block/entity/MachineBlockEntity.kt`

- 组件系统（ECS 变体）：
  - `src/main/kotlin/api/machine/component/*`
  - `src/main/kotlin/impl/machine/component/*`

- 配方与进程：
  - `src/main/kotlin/api/recipe/*`
  - `src/main/kotlin/impl/recipe/*`

## 从 MachineType 到 MachineInstance

- `MachineType`：描述“这个机器是什么”（结构、组件类型等）。
- `MachineBlockEntity.initialize(machineType)`：创建并绑定 `MachineInstanceImpl`。
- formed 状态：多方块匹配成功后，实例会更新 formed 状态，用于渲染与运行许可。

## 组件系统（MachineComponent / MachineSystem）

- 组件通过 `MachineComponentType` 进行声明与构建。
- `MachineSystem` 承担 tick 驱动与系统排序（依赖关系 -> 拓扑排序）。
- 某些组件 `system == null`，代表纯数据组件，不参与 tick。

## 配方模型（MachineRecipe / RecipeProcess）

- `MachineRecipe`：配方本体，主要由按类型分组的 requirements 组成。
- `RecipeProcess`：运行态进程（持有 seed，支持可复现随机；有独立 attributeMap overlay）。

## Requirement Layer（事务化需求系统）

- `RecipeRequirementType` -> 绑定 `RecipeRequirementSystem`

核心接口见：

- `src/main/kotlin/api/recipe/requirement/component/system/RecipeRequirementSystem.kt`
  - `start(process, component): RequirementTransaction`
  - `acquireTickTransaction(process, component): RequirementTransaction`（可选：Tickable）
  - `onEnd(process, component): RequirementTransaction`

事务语义（非常重要）：

- **获取事务（start/tick/end）时就会立即产生副作用**（例如预留物品、扣能量、写入临时状态等）
- 调用方根据 `RequirementTransaction.result` 决定：
  - `Success`：必须 `commit()`
  - `Blocked`：必须 `commit()`（并保持“无副作用”或“可恢复”语义，避免卡死）
  - `Failure`：必须 `rollback()`
- 执行器需要把“一组 requirement”视为一个原子阶段：若任一需求 `Blocked/Failure`，则对本阶段已获取的事务 **整体回滚**。

默认执行器实现参考：

- `src/main/kotlin/impl/machine/component/system/FactoryRecipeProcessorSystem.kt`
  - 按阶段执行：START -> (TICK...)* -> END
  - 对每个阶段收集 transactions，成功则全部 commit，失败/阻塞则反向 rollback
  - 对 requirements 做稳定排序（按 type id）以确保行为可复现/便于测试

该层是扩展“输入/输出/概率/倍率/可选候选”等复杂行为的主要承载点。

### Requirement Overlay（按进程覆写）

支持为单个 `RecipeProcess` 挂载 overlay，在执行 requirement 前解析“生效的组件”。

- 入口：`src/main/kotlin/impl/recipe/requirement/overlay/RecipeRequirementOverlay.kt`
- overlay 组件：`impl/recipe/process/component/RecipeOverlayProcessComponent*`

典型用途：

- 同一份配方在不同进程实例上应用不同的消耗倍率/过滤条件
- 为并行进程提供独立的 requirement 参数视图

### Process Components / Systems（配方进程组件系统）

`RecipeProcess` 也支持组件系统：每个 `RecipeProcessComponentType` 可绑定一个 process system，并在每个 machine tick 执行 pre/tick/post。

- `FactoryRecipeProcessorSystem` 会在每 tick 内调用 `tickProcessComponents(process, Phase.*)`
- 生命周期辅助组件：`impl/recipe/process/component/RecipeLifecycleStateProcessComponent*`（用于标记 started 等状态）

## 配方索引（Recipe Indexing）

为避免高频遍历所有配方，引入索引注册表：

- `IRecipeIndexRegistry` / `RecipeIndexRegistry`
- 通过 `RequirementIndexFactory` 为不同需求类型构建索引

> 注意：这里的“配方索引”指 **运行时扫描加速**（缩小候选配方集合），与 JEI 的 `IIngredients` “索引”（用于搜索/反查）不是一回事。

### 目标与约束

目标：在 `FactoryRecipeScanningSystem` 扫描候选配方时，先用索引做一层**保守预过滤**，减少后续并行度约束（constraint）与事务化执行器的昂贵模拟次数。

约束（重要）：

- 索引必须 **保守**：宁可 false positive（多放进候选），尽量避免 false negative（把能跑的配方过滤掉）。
- 索引只能使用“低成本且可观测”的机器状态。
  - 对 item/fluid：优先使用 key-level storage-backed 容器（`StructureItemStorageContainerComponent` / `StructureFluidStorageContainerComponent`），因为它们能列举资源与数量。
  - 对 capability-backed 容器（`IItemHandler` / `IFluidHandler`）若没有“枚举内容”的 API，索引应视为 **无意见**（`lookup() == null`）或直接禁用该 machineType 的索引。

### 当前语义建议（lookup 返回值）

- `RequirementIndex.lookup(machine)` 返回：
  - `null`：该 index 对当前机器状态“无意见”（例如该机器没有可观测的对应容器）。
  - `emptySet()`：明确表示“当前状态下无任何配方匹配”（强过滤）。
- `RecipeIndex.lookup(machine)` 通过对所有非 null 结果取交集得到候选集。
  - 若所有 index 都返回 null，则 `RecipeIndex.lookup` 的结果应视为“索引不可用”，调用方应回退到普通扫描（而非把它当作 0 候选）。

### 各需求类型的索引规划

#### ITEM（`ItemRequirementComponent`）

构建期（按 machineType 的 recipe 列表构建）：

- Key：使用 `PMKey<ItemStack>` 的“原型等价”（忽略 count）。
- 数据结构：
  - `recipesByInputKey: Map<PMKey<ItemStack>, Set<MachineRecipe>>`
    - 对每个 recipe，把所有 item inputs 的 key 原型映射到该 recipe。
  - （可选增强）`requiredByRecipe: Map<MachineRecipe, Map<PMKey<ItemStack>, Long>>`
    - 对每个 recipe，把 inputs 按 key 聚合计数（1x 并行）。用于 lookup 时做“总量级”的快速过滤。

lookup（从机器读状态并过滤）：

- 仅使用 `structureComponentMap` 中 **PortMode.OUTPUT** 的 item sources。
- 仅统计 storage-backed 容器能列举的资源（`SlottedResourceStorage`）：
  - 汇总 `available[key] = sum(storage.getAmount(key))`。
- 过滤策略：
  - 粗过滤：对 machine 当前拥有的每个 key，把 `recipesByInputKey[key]` union 成候选集。
  - 细过滤（可选）：如果存在 `requiredByRecipe`，要求候选 recipe 的每个 required key 都满足 `available[key] >= required`。

#### FLUID（`FluidRequirementComponent`）

构建期：

- Key：`PMKey<FluidStack>` 原型等价。
- 建议把 `inputs` 与 `inputsPerTick` 都纳入 `recipesByInputKey` 的粗过滤（存在性过滤）。
- 细过滤（总量级）建议仅对 `inputs` 做聚合校验，避免过早引入 perTick 语义导致 false negative；真正能否 tick 由 constraint 与事务执行保证。

lookup：

- 仅使用 **PortMode.OUTPUT** 的 fluid sources。
- 仅统计 storage-backed `ResourceStorage` 的资源与数量。

#### ENERGY（`EnergyRequirementComponent`）

构建期：

- 以 `input`（可选 `input + inputPerTick`）作为“最小能量阈值”建立 recipe -> threshold 的表。
- lookup 时读取机器可用能量（所有 **PortMode.OUTPUT** `StructureEnergyContainer.stored` 的合计），过滤 `stored >= threshold`。

### 封装型需求（flatten / unwrap）

- `CheckpointRequirementComponent(requirement=...)`：建议在索引构建阶段直接解包，令内部 requirement 参与索引。
- `SelectiveRequirementComponent(candidates=[...])`：建议把 candidates 中可识别的 ITEM/FLUID/ENERGY 全部 union 进索引（保守，不会 false negative）。

### Eligibility（何时禁用索引）

建议 `RecipeIndexRegistry.isEligibleForIndexing(machineType)` 至少考虑：

- 机器是否拥有可观测的 storage-backed 容器（否则索引收益很低且容易退化）。
- 是否存在会在运行时动态修改 recipe.requirements 的机制（若存在，索引会变成 stale，应禁用或要求该机制显式宣告“可索引”。）

---

## Item / Fluid 的 Chance + 模糊输入 + 随机输出（设计草案）

本节是后续实现的设计草案，用于约定语义与边界。

### 术语与目标

- **chance 化**：某个输入/输出操作并非必定发生，而是按概率发生；概率可受机械属性影响，且可能超过 200%（等价于“可能发生多次/可并行化”）。
- **模糊输入**：输入是候选集合；检查时选择第一个可用材料并锁定；实际消耗只消耗锁定材料；失败必须事务回滚。
- **随机输出**：输出候选集合；检查时对候选集做一次输出可行性检查；实际输出时从候选集中按权重随机选择 N 个（不放回）进行输出。
- **随机可复现**：所有随机必须使用 `RecipeProcess.seed` 派生，跨重载一致。

### 核心规则（按需求要求固化）

1) 检查输入/输出时：chance 恒视为 100%（即“严格检查”）。
2) 实际输入/输出时：才使用最终概率值。
3) chance 与模糊同时出现：
   - 检查阶段仍按 100% 且始终执行模糊输入/输出的检查。
   - 实际阶段先判定 chance，若失败则直接跳过本次模糊操作（不做 IO）。

### 数据模型（建议扩展字段）

以 Item 为例（Fluid 同理）：

- `inputs`: List<PMKey<ItemStack>>（保持现有语义：确定性输入）
- `outputs`: List<PMKey<ItemStack>>（确定性输出）

新增（建议）：

- `chance`: Double?（百分比语义，例如 50.0 表示 50%）
- `chanceAttribute`: ResourceLocation?（可选：从 `process.attributeMap` 读取的倍率/加成属性）
  - 最终概率：`effectiveChance = baseChance * attrMultiplier`（不做上限封顶，允许 > 200%）。

模糊输入（建议采用“分组”模型以表达多组输入位）：

- `fuzzyInputs`: List<FuzzyInputGroup<ItemStack>>?
  - 每组包含：`candidates: List<PMKey<ItemStack>>`（按顺序优先） + `count: Long`
  - 检查时：按顺序选第一个可满足的 candidate 并锁定。

随机输出（建议）：

- `randomOutputs`: RandomOutputPool<ItemStack>?
  - `candidates: List<WeightedKey<ItemStack>>`（key + count + weight）
  - `pickCount: Int`（每次输出选择 N 个不同候选，不放回）
  - 检查时：按 100% chance、按最大并行度进行一次“严格输出检查”（见下文）。
  - 输出时：按权重不放回选择 `pickCount` 个候选并输出。

### 锁定与事务语义（如何做到“检查时锁定且可回滚”）

建议为 Item/Fluid 引入一个 process-level 组件（例如 `RequirementResolutionProcessComponent`）：

- 存储：`locks[(requirementId, groupIndex, phase)] -> chosenKey`
- 在 requirement system 里：
  - 当一个阶段（start/tick/end）的全部前置模拟检查通过后，再写入 lock。
  - 若本阶段最终返回 `Failure` 并 rollback，则撤销本次写入的 lock。
  - 对 `Blocked`：应保证无副作用（不写入 lock）。

### 随机数派生（确保完全可复现）

使用 `RecipeProcess.getRandom(salt)`：

- salt 建议包含：`requirementId + stage + phase + groupIndex + attemptIndex + tickCounter`。
- 对 per-tick 随机：建议引入一个持久化的 tick 计数 process 组件（每 machine tick +1），用于盐值，避免每 tick 都得到同一随机结果。

### chance 与并行共存时的“更权衡”实现（避免全有或全无）

设：

- 并行度：$k$（由 process 的 `PROCESS_PARALLELISM` 或扫描系统决定）
- 最终概率百分比：$C$（可 > 200），对应 $c = C/100$

我们需要一个“既不稳定、又不极端”的抽样方式。

建议将 $c$ 拆成整数部分与小数部分：

$$c = g + p,\quad g=\lfloor c \rfloor,\; p\in[0,1)$$

把一次需求在并行 $k$ 下的“成功次数”定义为：

$$S = g\cdot k + \mathrm{Binomial}(k, p)$$

- 直观解释：
  - 每个并行实例先保证发生 $g$ 次（当 $c>1$ 时等价于“多次执行”）。
  - 再对每个并行实例做一次额外的伯努利试验，概率为 $p$。
- 性质：
  - $\mathbb{E}[S]=k\cdot c$，满足“概率可超过 200% 且可并行化”的期望。
  - 方差来自二项分布，不会退化成“要么全出要么全不出”。

实现上：

- 使用 `process.getRandom(...)` 派生 RNG。
- 计算：`guaranteed = g * k`，`extra = countSuccesses(k, p)`（对 i=0..k-1 做 p 的伯努利即可；k 一般不大）。
- 最终本次阶段要执行的 IO 倍数：`times = guaranteed + extra`。

注意：由于检查阶段 chance 固定视为 100%，检查将按 `k` 的满额进行，实际执行按 `times` 进行。
这符合“严格检查”的要求。

### 模糊输入/随机输出与 times 的关系

- **模糊输入**：
  - 检查：按 `k` 满额检查并锁定每组输入的 chosenKey。
  - 执行：先根据 chance 得到 `times`；若 `times==0` 则直接跳过；否则按锁定 key 消耗 `count * times`。

- **随机输出**：
  - 检查：按 `k` 满额，且对候选池进行一次“严格输出检查”。
    - 保守策略：按 `pickCount` 选择“最坏情况需求”（例如取 count 最大的 `pickCount` 个候选）做容量模拟，确保无论实际随机选到哪个组合都不阻塞。
  - 执行：先根据 chance 得到 `times`；每次输出循环执行：
    - 使用 RNG 对候选池做不放回加权抽样，得到 `pickCount` 个 key，输出对应 count。
    - 每个“输出循环”内部保证同一候选不会被重复选中。

### 索引与扫描的影响（前瞻）

chance 与随机输出会让“实际 IO”变得更动态，因此：

- 运行时 RecipeIndex（扫描加速）建议仍按“chance=100% 严格检查”的语义来建索引与过滤，避免把配方放进候选却在严格检查阶段卡死。
- eligibility：若某些需求在运行时会动态改变其候选集合（而不是仅随机选择），应禁用索引。

## See also

- [多方块结构系统（MachineStructure）](./Structures.md)
- [属性系统（Machine Attributes）](./Attributes.md)
- [调度器（TaskScheduler）](./TaskScheduler.md)
