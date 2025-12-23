# 结构预览（客户端 / Structure Preview）

English translation: [`docs/en/StructurePreview.md`](./en/StructurePreview.md)

本文档介绍 PrototypeMachinery 的两条“客户端结构预览”能力：

1. **世界内投影预览**（`/pm_preview`）：把结构投影到世界中（ghost / outline / block model）。
2. **GUI 结构预览界面（ModularUI）**（`/pm_preview_ui`）：在界面里查看材料/BOM，并提供 3D/切片视图（可选世界扫描对比）。

> 这是一个偏调试/开发者向的功能：完全客户端侧渲染，不会替你放置方块，也不会改动世界。

## 快速上手

### 1）启动投影：/pm_preview

客户端命令：

- `/pm_preview <structureId> [sliceCount] [distance] [mode]`
- `/pm_preview off`（或 `/pm_preview stop`）

参数说明：

- `structureId`：结构 ID（来自结构 JSON 的 `id` 字段，或代码侧注册的 id）。
- `sliceCount`：可选，仅用于覆盖 slice 结构的预览切片数量。
- `distance`：可选，最大渲染距离（单位：格）。
- `mode`：可选，渲染模式（见下文）。

命令实现：`src/main/kotlin/client/preview/StructurePreviewClientCommand.kt`

### 1b）打开 GUI：/pm_preview_ui

客户端命令：

- `/pm_preview_ui <structureId> [sliceCount]`

该命令会打开一个**只读**的 ModularUI 界面，用于结构预览与材料/BOM 浏览。

命令实现：`src/main/kotlin/client/preview/ui/StructurePreviewUiClientCommand.kt`

### 2）渲染模式（mode）

- `ghost`（默认）：半透明方块（性能较好）
- `outline`：线框轮廓
- `both`：ghost + outline
- `block_model` / `block` / `model`：渲染真实方块模型（更慢，适合小结构）

### 3）锁定/旋转朝向（24 向）

投影默认会**跟随玩家朝向**（front/top）。你可以随时锁定或旋转：

- `R`：锁定/解锁投影朝向
- `[` / `]`：旋转（负向/正向）
- 旋转轴选择（按住修饰键）：
  - 无修饰键：Yaw（绕 UP 旋转）
  - `Shift`：Pitch（绕 EAST 旋转）
  - `Ctrl`：Roll（绕 SOUTH 旋转）

该系统基于 `StructureOrientation(front, top)`，因此支持 **24 种正交姿态**（不仅仅是 4 向水平旋转）。

按键实现：`src/main/kotlin/client/preview/ProjectionKeyBindings.kt`

## HUD 与本地化（i18n）

预览 HUD/聊天提示采用 lang key：

- 调试命令提示：`pm.preview.*`
- 投影 HUD / 聊天：`pm.projection.*`
- 按键绑定名称：`key.pm.preview.*`

语言文件：

- `src/main/resources/assets/prototypemachinery/lang/en_us.lang`
- `src/main/resources/assets/prototypemachinery/lang/zh_cn.lang`

## 性能与限制

- 大结构投影可能会有明显的 CPU/GPU 压力（尤其是 `block_model`）。
- 渲染/缓存预算集中在 `ProjectionConfig` 中（用于限制每帧渲染量、缓存容量等）：
  - `src/main/kotlin/client/preview/ProjectionConfig.kt`
- 投影会对结构进行展开（entries/statuses）并按预算分批渲染；因此可能会出现“逐步填充”的效果，这是预期行为。

## 压力测试：超大结构示例

仓库内提供一个用于压力测试的超大示例结构：

- 示例 JSON：`src/main/resources/assets/prototypemachinery/structures/examples/huge_preview_64x16x64.json`
- 生成脚本：`scripts/generate_huge_structure.py`

该示例用于验证：结构加载、预览展开、渲染预算/缓存策略在极端规模下的表现。

## 相关代码位置

- 投影主逻辑：`src/main/kotlin/client/preview/WorldProjectionManager.kt`
- 射线拾取（DDA）：`src/main/kotlin/client/preview/ProjectionRayMarcher.kt`
- HUD 渲染：`src/main/kotlin/client/preview/ProjectionHudRenderer.kt`
- 预览数据模型（API）：`src/main/kotlin/api/machine/structure/preview/StructurePreviewModel.kt`

---

## GUI 结构预览界面（ModularUI / Structure Preview UI）

GUI 预览的核心目标是“**host-agnostic 的只读 UI + 可选的 world scan/cache + 可复用的 3D widget**”。

### 主要能力

