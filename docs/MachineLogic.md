# 机器逻辑与配方架构

本文描述 PrototypeMachinery 的“机器运行时骨架”与“配方执行”设计。

> 说明：当前仓库处于预览/基础设施阶段。部分扫描/匹配逻辑仍是占位实现，但数据结构与扩展点已就位。

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
- 执行模型为事务化：`start` / `acquireTickTransaction` / `onEnd`
  - Failure -> rollback
  - Success/Blocked -> commit

该层是扩展“输入/输出/概率/倍率/可选候选”等复杂行为的主要承载点。

## 配方索引（Recipe Indexing）

为避免高频遍历所有配方，引入索引注册表：

- `IRecipeIndexRegistry` / `RecipeIndexRegistry`
- 通过 `RequirementIndexFactory` 为不同需求类型构建索引

## See also

- [多方块结构系统（MachineStructure）](./Structures.md)
- [属性系统（Machine Attributes）](./Attributes.md)
- [调度器（TaskScheduler）](./TaskScheduler.md)
