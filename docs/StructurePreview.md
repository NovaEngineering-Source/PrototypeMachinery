# 结构投影预览（客户端 / Structure Projection Preview）

本文档介绍 PrototypeMachinery 的**客户端结构投影预览**功能：将已注册的 `MachineStructure` 以“轮廓/幽灵方块/模型”的方式投影到世界中，辅助搭建与调试。

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
