# 渲染系统与安全资源（加密/解密）设计文档（草案）

> 目标：为 PrototypeMachinery 增加独立的模型渲染子系统，支持 GeckoLib / OBJ，支持多线程构建与批量渲染，并兼容 Lumenized 辉光渲染；同时提供“磁盘密文、进服解密”的资源管理方案，且**不触发全局资源重载**。
>
> 本文面向后续实现：定义模块边界、关键接口、线程模型、缓存/失效策略与落地步骤。

---

## 现状实现（以代码为准 / 已落地）

> 说明：本文件最初是“设计草案”。目前渲染子系统的**核心渲染管线与 Bloom 兼容已落地**，但“安全资源（加密/解密）”部分仍偏设计。
>
> 维护时请优先以本节与源码为准（下面的草案章节可作为后续扩展的约束与目标）。

补充：当前版本中，“安全资源（`secure/*`）”**并未实现解密**。

- 代码层面：`SecureAssetResolver` 对 `secure/` 路径会直接拒绝访问（抛出 `UnsupportedOperationException` / `exists=false`），避免“解锁后读到的仍是密文”这种更隐蔽的错误。
- 因此：本文件中关于“磁盘密文、进服解密”的内容目前仍属于**未来规划/设计约束**，不应当被视为已可用功能。

### 当前渲染时序（Minecraft 1.12.2 / Forge）

- **TESR 阶段（只提交，不绘制）**：`MachineBlockEntitySpecialRenderer` 仅收集渲染数据并提交到集中队列。
- **集中 flush（保证顺序）**：通过 mixin 在 `RenderGlobal#renderEntities` 内、`TileEntityRendererDispatcher.drawBatch(I)` 之后立即调用 `MachineRenderDispatcher.flush()`：
  1) 先渲染所有机器的 `DEFAULT`（不透明）
  2) 再渲染所有机器的 `TRANSPARENT`（半透明）
  3) Bloom：
     - 若检测到 GT Bloom 可用：把 `BLOOM/BLOOM_TRANSPARENT` 缓冲加入 `RenderManager` 队列，**延后到 GT Bloom 回调阶段**绘制
     - 否则：在此处直接绘制（fullbright 回退）

这个时序主要解决：

1) **半透明跨机器/跨子结构的排序一致性**：统一在 flush 中“先不透明后半透明”，避免 TESR 内部分散 draw 造成的混合顺序问题。

2) **GT Bloom 的时序约束**：GT 的 bloom 回调发生在 `EntityRenderer#renderWorldPass` 中，早于 Forge 的 `RenderWorldLastEvent`。
   因此不应把“基础模型”放到 `RenderWorldLastEvent` 才绘制，否则会出现 bloom 先于 base render 的错序（常见表现为泛白/亮度异常）。

### 关键实现文件（入口导航）

- 集中式渲染调度：`src/main/kotlin/client/impl/render/MachineRenderDispatcher.kt`
- TESR（submit-only）：`src/main/kotlin/client/impl/render/binding/MachineBlockEntitySpecialRenderer.kt`
- flush 注入点（TESR batch 之后）：`src/main/java/github/kasuminova/prototypemachinery/mixin/minecraft/MixinRenderGlobal.java`
- 防止 TESR 被模型隐藏器误伤：`src/main/java/github/kasuminova/prototypemachinery/mixin/minecraft/MixinTileEntityRendererDispatcher.java`
- 渲染 pass 状态（blend/depthMask/fullbright 等）：`src/main/kotlin/client/impl/render/RenderTypeState.kt`
- Bloom 桥接（GT 可选依赖）：`src/main/kotlin/client/impl/render/bloom/GregTechBloomBridge.kt`
- Bloom/缓冲队列：`src/main/kotlin/client/impl/render/RenderManager.kt`
- 兜底 flush（安全网）：`src/main/kotlin/client/impl/render/WorldRenderFlushHandler.kt`
- 缓存 key（包含朝向摘要）：`src/main/kotlin/client/api/render/RenderKey.kt`
- RenderTask 缓存策略：`src/main/kotlin/client/impl/render/task/RenderTaskCache.kt`
- mixin 配置：`src/main/resources/mixins.prototypemachinery.json` + `src/main/java/github/kasuminova/prototypemachinery/mixin/PMMixinLoader.java`

（补充）性能与调优现状（HUD 指标、RenderTuning 配置、VBO 缓存与 BufferBuilder 池）：

- [`docs/RenderingPerformance.md`](./RenderingPerformance.md)

### 缓存与失效（现状实现要点）

- `RenderKey` 增加了 `orientationHash`（朝向摘要），用于确保“旋转/扭转”能触发缓存失效与重建。
  - 现有实现中，TESR 侧会把 `front/top/twist` 编码到 `orientationHash`（例如：`front.ordinal * 24 + top.ordinal * 4 + twist`）。
