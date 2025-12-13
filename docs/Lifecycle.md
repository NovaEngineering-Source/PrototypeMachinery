# 生命周期与加载顺序

本文用于帮助定位“为什么某个注册/加载必须在某阶段做”。

## 关键入口

- `src/main/kotlin/PrototypeMachinery.kt`

## 结构加载（StructureLoader）

- PreInit：`StructureLoader.loadStructureData(event)`
  - 读取 `config/prototypemachinery/structures/*.json`
  - 如为空，复制资源内示例到 `config/.../structures/examples/`

- PostInit：`StructureLoader.processStructures(event)`
  - 解析 blockId/meta 到实际 `BlockState`
  - 转换并注册结构

## 机器类型注册（MachineType）

- PreInit：`MachineTypeRegisterer.processQueue(event)`
  - 将脚本/代码入队的 MachineType 写入 `MachineTypeRegistryImpl`

之后在 Forge 的 Block 注册事件里，会基于已注册的 MachineType 创建 MachineBlock。

## 服务器停止

- `TaskSchedulerImpl.shutdown()`：停止调度器并清理资源

## See also

- [结构系统总览](./Structures.md)
- [方块/物品/方块实体注册流水线](./RegistrationPipeline.md)
