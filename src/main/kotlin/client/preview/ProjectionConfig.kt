package github.kasuminova.prototypemachinery.client.preview

/**
 * Global configuration for structure projection preview rendering and interaction.
 *
 * 结构投影预览的全局配置常量。
 */
internal object ProjectionConfig {

    // ====== Performance Tuning ======

    /** Maximum block status checks per client tick. / 每 tick 最多检查多少方块状态。 */
    const val MAX_STATUS_CHECKS_PER_TICK: Int = 2048

    /** Maximum blocks rendered per frame in immediate mode. / 即时渲染模式下每帧最多渲染多少方块。 */
    const val MAX_BLOCKS_RENDER_PER_FRAME: Int = 4096

    /** Maximum chunks rendered per frame in VBO mode. / VBO 模式下每帧最多渲染多少分块。 */
    const val MAX_CHUNKS_RENDER_PER_FRAME: Int = 96

    /** Default render distance (blocks). / 默认渲染距离（格）。 */
    const val DEFAULT_MAX_RENDER_DISTANCE: Double = 64.0

    /** If structure size is under this, render everything in one frame. / 结构大小低于此值则一帧内渲染全部。 */
    const val RENDER_ALL_IF_UNDER: Int = 8192

    /** If chunk count is under this, render all visible chunks in one frame. / 分块数量低于此值则一帧内渲染全部可见分块。 */
    const val RENDER_ALL_CHUNKS_IF_UNDER: Int = 128

    // ====== Rendering Visuals ======

    /** Alpha multiplier for block model rendering. / 方块模型渲染的 alpha 乘数。 */
    const val BLOCK_MODEL_OVERLAY_ALPHA: Float = 0.65f

    /** Z-fight reduction: polygon offset factor. / Z 竞争缓解：多边形偏移系数。 */
    const val POLYGON_OFFSET_FACTOR: Float = -1.0f

    /** Z-fight reduction: polygon offset units. / Z 竞争缓解：多边形偏移单位。 */
    const val POLYGON_OFFSET_UNITS: Float = -10.0f

    /** Small shrink for ghost aabb to avoid z-fighting. / Ghost AAB 的收缩量以避免 z 竞争。 */
    const val GHOST_AABB_SHRINK: Float = 0.002f

    /** Fullbright lightmap UV for preview blocks. 0x00F000F0 = (240, 240) in short pairs. */
    const val FULLBRIGHT_LIGHTMAP_UV: Int = 0x00F000F0

    // ====== Ray Marching ======

    /** Maximum steps in ray-march DDA (for HUD overlay pickup). / 射线步进最大步数（用于 HUD 拾取）。 */
    const val RAY_MARCH_MAX_STEPS: Int = 512

    // ====== Caching ======

    /** LRU capacity for model cache. / 模型缓存的 LRU 容量。 */
    const val MODEL_CACHE_SIZE: Int = 32

    /** LRU capacity for ghost/outline render cache. / Ghost 等渲染缓存的 LRU 容量。 */
    const val RENDER_CACHE_SIZE: Int = 16

    /** LRU capacity for block model render cache (slower to build). / 方块模型渲染缓存的 LRU 容量。 */
    const val BLOCK_MODEL_CACHE_SIZE: Int = 8

    // ====== Chunk Size ======

    /** Chunk dimensions (both in relative structure space). / 分块尺寸（在相对结构空间中）。 */
    const val CHUNK_SIZE: Int = 16
}