- `RenderTaskCache` 对 key 的变化采取“**新建任务**”而不是复用旧任务并重置：
  - 这是为了避免 RenderTask 内部保留旧 snapshot 导致的“消失/错画”，保证任务结果对其 key 是不可变的。

---

## 0. 约束与术语

### 0.1 关键约束

- Minecraft 1.12.2 / Forge：
  - OpenGL 调用必须在渲染线程（主线程）执行。
  - 资源系统的全局 reload（stitch atlas / model bake）代价很高，必须避免频繁触发。
- GeckoLib（可选依赖）：
  - 其模型/动画文件通常通过 `IResourceManager` + `ResourceLocation` 读取。
- “资产安全”现实：
  - 客户端侧无法做到绝对防拷，目标是提高提取成本、控制分发范围、会话级解锁。

### 0.2 术语

- **构建（build）**：CPU 侧生成顶点数据（BufferBuilder/自定义 Mesh）。
- **渲染（render）**：GL 状态切换、纹理 bind、VBO 上传与 draw call。
- **RenderTask**：后台执行“构建”的任务。
- **RenderManager**：前台收集 buffers 并批量绘制。
- **SecureSession**：会话密钥与版本戳。

---

## 1. 总体目标

### 1.1 功能目标

- 支持 **GeckoLib** 模型（`.geo.json`）与动画（`.animation.json` / molang）。
- 支持 **OBJ** 模型（建议走独立加载与自管渲染管线）。
- 支持**多线程构建**（CPU）与**批量渲染**（GL 线程）。
- GeckoLib 提供**完整动画控制 API**（不仅是“播放动画名”）。
- 渲染系统独立：与机器逻辑/结构逻辑解耦，仅通过轻量接口/数据结构接入。
- 支持 **辉光（Bloom）渲染**（参考 MMCE 的 Bloom/全亮通道实现），并能在缺少后处理时回退到 fullbright pass。

### 1.2 非目标（阶段性）

- 不以兼容所有 Shader/OptiFine 组合为目标；优先稳定回退路径。
- 不立即实现 GPU skinning（可作为后续可选后端）。

---

## 2. 参考：MMCE 的 GeckoLib 资源读取与异步渲染

本项目工作区可参考的实现：

- 异步构建 + 批渲染：
  - `github.kasuminova.mmce.client.renderer.GeoModelRenderTask`
  - `github.kasuminova.mmce.client.renderer.ControllerModelRenderManager`
  - `github.kasuminova.mmce.client.renderer.RenderType`
- Lumenized / GT Bloom：
  - `github.kasuminova.mmce.client.renderer.BloomGeoModelRenderer`
- GeckoLib 外部加载器（资源读取）：
  - `github.kasuminova.mmce.client.resource.GeoModelExternalLoader`
  - `github.kasuminova.mmce.client.model.MachineControllerModel`

MMCE 的关键点：

1) **GeoModelExternalLoader** 使用 `ISelectiveResourceReloadListener`：当 `VanillaResourceType.MODELS` reload 时，批量加载 `.geo` 与动画文件。
2) 加载实现：`models.parallelStream()` 并行读取资源（通过 `IResourceManager.getResource` + GeckoLib loader）。
3) 渲染时：`GeoModelRenderTask` 在 ForkJoinPool 构建 BufferBuilder；前台 `ControllerModelRenderManager.draw()` 按（RenderType/Texture/Light）分组 draw。
4) Bloom：`RenderType.BLOOM` 把 lightmap 设为 `240,240` 实现 fullbright；在 GT/Lumenized 存在时走 bloom effect 回调。

本项目将复用其**管线思想**，但会在资源管理侧采用“方案 B：渲染系统自控加载时机”，并确保**解锁时不触发全局资源重载**。

---

## 3. 渲染子系统（独立模块）架构

### 3.1 分层

- **API 层（与业务解耦）**：
  - `Renderable` / `RenderProvider<T>`
  - `ModelSource`（Gecko/OBJ…）
  - `AnimationDriver`（GeckoLib 实现/空实现）
  - `RenderPass`（DEFAULT/TRANSPARENT/BLOOM…）

- **管线层（多线程构建、缓存、失效）**：
  - `RenderTask`（后台 build）
  - `TaskScheduler`（ForkJoinPool）
  - `RenderTaskCache`（按 key 缓存；支持 version 戳失效）

- **Backend 层（批渲染、VBO、Bloom 集成）**：
  - `RenderManager`（分组 draw）
  - `RenderTypeState`（preDraw/postDraw）
  - `ReusableVboUploader`（VBO 上传与 draw）
  - `BloomIntegration`（Lumenized/GT 可选）

