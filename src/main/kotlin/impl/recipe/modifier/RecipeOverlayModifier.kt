package github.kasuminova.prototypemachinery.impl.recipe.modifier

/**
 * Modifier that mutates per-process recipe overlay.
 * 会修改 per-process overlay 的修改器（用于动态配方修改）。
 */
public interface RecipeOverlayModifier {

    /** Unique modifier id / 唯一修改器 id */
    public val id: String

    /** Apply modifications into the process overlay. / 将修改写入进程 overlay。 */
    public fun apply(ctx: RecipeOverlayModifierContext)
}
