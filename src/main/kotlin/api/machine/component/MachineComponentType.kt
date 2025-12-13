package github.kasuminova.prototypemachinery.api.machine.component

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.component.system.MachineSystem
import net.minecraft.util.ResourceLocation

/**
 * # MachineComponentType - Component Type Definition and Factory
 * # MachineComponentType - 组件类型定义和工厂
 * 
 * Defines a type of machine component and provides a factory method to create instances.
 * Acts as both a type identifier and a component factory in the ECS pattern.
 * 
 * 定义机械组件的类型并提供创建实例的工厂方法。
 * 在 ECS 模式中同时充当类型标识符和组件工厂。
 * 
 * ## Responsibilities / 职责
 * 
 * 1. **Type Identification**: Unique ID for this component type
 *    **类型标识**: 此组件类型的唯一 ID
 * 
 * 2. **System Binding**: Links components to their processing System
 *    **系统绑定**: 将组件链接到其处理系统
 * 
 * 3. **Component Factory**: Creates new component instances
 *    **组件工厂**: 创建新的组件实例
 * 
 * ## Component Type Registry / 组件类型注册表
 * 
 * Component types are typically registered per-machine-type basis.
 * Each MachineType declares which component types it supports.
 * 
 * 组件类型通常按机械类型注册。
 * 每个 MachineType 声明它支持的组件类型。
 * 
 * ## Related Classes / 相关类
 * 
 * - [MachineComponent] - Component instances created by this type
 * - [MachineSystem] - System that processes components of this type
 * - [github.kasuminova.prototypemachinery.api.machine.MachineType] - Declares supported component types
 * - [MachineComponentMap] - Stores component instances by type
 * 
 * ## Example / 示例
 * 
 * ```kotlin
 * // Define a custom component type
 * object MyComponentType : MachineComponentType<MyComponent> {
 *     override val id = ResourceLocation(\"mymod\", \"my_component\")
 *     override val system = MyComponentSystem
 *     
 *     override fun createComponent(machine: MachineInstance): MyComponent {
 *         return MyComponentImpl(machine)
 *     }
 * }
 * ```
 * 
 * @param C The component type this factory creates / 此工厂创建的组件类型
 * @see MachineComponent
 * @see MachineSystem
 */
public interface MachineComponentType<C : MachineComponent> {

    /**
     * Unique identifier for this component type.
     * Used for lookups and registration.
     * 
     * 此组件类型的唯一标识符。
     * 用于查找和注册。
     */
    public val id: ResourceLocation

    /**
     * The system that processes components of this type.
     * Determines how components behave during ticks and events.
     * 
     * Returns null if the component does not require tick processing or event handling.
     * 
     * 处理此类型组件的系统。
     * 决定组件在 tick 和事件期间的行为方式。
     * 
     * 如果组件不需要 tick 或事件处理，则返回 null。
     */
    public val system: MachineSystem<C>?

    /**
     * The dependencies of this component type.
     * Used for topological sorting of components and systems.
     *
     * 此组件类型的依赖项。
     * 用于组件和系统的拓扑排序。
     */
    public val dependencies: Set<MachineComponentType<*>> get() = emptySet()

    /**
     * Factory method to create a new component instance.
     * Called during machine initialization.
     * 
     * 创建新组件实例的工厂方法。
     * 在机械初始化期间调用。
     * 
     * @param machine The machine instance that will own this component / 将拥有此组件的机械实例
     * @return A new component instance / 新的组件实例
     */
    public fun createComponent(machine: MachineInstance): C

}