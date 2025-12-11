package github.kasuminova.prototypemachinery.api.recipe.process.component

import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import net.minecraft.nbt.NBTTagCompound

/**
 * Runtime component attached to a RecipeProcess (e.g., progress timer, buffer).
 * 运行时进程组件（如进度计时器、缓存等）。
 */
public interface RecipeProcessComponent {

    public val type: RecipeProcessComponentType<*>

    public val owner: RecipeProcess

    public fun serializeNBT(): NBTTagCompound

    public fun deserializeNBT(nbt: NBTTagCompound)

}