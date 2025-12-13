# 机器类型注册（MachineType Registration）

本页描述 PrototypeMachinery 中 MachineType 的注册方式与加载时序。

## 你可以从哪里注册？

目前支持两条路径：

1. **代码侧（Kotlin/Java）**：构造 `MachineType` 并入队 `MachineTypeRegisterer.queue(...)`
2. **脚本侧（CraftTweaker / ZenScript）**：通过 `mods.prototypemachinery.MachineRegistry` 构建并注册

两者最终都会在 PreInit 阶段统一落到 `MachineTypeRegistryImpl`。

## 关键代码位置

- 入队与处理：`src/main/kotlin/common/registry/MachineTypeRegisterer.kt`
- 注册表实现：`src/main/kotlin/impl/machine/MachineTypeRegistryImpl.kt`
- 脚本桥接与 builder：
  - `src/main/kotlin/integration/crafttweaker/zenclass/ZenMachineRegistry.kt`
  - `src/main/kotlin/integration/crafttweaker/zenclass/ZenMachineTypeBuilder.kt`
  - `src/main/kotlin/integration/crafttweaker/CraftTweakerMachineTypeBuilder.kt`

## 时序（非常重要）

- **PreInit**：`PrototypeMachinery.preInit(...)` 中调用 `MachineTypeRegisterer.processQueue(event)`
  - 将队列中的 MachineType 写入 `MachineTypeRegistryImpl`

- **Forge Block Registry Event**：`BlockRegisterer` 会遍历 `MachineTypeRegistryImpl.all()`
  - 为每个 MachineType 创建并注册 `MachineBlock`

因此：MachineType 注册必须发生在 PreInit（或者更早的入队阶段），否则会错过后续方块注册。

## 代码侧示例（Kotlin）

示例可参考：`src/main/kotlin/example/ExampleMachineRegistration.kt`。

核心用法：

```kotlin
// During mod construction or PreInit
MachineTypeRegisterer.queue(myMachineType)
```

## 脚本侧示例（ZenScript）

入口：`mods.prototypemachinery.MachineRegistry`

资源中自带示例脚本：

- `src/main/resources/assets/prototypemachinery/scripts/examples/machine_registration.zs`

其中推荐的做法是通过结构 ID 进行延迟解析：

```zenscript
#loader preinit

import mods.prototypemachinery.MachineRegistry;

val m = MachineRegistry.create("mymod", "my_machine");
m.name("My Machine");
m.structure("example_complex_machine"); // 结构 ID（延迟解析）

MachineRegistry.register(m);
```

> `CraftTweakerMachineTypeBuilder.structure(String)` 底层会在首次访问 `MachineType.structure` 时从 `StructureRegistryImpl` 解析。

## 重复 ID 与错误处理

- `MachineTypeRegistryImpl.register(...)` 遇到重复 ID 会抛出 `IllegalArgumentException`。
- `MachineTypeRegisterer.processQueue(...)` 会捕获异常并写日志，不会直接让游戏崩溃。

## Thread Safety（线程安全）

注册表底层使用并发容器（如 `ConcurrentHashMap`）以保证读写安全。

但仍建议遵守生命周期：**在 PreInit 注册**，并避免在运行期随意动态注册新的 MachineType（除非你明确知道后续方块/渲染/UI 的影响）。

## See also

- [机器逻辑与配方架构](./MachineLogic.md)
- [结构系统总览](./Structures.md)
- [CraftTweaker（ZenScript）集成](./CraftTweaker.md)
