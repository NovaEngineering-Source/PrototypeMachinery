package github.kasuminova.prototypemachinery.api.recipe.selective

import org.jetbrains.annotations.ApiStatus

/**
 * A runtime modifier invoked when a selection is committed.
 *
 * 选择 commit 时调用的运行时修改器。
 */
@ApiStatus.Experimental
public fun interface SelectiveModifier {

    public fun apply(context: SelectiveContext)

}
