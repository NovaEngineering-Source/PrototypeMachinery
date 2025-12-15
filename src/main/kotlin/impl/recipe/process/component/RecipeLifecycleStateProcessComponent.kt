package github.kasuminova.prototypemachinery.impl.recipe.process.component

import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.process.component.RecipeProcessComponent
import github.kasuminova.prototypemachinery.api.recipe.process.component.RecipeProcessComponentType
import net.minecraft.nbt.NBTTagCompound

/**
 * Minimal per-process lifecycle state.
 * 进程生命周期最小状态：用于标记 start() 是否已成功执行。
 */
public class RecipeLifecycleStateProcessComponent(
    override val owner: RecipeProcess,
) : RecipeProcessComponent {

    override val type: RecipeProcessComponentType<*> = RecipeLifecycleStateProcessComponentType

    public var started: Boolean = false

    override fun serializeNBT(): NBTTagCompound {
        val tag = NBTTagCompound()
        tag.setBoolean("Started", started)
        return tag
    }

    override fun deserializeNBT(nbt: NBTTagCompound) {
        started = nbt.getBoolean("Started")
    }
}
