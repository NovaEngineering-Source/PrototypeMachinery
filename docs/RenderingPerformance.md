# 客户端渲染性能与调优（现状文档）

> 本文档记录目前 **PrototypeMachinery（Forge 1.12.2 / Java 8）** 客户端渲染侧已经落地的性能优化与调优开关，方便维护者：
>
> - 快速理解“为什么这么做”
> - 快速定位“该改哪个开关/哪段代码”
> - 读懂 HUD 指标，排查重建抖动、VBO 上传、DirectBuffer 占用等问题

更偏“渲染管线时序与 Bloom 顺序保证”的设计/现状说明请看：[`docs/RenderingSystem_SecureAssets.md`](./RenderingSystem_SecureAssets.md)

---

## 1. 总体结构（只提交 + 集中 flush）

- TESR（`MachineBlockEntitySpecialRenderer`）只负责 **提交（submit）** 渲染任务/缓冲，不直接做 draw。
- `MachineRenderDispatcher.flush()` 在 TESR batch 之后统一执行绘制：
  - 先 `DEFAULT`（不透明）
  - 再 `TRANSPARENT`（半透明）
  - Bloom 在可用时延后到 GT Bloom 回调阶段（见 `GregTechBloomHooks` 等桥接）

这个结构的核心目标是：

- 将昂贵的“构建（CPU）”与“绘制（GL 主线程）”解耦
- 用缓存把“每帧重复 upload/draw”压到最低
- 保证跨机器的透明/辉光绘制顺序一致

---

## 2. 关键性能子系统（按热路径排序）

### 2.1 RenderTaskCache：后台构建 + 命中复用

相关文件：

- `src/main/kotlin/client/impl/render/task/RenderTaskCache.kt`
- `src/main/kotlin/client/impl/render/task/RenderTaskOwnerKeys.kt`

要点：

- RenderTaskCache 以 key 缓存构建产物，**旧结果可继续绘制**，新结果在后台构建完成后再替换。
- 为避免热路径 `HashMap.getNode + equals()`（以及每帧 data-class key 分配）带来的热点：
  - 引入 `RenderTaskOwnerKeys` 对 owner key 做 **intern（规范化复用）**
  - `OwnerKey` 以 **身份（identity）** 作为等价关系（同一个 TE + slot + part），避免昂贵 equals。
  - `RenderTaskCache.removeByTe` 走 `RenderTaskOwnerKeys.removeByTe(te)` 的快速清理路径。

适用范围：

- 大量 TESR/大结构时，key 访问频繁，HashMap 热点会非常明显。

### 2.2 BufferBuilderPool：控制 DirectBuffer 膨胀与抖动

相关文件：

- `src/main/kotlin/client/util/BufferBuilderPool.kt`
- `src/main/kotlin/client/util/NativeBuffers.kt`

要点：

- 全局复用 `BufferBuilder`（其底层是 direct `ByteBuffer`），减少频繁 malloc/free 导致的抖动。
- 引入“过大 buffer（oversize）”的保留策略 + 过期/裁剪：
  - 避免偶发超大模型把池子撑爆、或反复分配/释放导致 OS 级别的 RSS 抖动。
- HUD 会显示 direct 分配估算/池子占用，用于定位 **DirectMemory OOM** 与“池子被大 buffer 污染”。

### 2.3 BufferBuilderVboCache：按 BufferBuilder 复用 VBO，减少每帧上传

相关文件：

- `src/main/kotlin/client/util/BufferBuilderVboCache.kt`
- `src/main/kotlin/client/util/ReusableVboUploader.kt`

要点：

- 构建完成后的 `BufferBuilder` 可绑定一个 GPU VBO（缓存上传结果），在缓存命中时避免 `glBufferData`/`VertexBuffer.bufferData` 的重复上传。
- 同时支持 **VBO id 池化**（减少 `glGenBuffers`/`VertexBuffer.<init>` 开销），对“主线程 GL churn”热点有明显改善。

### 2.4 OpaqueChunkVboCache（实验）：按 chunk-group 缓存不透明合并 VBO

相关文件：

- `src/main/kotlin/client/util/OpaqueChunkVboCache.kt`
- `src/main/kotlin/client/impl/render/MachineRenderDispatcher.kt`

要点：

- 仅对 `RenderPass.DEFAULT`（不透明）启用：
  - 把空间上同一 chunk-group（1x1 / 2x2 / 4x4 chunks）内的 **合并后不透明几何** 缓存在一个条目里。
  - 目标是减少“不同机器/大量结构”导致的重复合并与上传。
- 透明/Bloom 被刻意排除：
  - 透明排序与 Bloom 时序更敏感，容易引入错误与收益不确定。
- 采用 LRU/预算清理，HUD 可观测命中/重建/占用。

---

## 3. RenderTuning：可调参数（Forge config + /pm_config）

渲染调优开关统一存放于 API：

- `src/main/kotlin/api/tuning/RenderTuning.kt`

Forge 配置加载与热更新：

- `src/main/kotlin/common/config/PrototypeMachineryCommonConfig.kt`
- `/pm_config`：`src/main/kotlin/common/command/PmConfigServerCommand.kt`

目前已存在的配置分类（category）：

- `render_animation`
- `render_merge`
- `render_vbo_cache`
- `render_opaque_chunk_vbo_cache`
- `render_gecko`

### 3.1 运行时热调（建议流程）

1. 进服/进单机后，先用 `/pm_render_hud on` 打开 HUD。
2. 使用 `/pm_config list` 查看分类，`/pm_config list <category>` 查看键。
3. 用 `/pm_config set <category> <name> <value>` 调整，再观察 HUD 的：
   - build queue
   - cache hit/miss
   - VBO bytes / direct bytes
   - rebuild 频率

> 注意：`/pm_config` 是服务端命令（权限等级 2）。客户端渲染相关开关虽然“只在客户端使用”，但配置文件由服务端/单机一侧加载保存；在多人环境要注意权限与分发策略。

---

## 4. HUD 与调试命令

### 4.1 HUD

- `/pm_render_hud on|off|toggle`
- 指标来源：`RenderStats` + `RenderDebugHud`

HUD 常见用途：

- 判断是否发生 **构建抖动**（build/s 指标持续很高）
- 判断 VBO cache / chunk cache 是否有效（命中率、重建次数）
- 判断 BufferBuilderPool 是否在反复 new/borrow（池子不足或被 trim）
- 判断动画 key 是否被自动限流（防止 smooth 动画引起的重建风暴）

### 4.2 其它调试命令

- `/pm_render_stress [drawMultiplier]`：重复 draw call（压测渲染线程/驱动），并配合动画限流观测。
- `/pm_render_clear_caches [reason]`：清空渲染侧缓存（用于验证“缓存命中/失效”是否符合预期）。

---

## 5. 代码入口地图（快速跳转）

- TESR submit：`src/main/kotlin/client/impl/render/binding/MachineBlockEntitySpecialRenderer.kt`
- Flush 调度：`src/main/kotlin/client/impl/render/MachineRenderDispatcher.kt`
- HUD：`src/main/kotlin/client/impl/render/RenderDebugHud.kt`
- 统计：`src/main/kotlin/client/impl/render/RenderStats.kt`
- BufferBuilder 池：`src/main/kotlin/client/util/BufferBuilderPool.kt`
- VBO cache：`src/main/kotlin/client/util/BufferBuilderVboCache.kt`
- Chunk cache：`src/main/kotlin/client/util/OpaqueChunkVboCache.kt`
- 调优对象：`src/main/kotlin/api/tuning/RenderTuning.kt`
