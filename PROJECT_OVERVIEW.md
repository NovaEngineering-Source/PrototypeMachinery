# PrototypeMachinery 项目概要

> 本文档由 AI 基于当前仓库代码与改造过程自动生成，旨在为后续维护者提供高层概览与核心逻辑说明。

## 1. 项目定位与目标

PrototypeMachinery 是一个基于 Minecraft Forge 1.12.2 的多方块机械框架模组，核心目标是：

- 提供 **可声明式定义** 的多方块结构（通过 JSON 结构文件）
- 提供 **可扩展的机器类型系统**（MachineType + MachineInstance）
- 支持 **并发执行的机器调度系统**（TaskScheduler + ISchedulable）
- 提供 **可脚本化的 UI 与交互系统**（ModularUI + WidgetDefinition + UIRegistry/UIBindings/UI Actions）
- 通过 **CraftTweaker 集成** 暴露给脚本侧进行机器注册与配置

当前代码处于“预览/基础设施搭建”阶段，已经搭好完整的骨架，方便后续逐步填充具体逻辑。

---

## 2. 核心模块总览

### 2.0 属性系统（Machine Attributes）

属性系统用于表达“机器/进程的数值能力”（如速度、效率、需求倍率等），并支持在不同层级叠加：

- **机器基线（MachineInstance）**：`MachineInstance.attributeMap`（实现：`MachineAttributeMapImpl`）
- **进程叠加（RecipeProcess）**：`RecipeProcess.attributeMap`（实现：`OverlayMachineAttributeMapImpl(parent = owner.attributeMap)`），每个进程可独立叠加 modifiers 而互不影响。

修改器的统一运算顺序为：`ADDITION -> MULTIPLY_BASE -> MULTIPLY_TOTAL`。

序列化方面：

- 机器 attributeMap 全量持久化（base + modifiers）。
- 进程 attributeMap 只持久化 overlay 的 *local changes*（local modifiers + base override），避免把机器基线重复写入每个进程。

更多细节：

- `docs/Attributes.md`
- `docs/Localization.md`

> TODO：当前属性“注册表”仍为临时实现：反序列化主要依赖 `StandardMachineAttributes.getById(...)`，后续应替换为可扩展的全局属性注册表。

### 2.1 机械逻辑与配方架构（重点）

这一节集中介绍机械本体的逻辑管线与配方执行设计，按“类型 → 实例 → 组件 → 配方 → 需求系统”的层次展开。

#### 2.1.1 机器类型与实例化骨架

- **类型注册**：`MachineType` 通过 `MachineTypeRegistryImpl` 统一管理；`MachineTypeRegisterer` 支持在 PreInit 阶段集中写入。
- **实例创建**：`MachineBlockEntity.initialize(machineType)` 会创建 `MachineInstanceImpl`，并将机器的组件、属性、结构等绑定到具体方块实体。
- **形成状态**：`MachineInstanceImpl` 暴露 `isFormed()` 与内部 `setFormed(...)`，供结构校验后更新；`MachineBlock.getActualState` 动态读取此状态以渲染 `FORMED` 属性。

#### 2.1.2 组件系统与执行槽位

- **组件抽象**：`MachineComponent` + `MachineComponentType` + `MachineSystem` 组合描述“机器有哪些能力、由哪个系统按 tick 驱动”。系统间的执行顺序通过拓扑排序自动管理。
  - `MachineComponentType.system` 允许为 `null`：代表该组件不需要 tick/event 系统（例如纯数据存储组件）。
  - `MachineComponentMapImpl` 会在计算 `systems` / tick 顺序时自动过滤掉 `system == null` 的组件。
- **配方处理组件**：`FactoryRecipeProcessorComponent` 是当前的核心执行槽位，职责：
  - 维护 `activeProcesses`（正在运行的配方进程）
  - 控制 `maxConcurrentProcesses`（并发配额，默认取 `StandardMachineAttributes.MAX_CONCURRENT_PROCESSES`）
  - 挂载一组 `RecipeExecutor` 以驱动进程（可插拔执行器）
  - 序列化/反序列化所有进程到 NBT
- **系统驱动**：`FactoryRecipeProcessorComponentType` 绑定到 `FactoryRecipeProcessorSystem`（每 tick 驱动），并可叠加 `FactoryRecipeScanningSystem` 用于自动扫描/开工。

#### 2.1.3 配方定义与注册

- **配方定义**：`MachineRecipe` 仅包含 `id` 和按 `RecipeRequirementType` 分组的 `requirements`，输入/输出一视同仁由需求描述。
- **配方注册表**：`RecipeManagerImpl` 目前是轻量 Map 实现（后续可换 registry）。
- **匹配入口（占位）**：`FactoryRecipeScanningSystem` 预留扫描逻辑：当执行槽位未满时遍历全部配方，判断是否可开工，创建 `RecipeProcessImpl` 并 `startProcess`。

#### 2.1.4 进程（RecipeProcess）生命周期

- **运行态封装**：`RecipeProcessImpl` 绑定 `owner: MachineInstance` 与 `recipe: MachineRecipe`，拥有独立的 `attributeMap`（支持基于机器/配方的倍率、效率等）。
- **状态管理**：`RecipeProcessStatus`（progress/message/isError）提供进度与错误标记；序列化到 NBT 以便区块保存。
- **可复现随机性**：`RecipeProcess` 持有持久化的随机种子 (`seed`)，通过 `getRandom(salt)` 提供确定性的随机数生成器，确保跨会话和回滚逻辑的一致性。
- **组件扩展**：`RecipeProcessComponent` + `RecipeProcessComponentType` + `RecipeProcessSystem` 允许为进程本身挂载可 tick 的子组件（如进度条、缓存等）。

