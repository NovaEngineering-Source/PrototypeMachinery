package github.kasuminova.prototypemachinery.impl.key.fluid

import github.kasuminova.prototypemachinery.api.key.PMKey
import net.minecraftforge.fluids.FluidStack

/**
 * # FluidStackKey
 * # 流体栈键
 *
 * Interface for keys representing a FluidStack.
 *
 * 表示流体栈的键的接口。
 */
public interface PMFluidKey {

    /**
     * The underlying unique prototype key.
     *
     * 底层唯一原型键。
     */
    public val uniqueKey: UniquePMFluidKey

    /**
     * Casts this to the base PMKey class for usage in generic contexts.
     *
     * 将此转换为基础 PMKey 类，以便在泛型上下文中使用。
     */
    public fun asPMKey(): PMKey<FluidStack>

}
