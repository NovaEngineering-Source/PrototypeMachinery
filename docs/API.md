# 统一 API 入口（PrototypeMachineryAPI）

English translation: [`docs/en/API.md`](./en/API.md)

对外暴露的统一 API 入口位于：

- `src/main/kotlin/api/PrototypeMachineryAPI.kt`

它聚合了多个运行时注册表/管理器，例如：

- `machineTypeRegistry` -> `MachineTypeRegistryImpl`
- `structureRegistry` -> `StructureRegistryImpl`
- `recipeManager` -> `RecipeManagerImpl`
- `machineUIRegistry` -> `MachineUIRegistryImpl`

## 客户端集成（JEI / HEI）

JEI/HEI 集成不属于 `PrototypeMachineryAPI` 的统一入口（它是典型的客户端可选依赖集成），但对 addon 开发者而言同样需要一个“可扩展注册点”。

本项目提供的注册表用于把 **Requirement 系统**映射到 JEI：

- slot kind -> JEI ingredient group init/set：`JeiIngredientKindRegistry`
- requirement node -> 可索引 values：`JeiNodeIngredientProviderRegistry`
- requirement type -> renderer（slots/widgets/variants）：`JeiRequirementRendererRegistry`
- machine type -> layout：`JeiMachineLayoutRegistry`

重要边界：

- PrototypeMachinery 侧的 registries **不会替 addon 把“自定义 ingredient 类型”注册进 JEI 本体**（helper/renderer/比较逻辑等仍需 addon 按 JEI API 完成）。

详见：

- [JEI / HEI 集成（配方索引 + 默认 UI + Addon 扩展）](./JEI.md)

## Thread Safety

大多数 registry 采用并发容器（如 `ConcurrentHashMap`）实现读写安全。
但“何时注册/何时读取”的**生命周期约束**依旧存在：

- MachineType 应在 PreInit 完成注册
- Structure 的 block 解析需要等到 PostInit

详见：

- [生命周期与加载顺序](./Lifecycle.md)

## Key-level IO（基于 PMKey 的结构 IO）

PrototypeMachinery 的结构 IO（Structure IO）在内部已统一迁移为 **key-level**：

- 使用 `PMKey<T>` + `Long` 数量作为核心语义
- 扫描（parallelism 计算）与执行期复用同一套 key 匹配规则
- 对外 capability（`IItemHandler` / `IFluidHandler`）仅作为边界适配层（因其 `Int` 限制会做分块/限幅）

接口位置：

- `src/main/kotlin/api/machine/component/container/StructureKeyContainers.kt`

文档与迁移说明：

- [Key-level IO（基于 PMKey 的 IO）](./KeyLevelIO.md)
