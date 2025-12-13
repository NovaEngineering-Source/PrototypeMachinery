package github.kasuminova.prototypemachinery.api.machine.component.type

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType
import github.kasuminova.prototypemachinery.api.machine.component.system.MachineSystem
import github.kasuminova.prototypemachinery.impl.machine.component.ZSDataComponentImpl
import github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.data.ZenMachineData
import net.minecraft.util.ResourceLocation

/**
 * Component type for machine-level script data storage.
 * 机器级脚本数据存储组件类型。
 */
public object ZSDataComponentType : MachineComponentType<ZSDataComponent> {

    override val id: ResourceLocation = ResourceLocation("prototypemachinery", "zs_data")

    // No system needed - data is accessed directly, no tick logic
    // 不需要系统 - 数据直接访问，无 tick 逻辑
    override val system: MachineSystem<ZSDataComponent>? = null

    override fun createComponent(machine: MachineInstance): ZSDataComponent {
        return ZSDataComponentImpl(machine, this)
    }
}

/**
 * Machine component for storing ZenScript-accessible data.
 * 用于存储可从 ZenScript 访问的数据的机器组件。
 */
public interface ZSDataComponent : MachineComponent, MachineComponent.Serializable, MachineComponent.Synchronizable {

    /**
     * The mutable data container accessible from ZenScript.
     * 可从 ZenScript 访问的可变数据容器。
     */
    public val data: ZenMachineData

}
