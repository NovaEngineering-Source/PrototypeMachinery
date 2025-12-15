# 机器逻辑与配方架构

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

## See also

- [多方块结构系统（MachineStructure）](./Structures.md)
- [属性系统（Machine Attributes）](./Attributes.md)
- [调度器（TaskScheduler）](./TaskScheduler.md)
