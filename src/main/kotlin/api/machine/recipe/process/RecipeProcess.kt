package github.kasuminova.prototypemachinery.api.machine.recipe.process

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeMap
import github.kasuminova.prototypemachinery.api.machine.recipe.MachineRecipe
import github.kasuminova.prototypemachinery.api.machine.recipe.process.component.RecipeProcessComponent
import github.kasuminova.prototypemachinery.api.machine.recipe.process.component.RecipeProcessComponentType
import net.minecraft.nbt.NBTTagCompound

public interface RecipeProcess {

    public val owner: MachineInstance

    public val recipe: MachineRecipe

    public val attributeMap: MachineAttributeMap

    public var status: RecipeProcessStatus

    public val components: Map<RecipeProcessComponentType<*>, RecipeProcessComponent>

    public fun <C : RecipeProcessComponent> get(type: RecipeProcessComponentType<C>): C? {
        @Suppress("UNCHECKED_CAST")
        return components[type] as? C
    }

    public fun serializeNBT(): NBTTagCompound

    public fun deserializeNBT(nbt: NBTTagCompound)

}