# PrototypeMachinery 文档索引

本目录用于存放 PrototypeMachinery 的“可维护文档”。

如果你是第一次阅读：建议先看 `PROJECT_OVERVIEW.md`（项目总览/入口），再按需深入下面各主题。

## 快速导航

- **核心概念**
  - [属性系统（Machine Attributes）](./Attributes.md)
  - [机器逻辑与配方架构](./MachineLogic.md)
  - [资源键系统（PMKey）](./PMKey.md)
  - [资源存储（ResourceStorage / EnergyStorage）](./Storage.md)

- **多方块结构**
  - [结构系统总览](./Structures.md)
  - [MachineStructure JSON 配置指南](./StructureJsonGuide.md)
  - [StructureLoader 加载特性说明](./StructureLoadingFeatures.md)

- **注册与生命周期**
  - [机器类型注册（MachineType）与脚本注册](./MachineRegistration.md)
  - [方块/物品/方块实体注册流水线](./RegistrationPipeline.md)
  - [生命周期与加载顺序](./Lifecycle.md)

- **脚本与 UI**
  - [CraftTweaker（ZenScript）集成](./CraftTweaker.md)
  - [UI：默认 ModularUI + 脚本 UIRegistry](./UI.md)

- **模块功能**
  - [10 阶 Hatch 系统（Item / Fluid / Energy）](./Hatches.md)
  - [统一 API 入口（PrototypeMachineryAPI）](./API.md)
  - [调度器（TaskScheduler）](./TaskScheduler.md)

## 约定

- 文档尽量**以代码为准**：每篇都会列出关键文件路径，便于快速跳转。
- 若你发现文档与实现不一致：优先以实现为准，并欢迎在文档中补充“现状/限制”。