#### 2.1.5 需求类型与系统（Requirement Layer）

- **类型声明**：`RecipeRequirementType<C>` 绑定一个 `RecipeRequirementSystem<C>`，定义“这种需求由哪个系统执行”。
- **需求组件**：`RecipeRequirementComponent` 代表机器侧用于满足该需求的“端口/容器”。
  - **属性配置**：`properties` 映射表支持存储静态配置（如“忽略输出满”、“可选输入”）。
- **处理系统（事务化）**：`RecipeRequirementSystem` 采用 **事务化模型**。
  - **生命周期**：`start`（验证+预扣）、`acquireTickTransaction`（逐 tick 执行）、`onEnd`（完成/产出）。
  - **二阶段事务（commit/rollback）**：所有阶段返回 `RequirementTransaction`。
    - **获取即生效**：在 `start/acquireTickTransaction/onEnd` 获取事务时，核心变更（如扣除/预留资源）就会立即应用。
    - **commit()**：当事务返回非 Failure（Success/Blocked）时必定会被调用，并且实现必须保证成功；用于结算“获取阶段之后才应生效”的效果（例如：选择性包装已确定候选后，触发 Selective modifier 对进程属性施加加速/减速等效果）。
    - **rollback()**：仅当事务结果为 Failure 时才会被调用（并且触发时绝不会调用任何事务的 commit()）；用于撤销获取阶段引入的全部变更，并清理由 commit 阶段注入的可回滚副作用（例如：移除已应用到 `RecipeProcess.attributeMap` 的 attribute modifiers）。
- **高级特性**：
  - **检查点 (Checkpoint)**：`CheckpointRequirementSystem` 允许将需求包装，在特定 tick 原子性地执行完整生命周期（Start -> Tick -> End）。
  - **动态修改器 (Modifiers)**：`RecipeRequirement` 持有 `RecipeRequirementModifier` 列表，允许在执行前动态拦截并修改需求数据（用于实现并行化倍率、随机化变异、机器升级影响等）。
  - **选择性包装 (Selective)**：提供“从多个候选需求中择一”的包装组件，并可在 commit 时触发 modifier。
    - 组件：`impl/recipe/requirement/component/SelectiveRequirementComponent.kt`
    - 系统：`impl/recipe/requirement/component/system/SelectiveRequirementSystem.kt`
    - 进程侧状态（选择结果 + 已应用 modifier 记录）：`impl/recipe/process/component/SelectiveStateProcessComponent.kt` 与 `SelectiveStateProcessComponentType.kt`
    - modifier API：`api/recipe/selective/SelectiveContext.kt`、`SelectiveModifier.kt`、`SelectiveModifierRegistry.kt`
- **返回值模型**：`ProcessResult` 封装成功/失败与本地化错误信息。

#### 2.1.6 执行管线（Processor System）

- `FactoryRecipeProcessorSystem.onTick` 的预期流程：
  1. `component.tickProcesses()` —— 逐个执行器驱动进程
  2. 对每个进程：
     - 检查是否已满足完成条件（占位 `checkCompletion`）
     - 针对所有需求类型调用系统的逐 tick 处理（占位 `tickRequirements`）
     - 完成后调用需求系统的 `onEnd` 做收尾，随后移出队列
- **执行器插拔**：`RecipeExecutor.tick(processor)` 允许不同策略驱动（如同步/异步、优先级排序等）。当前未提供默认执行器，可在构造机器类型时按需注入。

#### 2.1.7 配方索引系统 (Recipe Indexing)

为了优化高频的配方查找操作（避免每 tick 遍历所有配方），引入了配方索引系统：

- **核心接口**：`IRecipeIndexRegistry`（API）与 `RecipeIndexRegistry`（实现）。
- **工作原理**：
  - 在 PostInit 阶段，系统遍历所有机器类型，利用注册的 `RequirementIndexFactory` 构建索引。
  - **RequirementIndex**：针对特定需求类型（如物品、能量）的预计算查找表。例如 `ItemRequirementIndex` 使用 `ItemStackKey` 快速定位包含特定输入的配方。
  - **RecipeIndex**：聚合了该机器类型所有可用的 `RequirementIndex`。
- **查询流程**：机器运行时调用 `RecipeIndex.lookup(machine)`，各子索引返回“潜在匹配配方”的交集，大幅缩小实际匹配范围。
- **扩展性**：模组开发者可实现 `RequirementIndexFactory` 并注册到 `PrototypeMachineryAPI.recipeIndexRegistry`，为自定义需求类型提供索引支持。

#### 2.1.8 设计要点与待办

- **匹配与开工**：需要在 `FactoryRecipeScanningSystem` 中实现实际的“需求可行性检测”（结合 `MachineInstance` 的组件/库存）并创建 `RecipeProcessImpl`。
- **完成判定**：`checkCompletion` 需根据配方时长/需求进度判断；可结合 `RecipeProcessComponent`（如进度条）实现。
- **需求事务**：Tickable 需求应在 `acquireTickTransaction` 中遵循“获取即生效 + commit/rollback 二阶段”模型；当本 tick/本阶段被确认采用时 commit，否则在失败时 rollback。
- **形成状态联动**：结构校验通过后调用 `MachineInstanceImpl.setFormed(true/false)`，使方块状态与渲染实时反映多方块是否完整。

