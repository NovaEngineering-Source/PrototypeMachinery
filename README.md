# PrototypeMachinery

[中文](#中文) | [English](#english)

---

## 中文

PrototypeMachinery 是一个面向 **Minecraft 1.12.2 (Forge)** 的模组/框架型项目，用于以“**多方块结构（JSON）+ 机器类型（MachineType）+ 组件系统（ECS）+ UI（ModularUI）+ CraftTweaker 脚本扩展**”的方式快速搭建与迭代多方块机器原型。

### 功能概览

- **多方块结构系统（MachineStructure）**：支持结构模式、朝向变换、校验器（Validator），并提供 JSON 加载器
- **客户端结构投影预览（Structure Projection Preview）**：在世界中投影结构轮廓/幽灵方块用于搭建与调试，支持 **24 向朝向（front+top）** 的锁定/旋转，并带 HUD 提示与本地化
- **两阶段加载**：PreInit 扫描结构 JSON，PostInit 解析方块引用并注册结构（见 `StructureLoader`）
- **机器类型注册（MachineType）**：支持代码侧与 CraftTweaker/ZenScript 侧注册，并在 PreInit 统一入库
- **MachineInstance（ECS 架构）**：机器实例作为 Entity，组件作为 Components，执行逻辑/任务作为 Systems（详见代码注释与 docs）
- **10 阶 Hatch 系统**：物品/流体/能量等接口的分级实现与 GUI
- **UI**：默认 ModularUI 界面 + 脚本 UIRegistry 覆盖 + UIBindings 数据绑定
- **统一 API 入口**：`PrototypeMachineryAPI` 聚合 registries 与 manager

### 文档导航（建议从这里开始）

- 项目总览（入口）：[`PROJECT_OVERVIEW.md`](./PROJECT_OVERVIEW.md)
- 文档索引：[`docs/README.md`](./docs/README.md)

常用主题直达：

- 生命周期与加载顺序：[`docs/Lifecycle.md`](./docs/Lifecycle.md)
- 结构系统：[`docs/Structures.md`](./docs/Structures.md)
- 结构 JSON 指南：[`docs/StructureJsonGuide.md`](./docs/StructureJsonGuide.md)
- 结构投影预览（客户端）：[`docs/StructurePreview.md`](./docs/StructurePreview.md)
- 机器注册：[`docs/MachineRegistration.md`](./docs/MachineRegistration.md)
- CraftTweaker 集成：[`docs/CraftTweaker.md`](./docs/CraftTweaker.md)
- UI：[`docs/UI.md`](./docs/UI.md)
- API 入口：[`docs/API.md`](./docs/API.md)

### 30 秒定位源码入口

1. 生命周期入口：`src/main/kotlin/PrototypeMachinery.kt`
2. 结构加载：`src/main/kotlin/common/structure/loader/StructureLoader.kt`
3. 机器类型注册：`src/main/kotlin/common/registry/MachineTypeRegisterer.kt` + `src/main/kotlin/impl/machine/MachineTypeRegistryImpl.kt`
4. 方块注册：`src/main/kotlin/common/registry/BlockRegisterer.kt`
5. UI 覆盖链路：`src/main/kotlin/impl/ui/registry/MachineUIRegistryImpl.kt` + `src/main/kotlin/integration/crafttweaker/zenclass/ui/*`

### 开发环境与运行

本项目使用 **Gradle + RetroFuturaGradle** 搭建 1.12.2 开发环境（JDK 8 toolchain）。

1. 首次初始化（生成反编译工作区等）：运行 `gradlew setupDecompWorkspace`
2. IDE：用 IntelliJ IDEA 导入/刷新 Gradle 工程
3. 运行：`gradlew runClient` / `gradlew runServer`（或使用 IDE 中已生成的 Run Configurations）

> `gradle.properties` 中包含 mixins/coremod 等开关；修改后可能需要重新 setup 并刷新 Gradle。

### 结构 JSON 与示例

- 结构 JSON 默认从 `config/prototypemachinery/structures/` 读取
- 若该目录为空，首次运行会从资源内复制示例到 `config/.../structures/examples/`

补充：仓库内包含一个用于压力测试的“超大结构”示例与生成脚本：

- 生成脚本：`scripts/generate_huge_structure.py`
- 示例结构（资源内，会被首跑复制到 config）：`src/main/resources/assets/prototypemachinery/structures/examples/huge_preview_64x16x64.json`

详见：[`docs/Structures.md`](./docs/Structures.md)、[`docs/StructureLoadingFeatures.md`](./docs/StructureLoadingFeatures.md)

### CraftTweaker（ZenScript）

- 脚本侧机器注册入口：`mods.prototypemachinery.MachineRegistry`
- 资源内示例脚本：`src/main/resources/assets/prototypemachinery/scripts/examples/*.zs`

详见：[`docs/CraftTweaker.md`](./docs/CraftTweaker.md)

### 资源维护（PNG 无损优化）

本仓库包含一个辅助脚本，用于对 PNG 执行**无损重压缩**并**剥离所有辅助元数据 chunk**（只保留必要的像素数据）。

- Script: `scripts/optimize_png.sh`
- Target: `src/main/resources`

### License

MIT

---

## English

PrototypeMachinery is a **Minecraft 1.12.2 (Forge)** mod/framework project that helps you prototype multiblock machines using:

- **Multiblock structures (JSON)**
- **Machine types (MachineType)**
- **ECS-style runtime instances (MachineInstance + components)**
- **UI via ModularUI**
- **CraftTweaker / ZenScript integration**

It also includes a client-side **structure projection preview** tool for building/debugging large multiblocks.

### Documentation (start here)

- Project overview (entry point): [`PROJECT_OVERVIEW.md`](./PROJECT_OVERVIEW.md)
- Docs index: [`docs/README.md`](./docs/README.md)

Quick links:

- Lifecycle & load order: [`docs/Lifecycle.md`](./docs/Lifecycle.md)
- Structures overview: [`docs/Structures.md`](./docs/Structures.md)
- Structure JSON guide: [`docs/StructureJsonGuide.md`](./docs/StructureJsonGuide.md)
- Structure preview (client-side): [`docs/StructurePreview.md`](./docs/StructurePreview.md)
- Machine registration: [`docs/MachineRegistration.md`](./docs/MachineRegistration.md)
- CraftTweaker integration: [`docs/CraftTweaker.md`](./docs/CraftTweaker.md)
- UI (ModularUI + script overrides): [`docs/UI.md`](./docs/UI.md)
- Public API entry point: [`docs/API.md`](./docs/API.md)

### Source entry points

1. Mod lifecycle: `src/main/kotlin/PrototypeMachinery.kt`
2. Structure loader: `src/main/kotlin/common/structure/loader/StructureLoader.kt`
3. MachineType registration: `src/main/kotlin/common/registry/MachineTypeRegisterer.kt` + `src/main/kotlin/impl/machine/MachineTypeRegistryImpl.kt`
4. Block registration: `src/main/kotlin/common/registry/BlockRegisterer.kt`
5. UI override chain: `src/main/kotlin/impl/ui/registry/MachineUIRegistryImpl.kt` + `src/main/kotlin/integration/crafttweaker/zenclass/ui/*`

### Development & run

This project uses **Gradle + RetroFuturaGradle** (JDK 8 toolchain) for the 1.12.2 dev environment.

1. First-time setup: run `gradlew setupDecompWorkspace`
2. Import/refresh the Gradle project in IntelliJ IDEA
3. Run: `gradlew runClient` / `gradlew runServer` (or use the generated IDE run configurations)

> Feature toggles (mixins/coremod/etc.) live in `gradle.properties`. If you change them, you may need to rerun setup and refresh Gradle.

### Structures & examples

- Structures are loaded from `config/prototypemachinery/structures/`
- If the directory is empty, examples will be copied from the mod assets into `config/.../structures/examples/` on first run

Extras for stress-testing:

- Generator script: `scripts/generate_huge_structure.py`
- Huge example structure (shipped as an asset): `src/main/resources/assets/prototypemachinery/structures/examples/huge_preview_64x16x64.json`

See: [`docs/Structures.md`](./docs/Structures.md), [`docs/StructureLoadingFeatures.md`](./docs/StructureLoadingFeatures.md)

### CraftTweaker (ZenScript)

- Script entry point for machine registration: `mods.prototypemachinery.MachineRegistry`
- Example scripts: `src/main/resources/assets/prototypemachinery/scripts/examples/*.zs`

See: [`docs/CraftTweaker.md`](./docs/CraftTweaker.md)

### Asset maintenance (lossless PNG optimize)

This repo includes a helper script that **losslessly recompresses** PNG files and **strips all ancillary metadata chunks** (keeping only essential pixel data representation).

- Script: `scripts/optimize_png.sh`
- Target: `src/main/resources`

### License

MIT
