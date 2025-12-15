package github.kasuminova.prototypemachinery.api.machine

import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeMap
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentMap
import github.kasuminova.prototypemachinery.api.machine.component.StructureComponentMap
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.common.block.entity.BlockEntity

/**
 * # MachineInstance - Machine Runtime Instance
 * # MachineInstance - 机械运行时实例
 * 
 * Represents a runtime instance of a machine in the world. This is the "Entity" in the ECS architecture.
 * Each placed multi-block machine has one MachineInstance that manages its state, components, and active processes.
 * 
 * 表示世界中机械的运行时实例。这是 ECS 架构中的"实体"。
 * 每个放置的多方块机械都有一个 MachineInstance 来管理其状态、组件和活动进程。
 * 
 * ## Architecture / 架构
 * 
 * This class follows the **Entity-Component-System (ECS)** pattern:
 * - **Entity**: MachineInstance itself
 * - **Components**: Stored in componentMap (e.g., ItemContainer, EnergyStorage)
 * - **Systems**: Process components and events (e.g., RecipeExecutor, TickableSystem)
 * 
 * 本类遵循 **实体-组件-系统 (ECS)** 模式:
 * - **实体**: MachineInstance 本身
 * - **组件**: 存储在 componentMap 中 (例如: ItemContainer, EnergyStorage)
 * - **系统**: 处理组件和事件 (例如: RecipeExecutor, TickableSystem)
 * 
 * ## Lifecycle / 生命周期
 * 
 * 1. **Creation**: Instantiated when machine structure is validated
 *    **创建**: 当机械结构验证通过时实例化
 * 
 * 2. **Component Initialization**: Components are created based on MachineType definition
 *    **组件初始化**: 根据 MachineType 定义创建组件
 * 
 * 3. **Runtime**: Systems tick components, processes execute recipes
 *    **运行时**: 系统 tick 组件，进程执行配方
 * 
 * 4. **Serialization**: State saved to/loaded from NBT
 *    **序列化**: 状态保存到/从 NBT 加载
 * 
 * ## Related Classes / 相关类
 * 
 * - [MachineType] - Blueprint/definition for this instance
 * - [MachineComponentMap] - Manages all components
 * - [MachineAttributeMap] - Manages dynamic attributes (speed, efficiency, etc.)
 * - [RecipeProcess] - Active recipe execution processes
 * - [github.kasuminova.prototypemachinery.api.machine.component.system.MachineSystem] - Systems that process this instance
 * - [BlockEntity] - Associated tile entity in the world
 * 
 * ## Example / 示例
 * 
 * ```kotlin
 * // Accessing components
 * val itemContainers = machineInstance.componentMap.getComponentsByType<ItemContainerComponent>()
 * 
 * // Checking active processes
 * val isProcessing = machineInstance.activeProcesses.isNotEmpty()
 * 
 * // Modifying attributes
 * val speedAttr = machineInstance.attributeMap.getAttribute(MachineAttributes.SPEED)
 * speedAttr?.addModifier(MachineAttributeModifier.multiplyTotal("upgrade_overclock", 0.5))
 * ```
 * 
 * @see MachineType
 * @see MachineComponentMap
 * @see MachineAttributeMap
 * @see RecipeProcess
 */
public interface MachineInstance {

    /**
     * The type definition of this machine.
     * Contains structure, supported components, and metadata.
     * 
     * 此机械的类型定义。
     * 包含结构、支持的组件和元数据。
     */
    public val type: MachineType

    /**
     * The associated tile entity in the world.
     * Used for world interaction and position information.
     * 
     * 世界中关联的 Tile Entity。
     * 用于世界交互和位置信息。
     */
    public val blockEntity: BlockEntity

    /**
     * Map of all components belonging to this machine.
     * Components provide functionality like item/fluid/energy storage.
     * 
     * 属于此机械的所有组件映射。
     * 组件提供如物品/流体/能量存储等功能。
     */
    public val componentMap: MachineComponentMap

    /**
     * Components provided by the currently formed structure.
     *
     * 当前已形成结构所提供的组件视图（例如各类容器/端口）。
     *
     * - 该表应在结构刷新/形成期间重建
     * - 不包含 systems 概念
     */
    public val structureComponentMap: StructureComponentMap
    /**
     * Map of dynamic attributes affecting machine behavior.
     * Attributes can be modified at runtime (e.g., speed, efficiency, consumption).
     * 
     * 影响机械行为的动态属性映射。
     * 属性可以在运行时修改(例如: 速度、效率、消耗)。
     */
    public val attributeMap: MachineAttributeMap

    /**
     * Check if the machine structure is fully formed.
     * 检查机械结构是否已完全形成。
     * 
     * @return true if the structure is valid and formed, false otherwise
     */
    public fun isFormed(): Boolean

    /**
     * Request a sync for a specific component.
     * 请求同步特定组件。
     *
     * @param component The component to sync / 要同步的组件
     */
    public fun syncComponent(component: MachineComponent.Synchronizable)
}