### 3.2 核心数据结构（建议）

- `RenderKey`
  - `modelId`（ResourceLocation 或自定义 key）
  - `textureId`
  - `variant`（可选）
  - `animStateHash`（tick 驱动后的状态摘要）
  - `secureVersion`（来自 SecureSession）
  - `orientationHash`（朝向摘要：包含 facing/top/twist 等，避免“旋转不刷新”）
  - `flags`（可选：按需扩展，如 fullbright/禁用动画等）

- `RenderPlan`
  - `Map<RenderPass, List<BufferSlice>>`
  - `BufferSlice`：`(texture, light, bufferBuilder)`

- `RenderPass` / `RenderTypeState`
  - DEFAULT
  - TRANSPARENT（depthMask false/true）
  - BLOOM（fullbright 240/240 + 可选 polygon offset）
  - BLOOM_TRANSPARENT

---

## 4. 线程模型：多线程构建 + 主线程绘制

### 4.1 原则

- 后台线程可做：
  - 资源解密、文件读取、解析
  - 动画求值（骨骼矩阵）
  - 网格展开与 BufferBuilder 填充
- 主线程必须做：
  - bindTexture
  - 设置 lightmap
  - VBO 上传与 draw

### 4.2 RenderTask 生命周期

- `RenderTaskCache.getOrSubmit(renderKey)`：
  - 命中可用任务：直接使用其已生成 buffers
  - 过期（key 变化：model/texture/secureVersion/anim/orientation 等）：**新建 task 并重新 submit**（避免复用旧 snapshot）
- `RenderTask.compute()`（后台）：
  - 读取/解密资源（通过 AssetResolver）
  - 解析模型（GeckoLib/OBJ）
  - 动画求值（tick 数据）
  - 按 RenderPass 写入对应 BufferBuilder（可按静/动拆分）

### 4.3 批渲染（RenderManager）

参考 MMCE `ControllerModelRenderManager`：

- key 维度：`RenderPass -> Texture -> Light -> List<BufferBuilder>`
- 每帧：
  - 收集 buffers
  - `draw()`：按 pass 执行 preDraw/postDraw，按纹理 bind，按光照设置 lightmap，然后上传 VBO 并批量 draw

---

## 5. GeckoLib 支持（含完整动画控制 API）

### 5.1 资源加载（方案 B：渲染系统自控）

建议不要依赖全局资源 reload 驱动加载，而是：

- 引入 `AssetResolver`：
  - `open(ResourceLocation): InputStream`（内部可解密）
- 引入 `GeoModelRepository`：
  - `getModel(rl)` / `getAnimation(rl)`
  - 支持并行预热（类似 MMCE 的 `models.parallelStream()`，但触发时机由我们控制）

触发时机建议：

- 进入服务器并完成“SecureSession 解锁”后：
  - 异步预热“本次服务器允许的模型清单”
  - 或按需懒加载（第一次使用时再加载）

### 5.2 动画控制 API

对外提供两层：

- **抽象层**（不暴露 GeckoLib 类型）：
  - `AnimationHandle`：setState/parameters/pause/resume
  - `AnimationStateSnapshot`：用于 `animStateHash`
- **高级层**（当 GeckoLib 存在时启用）：
  - `GeckoAnimationHandle`：可访问底层 `AnimationController`，允许 predicate、transition、强制切换等高级操作。

### 5.3 通道规则（推荐沿用 MMCE 命名约定）

- **按 bone 名自动判定 RenderPass（与 MMCE 一致）**：
  - `emissive*` / `bloom*`：进入 BLOOM 层级
  - `transparent*` / `emissive_transparent*` / `bloom_transparent*`：进入 TRANSPARENT（若同时 bloom 则为 BLOOM_TRANSPARENT）

> 注意：不再单独维护 `LUMENIZED` pass / 别名；发光叠加都归入 BLOOM 层级，由后处理（若存在）决定具体辉光效果。

---

## 6. OBJ 支持（推荐：自管加载与渲染）

### 6.1 推荐路线

- 不走 Forge `OBJLoader` 的 bake/IBakedModel 管线。
- 使用自管 OBJ 解析：输出统一 Mesh/SubMesh 结构。
- 复用 RenderTask/RenderManager 的批渲染机制。

### 6.2 RenderPass 映射

- 通过 group/object/material 名称前缀映射：
  - `bloom_` / `emissive_` → BLOOM
  - `transparent_` → TRANSPARENT
- 也可扩展读取 `.mtl` 自定义属性。

---

## 7. Lumenized 辉光渲染（参考 MMCE）

### 7.1 双层策略

1) **基础 fullbright**：
   - `RenderPass.BLOOM` 在 `preDraw()` 设置 lightmap `240,240`（MMCE `RenderType.BLOOM`）
   - 兼容无 Lumenized 环境。

