# PrototypeMachinery 项目概要

> 本文档由 AI 基于当前仓库代码与改造过程自动生成，旨在为后续维护者提供高层概览与核心逻辑说明。

## 1. 项目定位与目标

PrototypeMachinery 是一个基于 Minecraft Forge 1.12.2 的多方块机械框架模组，核心目标是：

- 提供 **可声明式定义** 的多方块结构（通过 JSON 结构文件）
- 提供 **可扩展的机器类型系统**（MachineType + MachineInstance）
- 支持 **并发执行的机器调度系统**（TaskScheduler + ISchedulable）
- 通过 **CraftTweaker 集成** 暴露给脚本侧进行机器注册与配置

当前代码处于“预览/基础设施搭建”阶段，已经搭好完整的骨架，方便后续逐步填充具体逻辑。

---

## 2. 核心模块总览

### 2.1 机械逻辑与配方架构（重点）

这一节集中介绍机械本体的逻辑管线与配方执行设计，按“类型 → 实例 → 组件 → 配方 → 需求系统”的层次展开。

#### 2.1.1 机器类型与实例化骨架

- **类型注册**：`MachineType` 通过 `MachineTypeRegistryImpl` 统一管理；`MachineTypeRegisterer` 支持在 PreInit 阶段集中写入。
- **实例创建**：`MachineBlockEntity.initialize(machineType)` 会创建 `MachineInstanceImpl`，并将机器的组件、属性、结构等绑定到具体方块实体。
- **形成状态**：`MachineInstanceImpl` 暴露 `isFormed()` 与内部 `setFormed(...)`，供结构校验后更新；`MachineBlock.getActualState` 动态读取此状态以渲染 `FORMED` 属性。

#### 2.1.2 组件系统与执行槽位

- **组件抽象**：`MachineComponent` + `MachineComponentType` + `MachineSystem` 组合描述“机器有哪些能力、由哪个系统按 tick 驱动”。系统间的执行顺序通过拓扑排序自动管理。
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
- **组件扩展**：`RecipeProcessComponent` + `RecipeProcessComponentType` + `RecipeProcessSystem` 允许为进程本身挂载可 tick 的子组件（如进度条、缓存等）。

#### 2.1.5 需求类型与系统（Requirement Layer）

- **类型声明**：`RecipeRequirementType<C>` 绑定一个 `RecipeRequirementSystem<C>`，定义“这种需求由哪个系统执行”。示例实现：`ItemRequirementType`、`FluidRequirementType`、`EnergyRequirementType`（位于 `common/registry/RecipeRequirementTypes`）。
- **需求组件**：`RecipeRequirementComponent` 代表机器侧用于满足该需求的“端口/容器”，如 `ItemRequirementComponent` 持有 `ItemContainerComponent`。
- **处理系统**：`RecipeRequirementSystem` 提供 `check / onStart / onEnd` 钩子，`Tickable` 版本增加 `acquireTickTransaction()` 以事务方式逐 tick 消耗/产出。现有系统（物品/流体/能量）均为骨架，等待接入真实容器/网络逻辑。
- **返回值模型**：`ProcessResult` 封装成功/失败与本地化错误信息，贯穿需求检查与执行。

#### 2.1.6 执行管线（Processor System）

- `FactoryRecipeProcessorSystem.onTick` 的预期流程：
  1. `component.tickProcesses()` —— 逐个执行器驱动进程
  2. 对每个进程：
     - 检查是否已满足完成条件（占位 `checkCompletion`）
     - 针对所有需求类型调用系统的逐 tick 处理（占位 `tickRequirements`）
     - 完成后调用需求系统的 `onEnd` 做收尾，随后移出队列
- **执行器插拔**：`RecipeExecutor.tick(processor)` 允许不同策略驱动（如同步/异步、优先级排序等）。当前未提供默认执行器，可在构造机器类型时按需注入。

#### 2.1.7 设计要点与待办

- **匹配与开工**：需要在 `FactoryRecipeScanningSystem` 中实现实际的“需求可行性检测”（结合 `MachineInstance` 的组件/库存）并创建 `RecipeProcessImpl`。
- **完成判定**：`checkCompletion` 需根据配方时长/需求进度判断；可结合 `RecipeProcessComponent`（如进度条）实现。
- **需求事务**：Tickable 需求应在 `acquireTickTransaction` 中执行“预扣-提交”模式，失败则自然回滚。
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
- `common/integration/crafttweaker/CraftTweakerExamples.kt`
- 资源脚本示例：`assets/prototypemachinery/scripts/examples/machine_registration.zs`

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
  - `FORMED: PropertyBool` — 多方块是否形成（**不存入 meta，动态计算**）
- 初始化：
  - 设定硬度、抗性、采集工具等级、音效等
  - `defaultState = baseState.withProperty(FACING, NORTH).withProperty(FORMED, false)`
  - `registryName = ResourceLocation(machineType.id.namespace, machineType.id.path + "_controller")`
- BlockState 编码：
  - `createBlockState()` — 包含 FACING & FORMED
  - `getStateFromMeta(meta)` — 仅编码朝向（0-3）
  - `getMetaFromState(state)` — 仅使用 `facing.horizontalIndex`
  - `getActualState(state, world, pos)` — 动态查询 TileEntity：
    - 若为 `MachineBlockEntity`，则 `formed = tile.machine.isFormed()`
    - 将 `FORMED` 属性更新并返回
- 渲染与方块实体：
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
- 支持自动重启线程池以兼容单人游戏多次加载；`shutdown()` 在 `PrototypeMachinery.serverStopping` 调用。
- `MachineInstanceImpl` 默认 `ExecutionMode.CONCURRENT`，在 `MachineBlockEntity.initialize` 时注册调度，在 `invalidate` 时移除。

后续落地建议：在 `onSchedule()` 内衔接配方扫描/执行与结构检查；若需要强一致性，可在关键路径强制使用 `MAIN_THREAD`。

## 3. 生命周期与加载顺序

整体加载与注册顺序如下：

1. **Mod 构造**
   - 可通过代码/脚本队列 MachineType（`MachineTypeRegisterer.queue`）

2. **PreInit** — `PrototypeMachinery.preInit`：
   - 初始化日志与元数据
   - 注册调度器到 `MinecraftForge.EVENT_BUS`
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