#### 2.1.8 ECS 核心与系统调度

本项目采用了轻量级的 ECS (Entity-Component-System) 架构变体来管理机器行为，确保复杂系统间的交互有序进行。

- **拓扑排序 (Topological Sorting)**：
  - 核心容器 `TopologicalComponentMap` 使用 **Kahn 算法** 维护组件与系统的执行顺序。
  - 确保系统按依赖关系正确执行（例如：结构检查系统必须在机器加工系统之前运行）。

- **系统依赖管理**：
  - 依赖关系下沉至 `MachineSystem` 定义（通过 `runAfter` 和 `runBefore` 属性）。
  - `MachineComponentMap` 自动解析这些依赖，构建有向无环图 (DAG)。支持动态添加/移除依赖。

- **性能优化**：
  - **缓存机制**：排序结果被缓存，仅在组件增删或依赖变更时标记为脏 (dirty) 并重新计算。
  - **基准测试**：经 JMH 验证，在 5000+ 节点的极端场景下，排序耗时仍控制在亚毫秒级 (0.5ms 左右)，完全满足游戏循环需求。

---

### 2.2 多方块结构系统

相关文件：
- `api/machine/structure/MachineStructure.kt`
- `impl/machine/structure/TemplateStructure.kt`
- `impl/machine/structure/SliceStructure.kt`
- `impl/machine/structure/StructureRegistryImpl.kt`
- `api/machine/structure/logic/StructureValidator.kt`
- `api/machine/structure/match/StructureMatchContext.kt`
- `common/structure/loader/StructureLoader.kt`
- `common/structure/serialization/StructureData.kt`
#### MachineStructure 概念

`MachineStructure` 表示一个可匹配的多方块结构单元：

- 关键成员：
  - `id: String`
  - `orientation: StructureOrientation`
  - `offset: BlockPos`
  - `validators: List<StructureValidator>`
  - `children: List<MachineStructure>`
- 关键方法：
  - `fun matches(context: StructureMatchContext, origin: BlockPos): Boolean`
  - `fun transform(rotation: (EnumFacing) -> EnumFacing): MachineStructure`
  - `fun createData(): StructureInstanceData`

#### TemplateStructure — 固定模板结构

`TemplateStructure` 实现了一个普通的“固定形状”结构：

- 基于 `StructurePattern`（一个 `Map<BlockPos, BlockPredicate>`）
- `matches(context, origin)` 逻辑：
  1. 调用 `context.enterStructure(this)` 初始化实例数据
  2. 根据 `offset` 计算 `offsetOrigin = origin + offset`
  3. 遍历 `pattern.blocks`，对每个 `(relativePos, predicate)`：
     - 计算 `actualPos = offsetOrigin + relativePos`
     - 调用 `predicate.matches(context, actualPos)`
  4. 运行所有 `validators`，传入 `offsetOrigin`
  5. 递归匹配所有 `children` 结构（使用 `offsetOrigin` 作为基准）
  6. 调用 `context.exitStructure(matched)`，若成功则构建 `StructureInstance` 并挂载到父级

#### SliceStructure — 切片结构

`SliceStructure` 用于定义可重复堆叠的结构（例如高塔、多层机壳）：

- 关键字段：
  - `pattern: StructurePattern`
  - `minCount: Int`
  - `maxCount: Int`
  - `sliceOffset: BlockPos = BlockPos(0, 1, 0)` — 每层的偏移向量
- 数据容器：`SliceStructureInstanceData(orientation)` 实现 `StructureInstanceData`：
  - `var matchedCount: Int` — 实际匹配到的层数

`matches(context, origin)` 逻辑：

1. 调用 `context.enterStructure(this)`
2. `offsetOrigin = origin + offset`
3. 初始化 `matchCount = 0`，`currentPos = offsetOrigin`
4. 在 `0 until maxCount` 循环：
   - 检查 `pattern` 在 `currentPos` 是否匹配
   - 若匹配，`matchCount++`，并 `currentPos += sliceOffset`
   - 若不匹配，跳出循环
5. 若 `matchCount !in minCount..maxCount`，返回 false
6. 更新 `context.currentMatchingData.matchedCount = matchCount`
7. 执行 `validators`（位置为 `offsetOrigin`）
8. 计算子结构原点（**挂载到最后一层切片**）：
   - `accumulatedOffset = sliceOffset * (matchCount - 1)`
   - `childOrigin = offsetOrigin + accumulatedOffset`
   - 对每个 `child.matches(context, childOrigin)`
9. 调用 `context.exitStructure(matched)`

#### StructureMatchContext 与实例构建

`StructureMatchContext` 不仅用于提供世界访问，还负责在匹配过程中构建运行时的结构实例树：

- **上下文栈**：维护 `MatchingFrame` 栈，支持嵌套结构匹配。
- **数据隔离**：每个结构进入时创建独立的 `StructureInstanceData`。
- **实例树构建**：当 `exitStructure(true)` 被调用时，当前结构的 `StructureInstance` 会被创建并自动添加到父结构的子列表中。最终根结构的实例可通过 `getRootInstance()` 获取。

#### StructureValidator & offset 传递

`StructureValidator` 目前有签名：

```kotlin
fun validate(context: StructureMatchContext, offset: BlockPos): Boolean
```

所有结构在调用验证器时都会将“本结构的基准位置（offsetOrigin）”传入，方便验证器做：
- 范围检测
- 特殊方块存在性检查
- 环境条件判断等

---

### 2.3 结构序列化与加载

