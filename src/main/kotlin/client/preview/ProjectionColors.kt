package github.kasuminova.prototypemachinery.client.preview

/**
 * Color definitions for structure projection preview visualization modes.
 *
 * 结构投影预览各种状态的颜色定义。
 */
internal object ProjectionColors {

    /** Color: RGBA as floats. */
    data class Color(val r: Float, val g: Float, val b: Float, val a: Float)

    // ====== Status Colors (for OUTLINE mode) ======

    /** Color for matching blocks. / 匹配方块的颜色。 */
    val MATCH = Color(0.2f, 1.0f, 0.2f, 0.20f)

    /** Color for mismatched blocks. / 不匹配方块的颜色。 */
    val MISMATCH = Color(1.0f, 0.2f, 0.2f, 0.55f)

    /** Color for unloaded chunks. / 未加载方块的颜色。 */
    val UNLOADED = Color(1.0f, 0.85f, 0.2f, 0.25f)

    /** Color for unknown requirement blocks. / 未知需求方块的颜色。 */
    val UNKNOWN = Color(0.7f, 0.7f, 0.7f, 0.25f)

    // ====== Ghost Mode Colors ======

    /** Main ghost color for exact block state requirement. / 精确方块需求的 Ghost 颜色。 */
    val GHOST_EXACT = Color(0.55f, 0.85f, 1.0f, 0.32f)

    /** Default ghost color for generic requirements. / 通用需求的默认 Ghost 颜色。 */
    val GHOST_DEFAULT = Color(0.78f, 0.78f, 0.78f, 0.22f)

    // ====== Outline Mode Colors (BOTH mode suffix) ======

    /** Outline color for unloaded blocks in BOTH mode. / BOTH 模式中未加载方块的描边颜色。 */
    val OUTLINE_UNLOADED = Color(1.0f, 0.85f, 0.2f, 0.35f)

    /** Outline color for other blocks in BOTH mode. / BOTH 模式中其他方块的描边颜色。 */
    val OUTLINE_DEFAULT = Color(1.0f, 1.0f, 1.0f, 0.28f)

    // ====== Helper ======

    /**
     * Get status color for OUTLINE mode.
     * 根据状态获取 OUTLINE 模式的颜色。
     */
    fun statusColor(status: WorldProjectionManager.Status): Color = when (status) {
        WorldProjectionManager.Status.MATCH -> MATCH
        WorldProjectionManager.Status.MISMATCH -> MISMATCH
        WorldProjectionManager.Status.UNLOADED -> UNLOADED
        WorldProjectionManager.Status.UNKNOWN -> UNKNOWN
    }

    /**
     * Get ghost color for requirement.
     * 根据需求类型获取 Ghost 颜色。
     */
    fun ghostColor(req: github.kasuminova.prototypemachinery.api.machine.structure.preview.BlockRequirement, unloadedBase: Color? = null): Color {
        if (unloadedBase != null) {
            return Color(unloadedBase.r, unloadedBase.g, unloadedBase.b, unloadedBase.a)
        }
        return if (req is github.kasuminova.prototypemachinery.api.machine.structure.preview.ExactBlockStateRequirement) {
            GHOST_EXACT
        } else {
            GHOST_DEFAULT
        }
    }
}
