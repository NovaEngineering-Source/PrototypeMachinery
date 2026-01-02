# PrototypeMachinery 项目概要

English translation (rough): [`PROJECT_OVERVIEW.en.md`](./PROJECT_OVERVIEW.en.md)

> 本文档用于为后续维护者提供高层概览与核心逻辑说明。

## 1. 近期重要变更（对照代码现状）

以下内容来自近期的大规模重构/新增功能，建议维护者优先了解：

- **客户端结构投影预览（Structure Projection Preview）**：支持 /pm_preview 调试命令、HUD 提示、本地化，以及 **24 向朝向（front+top）** 的锁定/旋转。
   - 详见：[`docs/StructurePreview.md`](./docs/StructurePreview.md)
- **结构预览 UI（ModularUI / Structure Preview UI）**：新增只读的 GUI 结构预览界面（`/pm_preview_ui`），可在界面内查看材料/BOM，并提供 **3D 视图（方块模型渲染）** 与 **切片（Layer）模式**。
   - 支持 dt 平滑的折叠菜单动画、线框覆盖层开关（便于观察 block model）、以及（可选的）客户端世界扫描对比（由宿主配置 gate）。
   - 详见：[`docs/StructurePreview.md`](./docs/StructurePreview.md)（已包含 GUI 章节）
   - UI 贴图规范（布局/交互/资源命名）：[`src/main/resources/assets/prototypemachinery/textures/gui/gui_structure_preview/gui_structure_preview.md`](./src/main/resources/assets/prototypemachinery/textures/gui/gui_structure_preview/gui_structure_preview.md)
- **GUI 贴图体系（切片 + 可选运行时 Atlas）**：结构预览相关贴图从“整张大图裁切表”迁移为“按组件切片的小图资源 + 稳定路径引用”。同时提供可选的运行时 GUI atlas（TextureMap + Stitcher）以减少大量小图的绑定开销。
   - 运行时 atlas：`src/main/kotlin/client/atlas/PmGuiAtlas.kt`
   - 构建期切片工具：`src/main/kotlin/devtools/atlas/GuiSliceGenerator.kt` + `src/main/resources/assets/prototypemachinery/pm_gui_slices/*.json`
- **机器控制器朝向系统（FACING + TWIST）**：朝向被拆分为 `FACING`（6 向）+ `TWIST`（0..3）。放置时默认“面向玩家”，并支持运行时旋转时的模型实时更新。
   - 代码入口：`common/block/MachineBlock.kt`、`common/block/entity/MachineBlockEntity.kt`、`client/model/ControllerModelBakeHandler.kt`、`common/util/TwistMath.kt`
- **客户端机器渲染管线重构（集中式 flush + 透明/Bloom 顺序保证）**：机器 TESR 仅提交渲染数据，统一在 TESR batch 之后集中渲染（先不透明再半透明），Bloom 在 GT 环境下延后到 bloom 回调阶段绘制，避免错序导致的亮度异常。
   - 设计/现状说明：[`docs/RenderingSystem_SecureAssets.md`](./docs/RenderingSystem_SecureAssets.md)
   - 代码入口：`client/impl/render/MachineRenderDispatcher.kt`、`client/impl/render/binding/MachineBlockEntitySpecialRenderer.kt`、`mixin/minecraft/MixinRenderGlobal.java`

- **客户端渲染性能与可观测性（HUD + RenderTuning + 多级缓存）**：围绕“减少构建抖动 / 减少 VBO 上传 / 降低 direct buffer churn / 降低 HashMap hot path”等目标，新增/强化了渲染侧缓存、BufferBuilder 复用与 HUD 指标。
   - 关键点：`RenderDebugHud`（/pm_render_hud）、`RenderTuning`（Forge config + /pm_config）、`BufferBuilderPool`、`BufferBuilderVboCache`、`ReusableVboUploader`（合并上传：orphaning / 可选 mapRange）。
   - 详见：[`docs/RenderingPerformance.md`](./docs/RenderingPerformance.md)

- **结构渲染数据组件化 + 增量同步（避免渲染线程结构匹配）**：把“渲染/隐藏所需的结构派生数据”（bounds + sliceCounts）迁移到系统组件 `StructureRenderDataComponent`，由服务器计算并通过 FULL/INCREMENTAL NBT 同步到客户端。
   - 详见：[`docs/StructureRenderDataSync.md`](./docs/StructureRenderDataSync.md)

- **Modern Backend（Cleanroom / Java 21+）与 Vector API 探测**：新增 `PMPlatform` 抽象与 SPI Provider，用于在主模组侧以“可选依赖”的方式接入 Java21+ 的批处理/Vector API 加速实现；运行时通过反射探测并安全回退。
   - 入口：[`docs/ModernBackend.md`](./docs/ModernBackend.md)、[`modern-backend/README.md`](./modern-backend/README.md)
- **结构匹配 fast-fail**：`StructurePattern` 具备 bounds（minPos/maxPos）并提供 `isAreaLoaded(...)`，在匹配前先检查覆盖范围是否已加载，避免未加载区块导致的误判与卡顿。
- **事务化 Requirement 系统**：配方需求执行采用 `RequirementTransaction` 事务模型（start / tick / end），失败/阻塞时整体回滚以保持原子性。
- **Requirement Overlay（按进程覆写）**：支持为单个 `RecipeProcess` 挂载 overlay，在执行前解析“生效的需求组件”。
- **压力测试结构示例**：提供超大结构 JSON 示例与生成脚本，便于测试加载/渲染/匹配性能。

## 📚 文档已拆分（推荐从这里开始）

本项目的文档已按主题拆分到 `docs/` 目录；本文保留为“总览 + 归档”，方便快速扫一遍全局。

- 文档索引（入口）：[`docs/README.md`](./docs/README.md)

英文文档（翻译中）：[`docs/en/README.md`](./docs/en/README.md)


## 2. 模块索引（详细文档在 docs/）

为了避免主文档过长且与 `docs/` 重复，这里只保留“地图式索引”。每个主题文档都包含关键文件路径与更详细说明：

- 属性系统：[`docs/Attributes.md`](./docs/Attributes.md)
- 机器逻辑与配方架构：[`docs/MachineLogic.md`](./docs/MachineLogic.md)
- 多方块结构：[`docs/Structures.md`](./docs/Structures.md)
  - JSON 指南：[`docs/StructureJsonGuide.md`](./docs/StructureJsonGuide.md)
  - Loader 特性：[`docs/StructureLoadingFeatures.md`](./docs/StructureLoadingFeatures.md)
- 结构预览（客户端：世界投影 / GUI）：[`docs/StructurePreview.md`](./docs/StructurePreview.md)
- 机器类型注册：[`docs/MachineRegistration.md`](./docs/MachineRegistration.md)
- CraftTweaker 集成：[`docs/CraftTweaker.md`](./docs/CraftTweaker.md)
- UI（默认 + 脚本 UIRegistry）：[`docs/UI.md`](./docs/UI.md)
- JEI / HEI 集成（配方索引 + 默认 UI + Addon 扩展）：[`docs/JEI.md`](./docs/JEI.md)
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
   - pattern nbt：`pattern[].nbt` 已支持（`StatedBlockNbtPredicate`），但 `alternatives` 中携带 NBT 的完整匹配仍有已知限制（当前会 warn 并回退到 base option）。

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
