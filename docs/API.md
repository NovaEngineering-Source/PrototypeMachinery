# 统一 API 入口（PrototypeMachineryAPI）

对外暴露的统一 API 入口位于：

- `src/main/kotlin/api/PrototypeMachineryAPI.kt`

它聚合了多个运行时注册表/管理器，例如：

- `machineTypeRegistry` -> `MachineTypeRegistryImpl`
- `structureRegistry` -> `StructureRegistryImpl`
- `recipeManager` -> `RecipeManagerImpl`
- `machineUIRegistry` -> `MachineUIRegistryImpl`

## Thread Safety

大多数 registry 采用并发容器（如 `ConcurrentHashMap`）实现读写安全。
但“何时注册/何时读取”的**生命周期约束**依旧存在：

- MachineType 应在 PreInit 完成注册
- Structure 的 block 解析需要等到 PostInit

详见：

- [生命周期与加载顺序](./Lifecycle.md)
