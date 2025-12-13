# UI：默认 ModularUI + 脚本 UIRegistry

本项目的 UI 分两条线：

1. **默认原生 UI**：基于 ModularUI，为机器/外设提供默认界面。
2. **脚本 UI**：CraftTweaker 侧通过 `UIRegistry` 注册 UI 定义，并可通过 `UIBindings` 做数据绑定。

## 代码位置

- 默认 UI（示例：Hatch）：
  - `src/main/kotlin/common/block/hatch/*/*GUI.kt`
  - `src/main/kotlin/client/gui/sync/*`
  - `src/main/kotlin/client/gui/widget/*`

- 脚本 UI：
  - `src/main/kotlin/integration/crafttweaker/zenclass/ui/PMUI.kt`
  - `src/main/kotlin/integration/crafttweaker/zenclass/ui/UIRegistry.kt`
  - `src/main/kotlin/integration/crafttweaker/zenclass/ui/UIBindings.kt`

- UI 注册表实现：`src/main/kotlin/impl/ui/registry/MachineUIRegistryImpl.kt`

## 同步原则（很重要）

- **权威数据在服务端**：ModularUI 的同步应以服务端为准。
- 客户端 UI 不应“自行重建”资源列表来覆盖服务端同步结果。

（这也是 ResourceStorage 增量同步能稳定工作的前提。）

## See also

- [资源存储（ResourceStorage / EnergyStorage）](./Storage.md)
- [CraftTweaker（ZenScript）集成](./CraftTweaker.md)