相关文件：
- `common/structure/serialization/StructureData.kt`
- `common/structure/loader/StructureLoader.kt`
- 资源示例：`src/main/resources/assets/prototypemachinery/structures/examples/*.json`

#### StructureData 序列化结构

`StructureData` 是结构定义的可序列化数据模型：

- 通用字段：
  - `id: String`
  - `type: String` — "template" / "slice"
  - `offset: BlockPosData`
  - `pattern: List<StructurePatternElementData>`
  - `validators: List<String>`
  - `children: List<String>` — 子结构的 ID
- slice 专用字段：
  - `minCount: Int?`
  - `maxCount: Int?`
  - `sliceOffset: BlockPosData?`

#### StructureLoader 生命周期

`StructureLoader` 在模组生命周期中承担两阶段任务：

1. **PreInit** — `loadStructureData(event: FMLPreInitializationEvent)`：
   - 扫描 `config/prototypemachinery/structures/` 下的 JSON 文件
   - 使用 `kotlinx.serialization` 解析为 `StructureData`
   - 存入 `structureDataCache`
   - 如果目录不存在则创建
   - 如果无文件，则从资源中复制示例结构到 `structures/examples/`

2. **PostInit** — `processStructures(event: FMLPostInitializationEvent)`：
   - 将 `StructureData` 转为 `MachineStructure` 实例
   - 解析 `pattern` 为 `StructurePattern`
   - 通过 ID 解析 `children` 结构，检测循环依赖
   - 使用 `StructureRegistryImpl.register()` 注册结构

#### 示例结构文件

所有示例结构位于资源路径：

- `assets/prototypemachinery/structures/examples/`
  - `simple_machine.json`
  - `slice_machine.json`
  - `complex_machine.json`
  - `child_structure.json`
  - `parent_with_child.json`
  - `README.md`

在第一次运行时，如 config 下结构目录为空，`StructureLoader` 会将这些示例复制到：

- `config/prototypemachinery/structures/examples/`

方便用户以此为模板编写自定义结构。

---

### 2.4 CraftTweaker 集成

相关文件：
- `integration/crafttweaker/ICraftTweakerMachineType.kt`
- `integration/crafttweaker/CraftTweakerMachineTypeBuilder.kt`
- `integration/crafttweaker/zenclass/ZenMachineTypeBuilder.kt`
- `integration/crafttweaker/zenclass/ZenMachineRegistry.kt`
- `integration/crafttweaker/zenclass/data/ZenMachineData.kt`
- `integration/crafttweaker/zenclass/ui/PMUI.kt`
- `integration/crafttweaker/zenclass/ui/UIRegistry.kt`
- `integration/crafttweaker/zenclass/ui/UIBindings.kt`
- `integration/crafttweaker/zenclass/ZenSelectiveContext.kt`
- `integration/crafttweaker/zenclass/ZenSelectiveModifiers.kt`
- `common/integration/crafttweaker/CraftTweakerExamples.kt`
- `common/handler/CraftTweakerReloadHandler.kt`
- 资源脚本示例：`assets/prototypemachinery/scripts/examples/machine_registration.zs`
- 资源脚本示例：`assets/prototypemachinery/scripts/examples/machine_ui_example.zs`

#### ICraftTweakerMachineType

- 表示从脚本侧构建的机器类型包装：
  - `id: ResourceLocation`
  - `name: String`
  - `structure: MachineStructure`
  - `componentTypes: Set<MachineComponentType<*>>`

#### CraftTweakerMachineTypeBuilder

面向集成为脚本侧提供构建器：

- `name(name: String)` — 设置显示名
- `structure(structure: MachineStructure)` — 直接传入结构实例
- `structure(structureId: String)` — **通过 ID 延迟解析结构**（推荐）：
  - 延迟到首次访问 `structure` 时，从 `StructureRegistryImpl` 获取
  - 避免结构/脚本加载顺序问题
- `addComponentType(componentType: MachineComponentType<*>)`
- `build(): ICraftTweakerMachineType` — 返回延迟加载结构的实现 `CraftTweakerMachineTypeImpl`

补充：`CraftTweakerMachineTypeBuilder` 会默认包含 `ZSDataComponentType`，确保脚本侧一定有可用的“机器数据组件”（UI bindings / 自定义数据写回依赖它）。

`CraftTweakerMachineTypeImpl`：
- 持有 `structureProvider: () -> MachineStructure`
- `override val structure` 使用 `by lazy` 延迟解析
- 在解析失败时抛出带有详细信息的 `IllegalStateException`

#### ZenMachineTypeBuilder

ZenScript 暴露类：`@ZenClass("mods.prototypemachinery.MachineTypeBuilder")`

- `name(String)`
- `structure(MachineStructure)`
- `structure(String structureId)` — 字符串重载，对应内部的 ID 解析版本
- `addComponentType(MachineComponentType)`
- `internal fun build(): ICraftTweakerMachineType`

#### 2.4.1 脚本数据：MachineData / ZSDataComponent

为了让脚本侧能以类似 NBT/Map 的方式存取机器数据，本项目提供了两层抽象：

- **ZenScript 数据容器**：`mods.prototypemachinery.data.MachineData`（实现：`integration/crafttweaker/zenclass/data/ZenMachineData.kt`）
  - 底层使用 `NBTTagCompound` 存储，便于序列化与网络同步。
  - 支持 `data["key"]` / `data.key` 的读写（`INDEXGET/INDEXSET` + member getter/setter）。
  - 提供常用类型化访问器：`getDouble/setDouble`、`getInt/setInt`、`getBool/setBool`、`getString/setString`。