- **3D 视图（方块模型渲染）**：在 GUI 中渲染 block model（尽量接近世界渲染的 pass/深度行为）。
- **切片（Layer）模式**：按 $Y$ 层查看结构切片，并通过右侧滑条选择层。
- **折叠菜单与裁剪**：顶部/底部/左侧组件按规范滑出/收缩，并在组件自身矩形内裁剪（避免影响其他区域）。
- **线框覆盖层开关**：用于对比“线框边界/坐标轴”与“真实方块模型”效果。
- **（可选）客户端世界扫描对比**：按 anchor 扫描世界并生成 mismatch/unloaded/unknown 状态，用于 issues-only 显示与统计。
  - 该能力由宿主配置 gate：只读宿主（例如 JEI）可以完全禁用 world scan。

### 视图与交互（当前实现约定）

- **模式切换**：底部 `layer_preview` 按钮为自锁开关。
  - 关闭时：3D 视图。
  - 开启时：切片（Layer）视图 + 右侧层滑条可用。
- **相机操作（3D）**：
  - 鼠标拖拽：旋转视角
  - 鼠标滚轮：缩放
  - 顶部 `preview_reset`：重置缩放与视角
- **线框开关**：底部 `replace_block` 按钮当前被复用为“线框覆盖层开关”（用于更清晰地观察 block model）。

> 注：一些更细的交互/布局规范（比如 replace_preview 的滑出/缩回、material_preview_ui 的拖拽缩放手柄等）以资源目录中的规范文档为准：
> `src/main/resources/assets/prototypemachinery/textures/gui/gui_structure_preview/gui_structure_preview.md`

### 世界扫描（World Scan）与宿主配置（Host Gate）

GUI 界面支持可选的客户端世界扫描，用于把 `StructurePreviewModel` 映射到世界中的实际方块，从而生成每个坐标的状态（MATCH / MISMATCH / UNLOADED / UNKNOWN）。

- 扫描缓存：`src/main/kotlin/client/preview/scan/StructurePreviewWorldScanCache.kt`
- 扫描器：`src/main/kotlin/client/preview/scan/StructurePreviewWorldScanner.kt`
- 宿主配置：`src/main/kotlin/client/preview/ui/StructurePreviewUiHostConfig.kt`
  - `allowWorldScan=false` 时 UI 必须保证不访问 `mc.world`（典型场景：JEI/read-only host）。

### 3D 渲染策略（实现要点）

`StructurePreview3DWidget` 负责渲染与交互，设计上不依赖真实世界（JEI-friendly）：

- **渲染输入**：`StructurePreviewModel`（结构本身）+ 可选 `statusProvider`（来自 world scan）
- **方块模型构建**：通过 `BlockRendererDispatcher.renderBlock(...)` 往 `BufferBuilder` 填充，再上传到 VBO。
- **邻面裁剪**：构造结构内的 `IBlockAccess`，为 `renderBlock` 提供相邻 block state，从而得到更接近世界的 face culling。
- **性能策略**：
  - 以固定大小 chunk（例如 $16^3$）对结构内方块分组
  - VBO 增量构建（build cursor）与每帧渲染预算（render cursor）
  - UI 关闭时 `dispose()` 释放 VBO，避免 GPU 内存泄漏
- **world-like pass**（目标是“看起来像世界渲染”）：
  - SOLID/CUTOUT：开启深度测试，写深度，通常不启用 blend
  - TRANSLUCENT：放到最后渲染，启用 blend，并在需要时关闭 depthMask

关键文件：

- GUI 入口：`src/main/kotlin/client/preview/ui/StructurePreviewUiScreen.kt`
- GUI 命令：`src/main/kotlin/client/preview/ui/StructurePreviewUiClientCommand.kt`
- 3D 视图 widget：`src/main/kotlin/client/preview/ui/widget/StructurePreview3DWidget.kt`

---

## GUI 贴图与资源组织（切片 / 规范 / atlas）

结构预览 GUI 的贴图资源位于：

- `src/main/resources/assets/prototypemachinery/textures/gui/gui_structure_preview/`

该目录下同时包含一份“布局/交互/贴图命名”的规范文档：

- `.../textures/gui/gui_structure_preview/gui_structure_preview.md`

此外，项目提供了可选的“构建期切片 + 运行时 atlas”管线：

- 构建期切片工具：`src/main/kotlin/devtools/atlas/GuiSliceGenerator.kt`
  - Manifest：`src/main/resources/assets/prototypemachinery/pm_gui_slices/*.json`
  - 产物：`build/generated/gui-slices/assets/.../textures/...`
- 运行时 atlas：`src/main/kotlin/client/atlas/PmGuiAtlas.kt`
  - 通过 TextureMap（Stitcher）把多个 sprite stitch 到 `textures/gui/pm_gui_atlas.png`
  - runtime index：`assets/prototypemachinery/pm_gui_atlas/<atlasId>.json`（由构建期工具生成）

> 当前结构预览 UI 的贴图主要以“稳定路径的切片 PNG”直接引用；atlas 管线为大量小图场景提供可选优化与统一管理。
