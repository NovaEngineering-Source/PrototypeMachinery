# 方块/物品/方块实体注册流水线

English translation: [`docs/en/RegistrationPipeline.md`](./en/RegistrationPipeline.md)

本项目并非把所有“复杂对象”都塞进 Forge Registry；而是：

- **MachineType**：使用内部注册表（`MachineTypeRegistryImpl`）在 PreInit 注册
- **机器控制器方块（MachineBlock）**：在 Forge 的 Block 注册事件中为每个 MachineType 创建并注册
- **方块实体**：集中注册（例如 `MachineBlockEntity`）

## 代码位置

- MachineType 入队/处理：`src/main/kotlin/common/registry/MachineTypeRegisterer.kt`
- 机器方块注册：`src/main/kotlin/common/registry/BlockRegisterer.kt`
- MachineBlock：`src/main/kotlin/common/block/MachineBlock.kt`
- MachineBlockEntity：`src/main/kotlin/common/block/entity/MachineBlockEntity.kt`

## 时序（简化）

1. PreInit：脚本/代码入队 -> `MachineTypeRegisterer.processQueue(event)` 注册 MachineType
2. Block Registry Event：`BlockRegisterer` 遍历 `MachineTypeRegistryImpl.all()`，注册 MachineBlock
3. 后续 Item 注册事件（如有）：基于已缓存的 machineBlocks 注册 ItemBlock

## See also

- [生命周期与加载顺序](./Lifecycle.md)
- [机器类型注册（MachineType）与脚本注册](./MachineRegistration.md)