- **机器组件承载**：`ZSDataComponent`（机器级）与 `ZSDataProcessComponent`（进程级）
  - 组件实现会在数据变更时触发 `MachineInstance.syncComponent(...)`，将同步粒度简化为“组件级全量同步”。
  - 这类组件的 `system` 为 `null`，不参与 tick 驱动，仅作为“可序列化 + 可同步的数据容器”。

#### 2.4.2 脚本 UI：PMUI / UIRegistry / UIBindings

本项目引入了一套“定义式 UI + 运行时覆盖”的脚本接口，主要由三个入口组成：

- **UI 构建 DSL**：`mods.prototypemachinery.ui.PMUI`
  - 提供 `Panel/Row/Column/Grid` 等布局构建器，以及 Button/Slider/ProgressBar/DynamicText/Slot 等组件构建器。
  - 脚本构建结果会被编译为 `WidgetDefinition`（见 `api/ui/definition/*`）。
- **UI 注册表**：`mods.prototypemachinery.ui.UIRegistry`
  - 将脚本构建的 UI 注册到 `PrototypeMachineryAPI.machineUIRegistry`。
  - 支持 `priority`：优先级更高的注册会覆盖较低优先级；相同优先级下后注册覆盖先注册。
- **数据绑定注册表**：`mods.prototypemachinery.ui.UIBindings`
  - 将 UI 中使用的字符串 key 解析为服务端 getter / setter，并接入 ModularUI 的同步值（如 `DoubleSyncValue`）。
  - 常见用法是把 Slider 的写入回传服务端，并存到 `ZSDataComponent` 中。

运行时 UI 的选择顺序（见 `MachineBlockEntity.buildUI`）：
1) 机器组件中的 `UIProviderComponent`（如果机器类型通过组件提供了 UI）
2) `MachineUIRegistry`（脚本/模组运行时注册、可覆盖）
3) `MachineType.uiDefinition`（机器类型静态默认 UI）
4) `DefaultMachineUI`（内置默认 UI）

#### 2.4.3 UI 行为（client -> server）：UIActionRegistry

为了支持“按钮点击/快捷操作”等需要 client -> server 的交互，本项目提供：

- `PrototypeMachineryAPI.uiActionRegistry`：按 `machineId + actionKey`（或全局 actionKey）注册处理器。
- 网络包：`common/network/PacketMachineAction.kt` 将 `pos + actionKey + payload(NBT)` 从客户端发送到服务端并执行。
- 内置回退动作：`prototypemachinery:toggle_bool:<bindingKey>`
  - 若未注册显式 action，会尝试解析一个可写的 bool binding 并将其取反。

#### 2.4.4 CraftTweaker 脚本热重载（ZenUtils）

为适配 CraftTweaker 脚本 reload（依赖 ZenUtils 的 `ScriptReloadEvent`），项目在 PreInit 中注册了 `CraftTweakerReloadHandler`：

- 在 `ScriptReloadEvent.Pre` 阶段自动清理以下注册表，避免脚本重复加载导致堆叠：
  - `MachineUIRegistry`
  - `UIBindingRegistry`
  - `UIActionRegistry`

#### 示例脚本与自动复制

资源路径：`assets/prototypemachinery/scripts/examples/`
- `machine_registration.zs`
- `README.md`

运行时：

- `CraftTweakerExamples.initialize(event)` 在 PreInit 中被调用
  - 检测 `Loader.isModLoaded("crafttweaker")`
  - 如果存在且 `config/../scripts/prototypemachinery/examples/` 为空：
    - 复制示例脚本到该目录

示例脚本中演示了：
- `structure("example_simple_machine")`
- `structure("example_slice_machine")`
- `structure("example_complex_machine")`
- `structure("example_parent_with_child")`

并在注释中说明使用 ID 方式进行延迟加载是推荐做法。

#### 选择性修改器（Selective Modifiers）

- ZenScript 注册入口：`@ZenClass("mods.prototypemachinery.SelectiveModifiers")`
- 运行时上下文：`@ZenClass("mods.prototypemachinery.SelectiveContext")`
  - `id()` 返回配方内的 `selectionId`
  - 可通过 `addProcessSpeed(...)` / `mulProcessSpeed(...)` 对进程速度等属性施加 modifier（底层写入 `RecipeProcess.attributeMap`）

---

### 2.5 方块与物品注册流水线

相关文件：
- `common/block/MachineBlock.kt`
- `common/block/entity/MachineBlockEntity.kt`
- `common/registry/BlockRegisterer.kt`
- `common/registry/ItemRegisterer.kt`
- `common/CommonProxy.kt`

#### MachineBlock

- 继承自 `BlockContainer`
- 关键属性：
  - `FACING: PropertyEnum<EnumFacing>` — 水平朝向
  - `TWIST: PropertyInteger` — 旋转角度 (0-3)，配合 FACING 实现 24 方向旋转
  - `FORMED: PropertyBool` — 多方块是否形成（**不存入 meta，动态计算**）
- 初始化：
  - 设定硬度、抗性、采集工具等级、音效等
  - `defaultState` 包含 FACING, TWIST, FORMED
  - `registryName = ResourceLocation(machineType.id.namespace, machineType.id.path + "_controller")`
- BlockState 编码：
  - `createBlockState()` — 包含 FACING, TWIST, FORMED
  - `getStateFromMeta(meta)` / `getMetaFromState(state)` — 编码 6 向 Facing 和 4 向 Twist 到 Meta (需自定义逻辑或扩展 Meta 存储)
  - `getActualState(state, world, pos)` — 动态查询 TileEntity 更新 `FORMED` 属性
