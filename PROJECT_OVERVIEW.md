# PrototypeMachinery 项目概要

> 本文档用于为后续维护者提供高层概览与核心逻辑说明。

## 1. 近期重要变更（对照代码现状）

以下内容来自近期的大规模重构/新增功能，建议维护者优先了解：

- **客户端结构投影预览（Structure Projection Preview）**：支持 /pm_preview 调试命令、HUD 提示、本地化，以及 **24 向朝向（front+top）** 的锁定/旋转。
   - 详见：[`docs/StructurePreview.md`](./docs/StructurePreview.md)
- **结构匹配 fast-fail**：`StructurePattern` 具备 bounds（minPos/maxPos）并提供 `isAreaLoaded(...)`，在匹配前先检查覆盖范围是否已加载，避免未加载区块导致的误判与卡顿。
- **事务化 Requirement 系统**：配方需求执行采用 `RequirementTransaction` 事务模型（start / tick / end），失败/阻塞时整体回滚以保持原子性。
- **Requirement Overlay（按进程覆写）**：支持为单个 `RecipeProcess` 挂载 overlay，在执行前解析“生效的需求组件”。
- **压力测试结构示例**：提供超大结构 JSON 示例与生成脚本，便于测试加载/渲染/匹配性能。

## 📚 文档已拆分（推荐从这里开始）

本项目的文档已按主题拆分到 `docs/` 目录；本文保留为“总览 + 归档”，方便快速扫一遍全局。

- 文档索引（入口）：[`docs/README.md`](./docs/README.md)


## 2. 模块索引（详细文档在 docs/）

为了避免主文档过长且与 `docs/` 重复，这里只保留“地图式索引”。每个主题文档都包含关键文件路径与更详细说明：

- 属性系统：[`docs/Attributes.md`](./docs/Attributes.md)
- 机器逻辑与配方架构：[`docs/MachineLogic.md`](./docs/MachineLogic.md)
- 多方块结构：[`docs/Structures.md`](./docs/Structures.md)
  - JSON 指南：[`docs/StructureJsonGuide.md`](./docs/StructureJsonGuide.md)
  - Loader 特性：[`docs/StructureLoadingFeatures.md`](./docs/StructureLoadingFeatures.md)
- 结构投影预览（客户端）：[`docs/StructurePreview.md`](./docs/StructurePreview.md)
- 机器类型注册：[`docs/MachineRegistration.md`](./docs/MachineRegistration.md)
- CraftTweaker 集成：[`docs/CraftTweaker.md`](./docs/CraftTweaker.md)
- UI（默认 + 脚本 UIRegistry）：[`docs/UI.md`](./docs/UI.md)
- 调度器：[`docs/TaskScheduler.md`](./docs/TaskScheduler.md)
- 统一 API 入口：[`docs/API.md`](./docs/API.md)
- 资源键系统：[`docs/PMKey.md`](./docs/PMKey.md)
- 资源存储：[`docs/Storage.md`](./docs/Storage.md)
- 10 阶 Hatch 系统：[`docs/Hatches.md`](./docs/Hatches.md)
- 方块/物品注册流水线：[`docs/RegistrationPipeline.md`](./docs/RegistrationPipeline.md)
- 生命周期与加载顺序：[`docs/Lifecycle.md`](./docs/Lifecycle.md)

---

## 3. 30 秒上手（从哪里读代码）

1. 生命周期入口：`src/main/kotlin/PrototypeMachinery.kt`
2. 结构加载：`common/structure/loader/StructureLoader.kt`
3. 机器类型注册：`common/registry/MachineTypeRegisterer.kt` + `impl/machine/MachineTypeRegistryImpl.kt`
4. 机器方块注册：`common/registry/BlockRegisterer.kt`
5. UI 覆盖链路：`impl/ui/registry/MachineUIRegistryImpl.kt` + `integration/crafttweaker/zenclass/ui/*`

6. **PMKey / 库存（按类型计数 + 槽位视图）**：
   - 概念与约定：`docs/PMKey.md`
   - 存储实现与同步机制（含 SlottedResourceStorage / dirty-slot）：`docs/Storage.md`

---

## 4. 维护者提示

- **文档策略**：主文档只做“入口/导航”，主题细节以 `docs/` 为准。
- **首跑结构示例**：若 `config/prototypemachinery/structures/` 为空，会复制 `assets/.../structures/examples/` 到 `config/.../structures/examples/`。
- **结构文件组织**：推荐按子目录分类（例如 `structures/components/`），loader 采用递归扫描。

- **Kotlin / API 规范**：
   - 项目启用了 Kotlin `explicitApi()`：公共 API 需显式声明可见性与类型（见 `build.gradle.kts`）。
   - 根包包含 `package.kt`（`src/main/kotlin/package.kt`），用于统一根包声明；一般不需要把包路径设计得过深，避免冗长且难维护的嵌套包。

---

## 5. 未来扩展建议

基于当前实现，未来可以在以下方向继续扩展：

1. **结构 JSON 表达能力增强**
   - validators：目前 schema 有 `validators` 字段，但 loader 仍为 TODO（见 `StructureLoader` 中的注释）。
   - pattern nbt：schema 有 `pattern[].nbt` 字段，但目前未参与 predicate 生成。

2. **StructureValidator 实现集**
   - 比如：
     - `HeightValidator` — 限制结构高度
     - `BiomeValidator` — 限制仅在特定群系运作
     - `NeighborValidator` — 检查附近方块

3. **更丰富的 Pattern Predicate**
   - 目前仅示例了 `StatedBlockPredicate`
   - 可以拓展：
     - 任意方块集合
     - Tag-based predicate
     - NBT 条件等

4. **GUI / 组件系统的联动**
   - 通过 `MachineComponentType` 动态组合：能量、物品、流体等
   - GUI 根据组件自动生成界面

5. **结构预览体验增强**
   - 将目前的调试投影扩展为更完整的“搭建辅助 UI”（例如 BOM 面板、缺失方块统计、快捷复制结构 ID 等）。
   - 完善按键本地化与 HUD 文案的一致性（避免硬编码）。

---

## 6. 总结

当前项目已经具备一个相当完整且可扩展的基础架构，后续只需在既有骨架上填充业务逻辑与更多内容即可。
