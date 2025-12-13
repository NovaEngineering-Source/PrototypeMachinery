# CraftTweaker（ZenScript）集成

本项目提供 CraftTweaker/ZenScript 侧的“预览期 API”，用于：

- 机器类型（MachineType）注册
- UI 定义注册（UIRegistry）与数据绑定（UIBindings）
- Hatch 配置查询/更新（如有）

## 代码位置

- 机器类型注册：
  - `src/main/kotlin/integration/crafttweaker/zenclass/ZenMachineRegistry.kt`
  - `src/main/kotlin/integration/crafttweaker/zenclass/ZenMachineTypeBuilder.kt`
  - `src/main/kotlin/integration/crafttweaker/CraftTweakerMachineTypeBuilder.kt`

- 机器数据（脚本数据容器）：
  - `src/main/kotlin/integration/crafttweaker/zenclass/data/ZenMachineData.kt`
  - `src/main/kotlin/api/machine/component/type/ZSDataComponentType.kt`

- UI：
  - `src/main/kotlin/integration/crafttweaker/zenclass/ui/PMUI.kt`
  - `src/main/kotlin/integration/crafttweaker/zenclass/ui/UIRegistry.kt`
  - `src/main/kotlin/integration/crafttweaker/zenclass/ui/UIBindings.kt`

- 示例脚本：`src/main/resources/assets/prototypemachinery/scripts/examples/*.zs`

## 机器注册（脚本侧）

脚本侧入口：`mods.prototypemachinery.MachineRegistry`

核心流程：

1. `MachineRegistry.create(modId, path)` 得到 `MachineTypeBuilder`
2. builder 设置 name / structure / componentTypes 等
3. `MachineRegistry.register(builder)` -> 入队 `MachineTypeRegisterer.queue(...)`
4. PreInit 阶段 `MachineTypeRegisterer.processQueue(...)` 写入 `MachineTypeRegistryImpl`

### 结构引用（推荐）

builder 支持通过 structureId 延迟解析结构：

- `builder.structure("example_complex_machine")`

其底层实现会在首次访问 `MachineType.structure` 时从 `StructureRegistryImpl` 解析，避免加载顺序问题。

## UIRegistry 与 UIBindings

- `UIRegistry.register(machineId, panel, priority)`：以优先级覆盖/叠加 UI 定义
- `UIBindings.*`：把 UI 组件与 `ZSDataComponent` 的键值绑定，实现脚本侧数据读写

## See also

- [机器类型注册（MachineType）与脚本注册](./MachineRegistration.md)
- [UI：默认 ModularUI + 脚本 UIRegistry](./UI.md)
- [10 阶 Hatch 系统（Item / Fluid / Energy）](./Hatches.md)
