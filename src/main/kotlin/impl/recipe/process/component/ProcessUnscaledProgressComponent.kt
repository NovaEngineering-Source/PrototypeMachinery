package github.kasuminova.prototypemachinery.impl.recipe.process.component

import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.process.component.RecipeProcessComponent
import github.kasuminova.prototypemachinery.api.recipe.process.component.RecipeProcessComponentType
import net.minecraft.nbt.NBTTagCompound

/**
 * Tracks the number of successful machine ticks consumed by this process, independent of PROCESS_SPEED.
 *
 * 用于记录进程已“成功推进”的机器 tick 数（不受 PROCESS_SPEED 影响）。
 *
 * Rationale:
 * - `RecipeProcessStatus.progress` is advanced by PROCESS_SPEED (scaled progress).
 * - Some requirements (e.g. Checkpoint) may want to use an unscaled tick timeline.
 */
public class ProcessUnscaledProgressComponent(
    override val owner: RecipeProcess,
) : RecipeProcessComponent {

    override val type: RecipeProcessComponentType<*> = ProcessUnscaledProgressComponentType

    /** Successful ticks advanced (unscaled). */
    public var ticks: Float = 0.0f

    override fun serializeNBT(): NBTTagCompound {
        val tag = NBTTagCompound()
        tag.setFloat("Ticks", ticks)
        return tag
    }

    override fun deserializeNBT(nbt: NBTTagCompound) {
        ticks = nbt.getFloat("Ticks")
    }
}