- 渲染与方块实体：
  - 自动生成 BlockState JSON 和 Item Model JSON（如果启用资源生成）
  - 渲染层：`CUTOUT`
  - 渲染类型：`MODEL`
  - 非不透明方块
  - `createTileEntity` / `createNewTileEntity` 返回新的 `MachineBlockEntity`

#### MachineBlockEntity

- 持有 `public lateinit var machine: MachineInstanceImpl`
- `initialize(machineType)`：
  - 创建 `MachineInstanceImpl(this, machineType)`
  - 在服务端注册到 `TaskSchedulerImpl`
- `update()` 中预留了机器 tick 处理逻辑

#### BlockRegisterer

- 由 `CommonProxy` 在初始化时注册到 `MinecraftForge.EVENT_BUS`
- 在 `RegistryEvent.Register<Block>` 中：
  - 从 `MachineTypeRegistryImpl.all()` 获取所有机器类型
  - 为每个类型创建 `MachineBlock(machineType)`
  - 注册到 Forge Block 注册表
  - 缓存入 `machineBlocks` Map，用于后续 Item 注册

#### ItemRegisterer

- 在 `RegistryEvent.Register<Item>` 中：
  - 通过 `BlockRegisterer.getAllMachineBlocks()` 获取所有机器方块
  - 为每个方块创建 `ItemBlock(machineBlock)`：
    - `registryName = machineBlock.registryName`
    - `setUnlocalizedName(machineBlock.unlocalizedName)`
  - 注册到 Forge Item 注册表

如此，所有通过 `MachineType` 注册的机器类型都会自动获得：
- 一个控制器方块
- 一个对应的物品形态（ItemBlock）

#### Hatch Blocks（Item/Fluid/Energy 仓位方块）

除机器控制器外，项目还内置了一套 **10 级 Hatch 系统**，用于为多方块机器提供外部 I/O 能力（物品 / 流体 / 能量）。

相关代码入口：

- 注册器：`common/registry/HatchRegisterer.kt`
- 方块：
  - 物品：`common/block/hatch/item/ItemHatchBlock.kt`、`ItemIOHatchBlock.kt`
  - 流体：`common/block/hatch/fluid/FluidHatchBlock.kt`、`FluidIOHatchBlock.kt`
  - 能量：`common/block/hatch/energy/EnergyHatchBlock.kt`

注册与命名规则（Tier 1..10）：

- 物品：
  - `item_input_hatch_<tier>`
  - `item_output_hatch_<tier>`
  - `item_io_hatch_<tier>`
- 流体：
  - `fluid_input_hatch_<tier>`
  - `fluid_output_hatch_<tier>`
  - `fluid_io_hatch_<tier>`
- 能量：
  - `energy_input_hatch_<tier>`
  - `energy_output_hatch_<tier>`
  - `energy_io_hatch_<tier>`

> 注意：当前 Hatch 方块 **不再携带 facing（朝向）方块状态**。也就是说，这些方块的 BlockState 不需要（也不应该）再配置 `facing=north/east/...` 之类的变体。

资源文件约定（Forge 1.12.2 标准 BlockState/Model 管线）：

- BlockState（已补齐 90 个）：
  - 路径：`src/main/resources/assets/prototypemachinery/blockstates/`
  - 文件名：与注册名一致，例如 `item_input_hatch_1.json`
  - 统一使用 `variants.normal` 指向同名模型（不带任何属性）

- Block 模型（两层结构，便于兼容与重用）：
  1) **标准名入口模型**（已新增 90 个）
     - 路径：`src/main/resources/assets/prototypemachinery/models/block/`
     - 文件名：与注册名一致，例如 `models/block/item_input_hatch_1.json`
     - 内容：仅 `parent` 指向旧的分目录模型（见下一条），用于“把模型文件名标准化”同时避免破坏已有资源结构。
  2) **既有分目录模型（按类型/模式 + lv1..lv10）**
     - 路径示例：
       - `models/block/item_hatch/input/lv1.json`
       - `models/block/fluid_hatch/output/lv10.json`
       - `models/block/energy_hatch/io/lv5.json`

- ItemBlock 模型（已补齐 90 个）：
  - 路径：`src/main/resources/assets/prototypemachinery/models/item/`
  - 文件名：与注册名一致，例如 `models/item/item_input_hatch_1.json`
  - 内容：`parent` 指向对应的 block 模型，确保物品栏/手持渲染正确。

---

### 2.6 Scheduler & 并发执行（补充，后置）

相关文件：
- `api/scheduler/ISchedulable.kt`
- `api/scheduler/TaskScheduler.kt`
- `impl/scheduler/TaskSchedulerImpl.kt`
- `impl/MachineInstanceImpl.kt`
- `common/block/entity/MachineBlockEntity.kt`

**定位**：提供统一调度中心，让实现 `ISchedulable` 的对象按需在主线程或线程池运行。

核心要点：
- `TaskSchedulerImpl` 监听 `ServerTickEvent`（END），按 `ExecutionMode` 分流：
  - `MAIN_THREAD`：事件线程直接执行
  - `CONCURRENT`：投递至固定大小线程池
