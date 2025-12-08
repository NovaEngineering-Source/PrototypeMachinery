package github.kasuminova.prototypemachinery.api.machine

import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType
import github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure
import net.minecraft.util.ResourceLocation

/**
 * # MachineType - Machine Type Definition
 * # MachineType - 机械类型定义
 * 
 * Defines a type of multi-block machine, including its structure, components, and metadata.
 * This is the blueprint that describes what a machine looks like and how it functions.
 * 
 * 定义多方块机械的类型，包括其结构、组件和元数据。
 * 这是描述机械外观和功能的蓝图。
 * 
 * ## Key Responsibilities / 主要职责
 * 
 * - **Structure Definition**: Defines the physical layout and block pattern of the machine
 *   - **结构定义**: 定义机械的物理布局和方块模式
 * 
 * - **Component Types**: Specifies what component types this machine can have
 *   - **组件类型**: 指定此机械可以拥有的组件类型
 * 
 * - **Identification**: Provides unique identification via ResourceLocation
 *   - **标识**: 通过 ResourceLocation 提供唯一标识
 * 
 * ## Related Classes / 相关类
 * 
 * - [MachineInstance] - Runtime instance of this machine type
 * - [MachineStructure] - Physical structure definition
 * - [MachineComponentType] - Component type definitions
 * - [MachineTypeRegistry] - Registry for all machine types
 * - [github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI] - API access point
 * 
 * ## Example / 示例
 * 
 * ```kotlin
 * // Creating a custom machine type
 * val myMachineType = object : MachineType {
 *     override val id = ResourceLocation("mymod", "my_machine")
 *     override val name = "My Machine"
 *     override val structure: MachineStructure = // ... structure definition
 *     override val componentTypes = setOf(/* component types */)
 * }
 * ```
 * 
 * @see MachineInstance
 * @see MachineStructure
 * @see MachineComponentType
 */
public interface MachineType {

    /**
     * Unique identifier for this machine type.
     * Used for registration and lookup.
     * 
     * 此机械类型的唯一标识符。
     * 用于注册和查找。
     */
    public val id: ResourceLocation

    /**
     * Display name of this machine type.
     * Used in UI and localization.
     * 
     * 此机械类型的显示名称。
     * 用于 UI 和本地化。
     */
    public val name: String

    /**
     * Physical structure definition of this machine.
     * Defines the block pattern, orientation support, and validation rules.
     * 
     * 此机械的物理结构定义。
     * 定义方块模式、朝向支持和验证规则。
     */
    public val structure: MachineStructure

    /**
     * Set of component types this machine supports.
     * Components are functional parts like item containers, energy storage, etc.
     * 
     * 此机械支持的组件类型集合。
     * 组件是功能部件，如物品容器、能量存储等。
     */
    public val componentTypes: Set<MachineComponentType<*>>

}