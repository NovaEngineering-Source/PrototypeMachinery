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

### Runtime JSON（Machine UI Editor 导出）

除了 builder 方式（`PMUI`）之外，`UIRegistry` 也支持直接注册 **runtime JSON**（通常由 Machine UI Editor 导出）：

- `UIRegistry.registerRuntimeJson(machineId, runtimeJson)`
- `UIRegistry.registerRuntimeJsonWithPriority(machineId, runtimeJson, priority)`

其中 `runtimeJson` 是一个 JSON 字符串；Mod 侧会在加载 UI 时解析 JSON，并按当前实现构建 ModularUI。

建议：

- 把 runtime JSON 当作“工具链产物”，脚本里只负责注册（以及注册必要的 `UIBindings`）。
- 如果需要条件/tabs/表达式绑定等能力，优先参考对接契约文档，避免依赖未落地语义。

对接细节（字段契约、兼容策略、支持的 widget type、visibleIf/enabledIf 与 tabs 的当前语义等）见：

- [Machine UI Editor：Runtime JSON 对接（现状 + 契约 + 限制）](./MachineUiEditorRuntime.md)

## See also

- [机器类型注册（MachineType）与脚本注册](./MachineRegistration.md)
- [UI：默认 ModularUI + 脚本 UIRegistry](./UI.md)
- [10 阶 Hatch 系统（Item / Fluid / Energy）](./Hatches.md)
