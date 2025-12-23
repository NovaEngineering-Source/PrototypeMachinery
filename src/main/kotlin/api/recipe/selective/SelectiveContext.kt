package github.kasuminova.prototypemachinery.api.recipe.selective

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeModifier
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeType
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
/**
 * Runtime context passed to selective modifiers.
 *
 * 传递给选择性修改器的运行时上下文。
 */
public interface SelectiveContext {

    public val machine: MachineInstance

    public val process: RecipeProcess

    /** A stable identifier of the selection instance (per recipe). / 选择实例的稳定标识（配方内）。 */
    public val selectionId: String

    /**
     * Add or replace an attribute modifier on this process.
     *
     * 向当前进程添加或替换一个属性修改器。
     */
    public fun putProcessAttributeModifier(attribute: MachineAttributeType, modifier: MachineAttributeModifier)

}