- **Scheduling Affinity（并发亲和分组）**：为降低并发执行时的资源竞争风险，引入 `SchedulingAffinity`。
  - 任务可提供一组“亲和 key”，调度器会把共享任意 key 的任务分到同一单线程 lane 中执行（同组串行、组间并行）。
  - `MachineInstanceImpl` 通过遍历组件中的 `AffinityKeyProvider` 来收集 key（并缓存，组件表变更时自动失效）。
- 支持自动重启线程池以兼容单人游戏多次加载；`shutdown()` 在 `PrototypeMachinery.serverStopping` 调用。
- `MachineInstanceImpl` 默认 `ExecutionMode.CONCURRENT`，在 `MachineBlockEntity.initialize` 时注册调度，在 `invalidate` 时移除。

后续落地建议：在 `onSchedule()` 内衔接配方扫描/执行与结构检查；若需要强一致性，可在关键路径强制使用 `MAIN_THREAD`。

### 2.7 API 统一访问点 (PrototypeMachineryAPI)

为了简化开发体验，所有核心注册表和管理器均通过 `PrototypeMachineryAPI` 单例对象暴露：

- **`machineTypeRegistry`**: 机器类型注册与查找。
- **`structureRegistry`**: 多方块结构定义与验证。
- **`recipeManager`**: 机器配方管理。
- **`recipeIndexRegistry`**: 配方索引注册表（管理索引工厂与构建）。
- **`recipeRequirementRegistry`**: 配方需求类型与系统注册。
- **`selectiveModifierRegistry`**: 选择性需求修改器注册。
- **`taskScheduler`**: 任务调度器。
- **`machineUIRegistry`**: 机器 UI 运行时注册/覆盖（脚本/模组）。
- **`uiBindingRegistry`**: UI 数据绑定注册与解析（key -> getter/setter）。
- **`uiActionRegistry`**: UI 行为注册与处理（client -> server）。

推荐在 Java/Kotlin 代码中始终通过此入口点访问各子系统，而非直接使用实现类。

### 2.8 资源键系统 (PMKey System)

为了解决 Minecraft 原生对象（如 `ItemStack`）作为 Map 键时性能低下且易出错（可变性）的问题，引入了类似 AE2 的资源键系统：

核心思想：将“可变的资源对象”（`ItemStack` / `FluidStack`）拆分为 **不可变的唯一原型 (UniqueKey)** + **可变数量 (count)** 两部分。

- **核心目标**：提供不可变、内存唯一（Interned）的资源标识符，用于高性能查找与缓存。
- **PMKey**：所有资源键的基类（`api/key/PMKey.kt`）。
  - `count: Long` 表示数量（对物品是 item count；对流体是 mB）。
  - `internalHashCode` 必须仅由唯一原型导出，忽略 `count`。
- **PMKeyType**：键类型与反序列化入口（`api/key/PMKeyType.kt`）。

#### 2.8.1 PMItemKey（ItemStack）

相关文件：
- `impl/key/item/PMItemKey.kt`
- `impl/key/item/PMItemKeyImpl.kt`
- `impl/key/item/PMItemKeyType.kt`
- `impl/key/item/UniquePMItemKey.kt`

要点：
- **唯一原型**：`UniquePMItemKey` 持有一个 `ItemStack` 原型，并基于 `Item + meta + NBT` 计算哈希。
  - 哈希策略结合 `System.identityHashCode(Item)` 与 meta 位运算，并融合 NBT hash，降低冲突。
  - `equals` 使用 `ItemStack.areItemStacksEqual`（比较 Item/meta/NBT）。
- **驻留 (Interning)**：`PMItemKeyType` 使用 `WeakHashMap<UniquePMItemKey, WeakReference<UniquePMItemKey>>`。
  - 通过“临时 key 查找规范 key”的方式去重。
  - 为避免引用可变 NBT，intern 时对原型执行 `copy()`（深拷贝）。
  - 允许在 `PMItemKeyImpl.equals` 中使用 `uniqueKey === other.uniqueKey`（引用相等），以获得更快比较。
- **数量语义**：`PMItemKeyImpl.count` 为逻辑数量；`get()` 会将数量钳制到 `Int.MAX_VALUE` 以生成可用的 `ItemStack`。

#### 2.8.2 PMFluidKey（FluidStack）

相关文件：
- `impl/key/fluid/PMFluidKey.kt`
- `impl/key/fluid/PMFluidKeyImpl.kt`
- `impl/key/fluid/PMFluidKeyType.kt`
- `impl/key/fluid/UniquePMFluidKey.kt`

要点：
- **唯一原型**：`UniquePMFluidKey` 持有一个 `FluidStack` 原型。
  - 哈希策略：`System.identityHashCode(Fluid)` 与 NBT hash 组合。
  - `equals` 使用 `FluidStack.isFluidEqual`（比较 Fluid 与 NBT，忽略 amount）。
- **驻留 (Interning)**：`PMFluidKeyType` 同样使用 `WeakHashMap<UniquePMFluidKey, WeakReference<UniquePMFluidKey>>`。
  - intern 时将 `amount` 临时归一化为 1，再构建 key；入池前用 `copy()` 深拷贝，避免持有可变引用。
  - 生成 `PMFluidKeyImpl` 时，`count` 以 mB 计。
- **序列化**：
  - `PMFluidKeyImpl.writeNBT` 写入 `FluidStack` 数据并附加 `PMCount`。
  - `PMFluidKeyType.readNBT` 通过 `FluidStack.loadFluidStackFromNBT` 反序列化，并读取 `PMCount`（若不存在则回退为原 amount）。

此系统是 **配方索引系统** 的基石，确保了在处理大量物品/流体资源时的检索效率与稳定性。

