package github.kasuminova.prototypemachinery.api.machine.recipe.process.component

import github.kasuminova.prototypemachinery.api.machine.recipe.process.RecipeProcess
import net.minecraft.nbt.NBTTagCompound

public interface RecipeProcessComponent {

    public val type: RecipeProcessComponentType<*>

    public val owner: RecipeProcess

    public fun serializeNBT(): NBTTagCompound

    public fun deserializeNBT(nbt: NBTTagCompound)

}