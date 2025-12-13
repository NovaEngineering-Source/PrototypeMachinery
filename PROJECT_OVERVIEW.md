# PrototypeMachinery 项目概要

> 本文档由 AI 基于当前仓库代码与改造过程自动生成，旨在为后续维护者提供高层概览与核心逻辑说明。

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

---

## 4. 维护者提示

- **文档策略**：主文档只做“入口/导航”，主题细节以 `docs/` 为准。
- **首跑结构示例**：若 `config/prototypemachinery/structures/` 为空，会复制 `assets/.../structures/examples/` 到 `config/.../structures/examples/`。
- **结构文件组织**：推荐按子目录分类（例如 `structures/components/`），loader 采用递归扫描。

---

## 5. 总结

PrototypeMachinery 当前已经形成可扩展的基础骨架：结构（JSON）、机器类型、调度器、UI 与脚本扩展点都已就位。
后续开发建议以 `docs/README.md` 为入口，按需深入各模块实现。

## 4. 未来扩展建议

基于当前骨架，未来可以在以下方向扩展：

1. **MachineInstance 逻辑填充**
   - 在 `onSchedule()` 中实现：
     - 多方块结构检测与 `setFormed(true/false)`
     - 配方处理（RecipeProcess）
     - 能源/物流交互

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

5. **更完善的文档与示例**
   - 在 `docs/` 中为 API、脚本接口、结构定义规则补充更细致说明

---

## 5. 总结

当前项目已经具备一个相当完整且可扩展的基础架构，后续只需在既有骨架上填充业务逻辑与更多内容即可。