### 2.9 默认原生 UI（ModularUI）

为机器提供了一个“开箱即用”的默认 GUI，并为后续整合包/扩展开发预留了空间。

相关文件与资源：
- `src/main/kotlin/client/gui/DefaultMachineUI.kt`
- `src/main/resources/assets/prototypemachinery/textures/gui/gui_controller_a.png`（主页面背景）
- `src/main/resources/assets/prototypemachinery/textures/gui/gui_controller_b.png`（扩展页面背景）
- `src/main/resources/assets/prototypemachinery/textures/gui/states.png`（标签页按钮贴图）

已实现的 UI 功能：
- **双标签页结构**：使用 `PagedWidget` + `PageButton` 实现页面切换。
  - 默认打开为 **主页面**。
  - 切换页时自动切换面板背景（A/B 两张 controller 贴图）。
- **主页面（Page 1）**：
  - 包含玩家物品栏（`SlotGroupWidget.playerInventory(...)`）。
  - 预留了机器信息区与 4x4 网格槽位（当前为占位演示，便于后续接入真实组件槽位）。
- **扩展页面（Page 2）**：
  - 使用 `gui_controller_b.png` 背景。
  - **不包含玩家物品栏**，保留为空白内容区，方便整合包作者/机器扩展在此添加自定义控件。
- **隐藏原版槽位背景**：通过 `SlotGroupWidget.SlotConsumer` 将 `ItemSlot.background(IDrawable.EMPTY)` 应用于玩家背包槽位，避免原版 slot 框干扰整体风格。
- **标签页贴图与状态**：
  - 标签按钮使用 `states.png` 右下角提供的预设贴图。
  - 每个 Tab 使用 3 张贴图：未选中 / 悬停 / 选中；两个 Tab 合计 6 张贴图（按从左到右的顺序取样）。
  - `DefaultMachineUI.kt` 内将坐标与尺寸集中到“Texture Configuration”代码块，便于运行时进行像素级微调与调试。

UI 的最终来源按以下优先级解析（见 `MachineBlockEntity.buildUI`）：
1) `UIProviderComponent`（机器组件提供 UI）
2) `MachineUIRegistry`（脚本/模组运行时覆盖）
3) `DefaultMachineUI`（内置默认 UI）

## 3. 生命周期与加载顺序

整体加载与注册顺序如下：

1. **Mod 构造**
   - 可通过代码/脚本队列 MachineType（`MachineTypeRegisterer.queue`）

2. **PreInit** — `PrototypeMachinery.preInit`：
   - 初始化日志与元数据
   - `NetworkHandler.init()` 注册网络包（机器同步、UI actions）
   - 注册调度器到 `MinecraftForge.EVENT_BUS`
   - 注册 CraftTweaker 脚本 reload 钩子（ZenUtils）
   - `StructureLoader.loadStructureData(event)`
     - 加载/复制结构 JSON
   - `CraftTweakerExamples.initialize(event)`
     - 检测并复制 CraftTweaker 示例脚本
   - `MachineTypeRegisterer.processQueue(event)`
     - 将所有机器类型写入 `MachineTypeRegistryImpl`

3. **Registry Events**（Forge 自动触发）：
   - `BlockRegisterer.onRegisterEvent(RegistryEvent.Register<Block>)`
     - 为所有 `MachineType` 注册 `MachineBlock`
   - `ItemRegisterer.onRegisterEvent(RegistryEvent.Register<Item>)`
     - 为所有 `MachineBlock` 注册 `ItemBlock`

4. **Init** — `PrototypeMachinery.init`：
   - 预留，当前只调用 `proxy.init()`

5. **PostInit** — `PrototypeMachinery.postInit`：
   - `StructureLoader.processStructures(event)`
     - 将 StructureData 转为 MachineStructure，并注册到 StructureRegistry

6. **ServerStopping** — `PrototypeMachinery.serverStopping`：
   - 调用 `TaskSchedulerImpl.shutdown()` 关闭调度器

从上面的顺序可以看出：
- 结构在 **PostInit** 完成最终解析与注册
- `MachineType` 注册在 **PreInit** 完成
- 机器方块/物品在 **Registry 阶段** 完成
- CraftTweaker 示例脚本仅在检测到 CraftTweaker 安装时复制，真正的机器注册由脚本侧在 **PreInit** 完成。

---

## 4. 未来扩展建议

基于当前骨架，未来可以在以下方向扩展：

1. **MachineInstance 逻辑填充**
   - 在 `onSchedule()` 中实现：
     - 多方块结构检测与 `setFormed(true/false)`
     - 配方处理（RecipeProcess）
     - 能源/物流交互

2. **StructureValidator 实现集**
   - 比如：
     - `HeightValidator` — 限制结构高度
     - `BiomeValidator` — 限制仅在特定群系运作
     - `NeighborValidator` — 检查附近方块

3. **更丰富的 Pattern Predicate**
   - 目前仅示例了 `StatedBlockPredicate`
   - 可以拓展：
     - 任意方块集合
     - Tag-based predicate
     - NBT 条件等

4. **GUI / 组件系统的联动**
   - 通过 `MachineComponentType` 动态组合：能量、物品、流体等
   - GUI 根据组件自动生成界面

5. **更完善的文档与示例**
   - 在 `docs/` 中为 API、脚本接口、结构定义规则补充更细致说明

---

## 5. 总结

当前项目已经具备一个相当完整且可扩展的基础架构，后续只需在既有骨架上填充业务逻辑与更多内容即可。
