# PrototypeMachinery 文档索引

本目录用于存放 PrototypeMachinery 的“可维护文档”。

如果你是第一次阅读：建议先看 `PROJECT_OVERVIEW.md`（项目总览/入口），再按需深入下面各主题。

## English translations

本项目的文档以 `docs/`（中文）为主。

如果你需要英文版本：

- 英文索引入口：[`docs/en/README.md`](./en/README.md)

目前优先覆盖 API/架构相关的核心页面（API、Machine Attributes、MachineLogic、PMKey、Key-level IO、Storage、TaskScheduler、Lifecycle、Registration、Localization），并逐步补齐结构系统相关页面（Structures、StructureLoadingFeatures、StructureJsonGuide、StructurePreview）、脚本/客户端集成页面（CraftTweaker、JEI / JEI-Internals）与模块功能页（Hatches），以及 UI 入口页（UI / MachineUiEditorRuntime）。

## 快速导航

- **核心概念**
  - [属性系统（Machine Attributes）](./Attributes.md)
  - [机器逻辑与配方架构](./MachineLogic.md)
  - [资源键系统（PMKey）](./PMKey.md)
  - [资源存储（ResourceStorage / EnergyStorage）](./Storage.md)
  - [本地化（Localization / i18n）](./Localization.md)

- **多方块结构**
  - [结构系统总览](./Structures.md)
  - [MachineStructure JSON 配置指南](./StructureJsonGuide.md)
  - [StructureLoader 加载特性说明](./StructureLoadingFeatures.md)
  - [结构预览（客户端：世界投影 / GUI）](./StructurePreview.md)

- **注册与生命周期**
  - [机器类型注册（MachineType）与脚本注册](./MachineRegistration.md)
  - [方块/物品/方块实体注册流水线](./RegistrationPipeline.md)
  - [生命周期与加载顺序](./Lifecycle.md)

- **脚本与 UI**
  - [CraftTweaker（ZenScript）集成](./CraftTweaker.md)
  - [UI：默认 ModularUI + 脚本 UIRegistry](./UI.md)
  - [Machine UI Editor：Runtime JSON 对接（现状 + 契约 + 限制）](./MachineUiEditorRuntime.md)
  - （相关）[结构预览 GUI（/pm_preview_ui）](./StructurePreview.md)

- **客户端集成（Client Integrations）**
  - [渲染系统（机器控制器 / 透明与 Bloom 顺序 / 集中式 flush）](./RenderingSystem_SecureAssets.md)
  - [JEI / HEI 集成（配方索引 + 默认 UI + Addon 扩展）](./JEI.md)
  - [JEI 集成维护文档（Internals：integration/jei 全包索引）](./JEI-Internals.md)

- **模块功能**
  - [10 阶 Hatch 系统（Item / Fluid / Energy）](./Hatches.md)
  - [统一 API 入口（PrototypeMachineryAPI）](./API.md)
  - [调度器（TaskScheduler）](./TaskScheduler.md)

## 约定

- 文档尽量**以代码为准**：每篇都会列出关键文件路径，便于快速跳转。
- 若你发现文档与实现不一致：优先以实现为准，并欢迎在文档中补充“现状/限制”。

### Kotlin / API 规范补充

- 本项目启用了 Kotlin `explicitApi()`（见 `build.gradle.kts`），因此：
  - 公共 API 的可见性、返回类型等需要显式声明；
  - 若你新增/调整对外 API，请尽量保持注释与 KDoc 完整，便于维护与生成文档。

- 根包已包含 `package.kt`（`src/main/kotlin/package.kt`），用于统一根包声明。
  - 在新增文件时，建议直接在文件顶部声明实际所在的包名即可；
  - **无需**为了“分层”而把包路径嵌套得过深（避免出现冗长的 `github.kasuminova.prototypemachinery....` 多层重复/无意义拆分）。
