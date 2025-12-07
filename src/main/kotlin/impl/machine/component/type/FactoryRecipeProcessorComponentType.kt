package github.kasuminova.prototypemachinery.impl.machine.component.type

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType
import github.kasuminova.prototypemachinery.api.machine.component.system.MachineSystem
import github.kasuminova.prototypemachinery.api.machine.component.type.FactoryRecipeProcessorComponent
import github.kasuminova.prototypemachinery.impl.machine.component.FactoryRecipeProcessorComponentImpl
import github.kasuminova.prototypemachinery.impl.machine.component.system.FactoryRecipeProcessorSystem
import net.minecraft.util.ResourceLocation

public object FactoryRecipeProcessorComponentType : MachineComponentType<FactoryRecipeProcessorComponent> {

    override val id: ResourceLocation = ResourceLocation("prototypemachinery", "factory_recipe_processor")

    override val system: MachineSystem<FactoryRecipeProcessorComponent> = FactoryRecipeProcessorSystem

    override fun createComponent(machine: MachineInstance): FactoryRecipeProcessorComponent = FactoryRecipeProcessorComponentImpl(
        type = this,
        owner = machine
    )

}