2) **后处理 bloom（可选）**：
   - 检测 Lumenized/GTCEu 是否存在：
    - 存在：注册 bloom effect 回调，在回调内调用 `RenderManager.drawBloomPasses(clearAfterDraw = true)`
    - 不存在：仅走 fullbright pass

### 7.2 避免强依赖

- 使用 `@Optional.Method(modid = "lumenized")` 或反射调用。
- `BloomIntegration` 抽象：
  - `registerIfAvailable()`
  - `enqueueBloomRenderable(renderable)`
  - `renderBloomPass()`

---

## 8. 安全资源（加密/解密）与资源管理优化（不触发全局 reload）

> 用户要求：磁盘密文，进服才解密，且避免资源重载；采用“提供者方案 B”，资源加载时机可控。

### 8.1 总体策略

- 解密能力与资源访问被封装在 `SecureAssetResolver`（渲染系统内部）。
- 解锁只更新：`SecureSession.key` + `SecureSession.versionStamp`。
- 不触发 Minecraft 全局资源 reload。
- 通过**版本戳**让渲染缓存/纹理缓存“自然失效并重建”。

### 8.2 SecureSession

- `locked`：version = 0，resolver 返回占位/拒绝访问。
- `unlocked`：version++，设置会话密钥（短期有效，换服失效）。

### 8.3 SecureAssetResolver（核心）

接口建议：

- `open(ResourceLocation): InputStream`
- `exists(ResourceLocation): Boolean`
- `currentVersion(): long`

实现要点：

- 资源存储为容器：`pm_assets.pak`（索引 + 条目密文）
- AES-GCM：每条目 nonce + tag，防篡改。
- 内存 LRU：解密后的 bytes 可缓存，但不落盘。

### 8.4 “不 reload 的热切换”机制

- RenderTask 的 `RenderKey` 包含 `secureVersion`。
- `secureVersion` 改变时：
  - 旧 task 自动判定过期 → 重建
- 纹理侧同理：
  - 使用 `DecryptingTexture`（自定义 ITextureObject）
  - 内部记录 `loadedAtVersion`，版本变则删除 GL texture 并重传

### 8.5 解锁后的异步预热（避免瞬时卡顿）

- 解锁后启动后台预热：
  - 只预热“服务器允许”的模型清单/附近实体实际用到的资源
  - 完成后触发局部刷新（纹理重传、task 重建），而非全局 reload。

---

## 9. 性能策略（避免 MMCE 的 CPU 高占用问题）

优先级从高到低：

1) **tick 驱动动画**：骨骼求值 20Hz，渲染帧仅插值/复用。
2) **dirty flag 精准失效**：只在模型/动画状态切换/secureVersion 变化时重建。
3) **静/动分离**：大量骨骼静态时，只重建动态子集。
4) **骨骼矩阵缓存**：层级矩阵链只在 dirty 时更新。
5) **剔除/LOD**：远距离停动画/只画静态。

GPU 路线（后续可选）：
- shader skinning（上传骨骼矩阵到 shader），但 1.12 兼容性风险高，应作为可选后端并保留 CPU 回退。

---

## 10. 实现步骤清单（建议按阶段落地）

### Phase 1：通用异步渲染管线（不含加密）

- 建立 RenderManager（分组 draw）
- 建立 RenderTask + TaskCache（ForkJoinPool）
- 建立 RenderPass/RenderTypeState（含 fullbright bloom pass）

### Phase 2：GeckoLib 适配（资源自控加载）

- 建立 GeoModelRepository（可并行预热）
- AnimationDriver（高级控制 API）
- bone 前缀 → pass 规则

### Phase 3：OBJ 适配

- OBJ 解析 → Mesh
- Mesh → BufferBuilder（按 pass/texture/透明拆分）

### Phase 4：SecureAssetResolver（加密解密，不触发 reload）

- 容器格式 + AES-GCM
- SecureSession 版本戳
- RenderKey 引入 secureVersion
- DecryptingTexture（按版本戳重传）
- 异步预热队列

### Phase 5：Lumenized Bloom 集成

- BloomIntegration 抽象
- 若 Lumenized/GT 存在则注册 bloom effect 回调

---

## 11. 附：与 MMCE 的差异（设计选择说明）

- MMCE 依赖资源 reload 驱动 `GeoModelExternalLoader` 更新；本方案采用**渲染系统自控加载**，避免全局 reload。
- MMCE 的异步构建/批渲染/RenderType 状态管理逻辑非常成熟，本方案建议直接复刻其思路并泛化成独立模块。
- Lumenized/GT Bloom：沿用 MMCE 的“fullbright pass + 可选 bloom effect”双层策略，保证无依赖时可回退。

