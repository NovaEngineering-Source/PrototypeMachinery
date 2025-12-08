package github.kasuminova.prototypemachinery.api.machine.component

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import net.minecraft.nbt.NBTTagCompound

/**
 * # MachineComponent - Machine Component (ECS Component)
 * # MachineComponent - 机械组件 (ECS 组件)
 * 
 * Base interface for all machine components. Components are the "C" in Entity-Component-System (ECS).
 * They store data and state, while Systems process them.
 * 
 * 所有机械组件的基础接口。组件是实体-组件-系统 (ECS) 中的 "C"。
 * 它们存储数据和状态，而系统处理它们。
 * 
 * ## Component Types / 组件类型
 * 
 * Common component implementations:
 * - ItemContainerComponent: Stores items in slots
 * - FluidContainerComponent: Stores fluids in tanks
 * - EnergyContainerComponent: Stores energy
 * - RecipeProcessorComponent: Manages recipe execution
 * 
 * 常见组件实现:
 * - ItemContainerComponent: 在槽位中存储物品
 * - FluidContainerComponent: 在储罐中存储流体
 * - EnergyContainerComponent: 存储能量
 * - RecipeProcessorComponent: 管理配方执行
 * 
 * ## Lifecycle / 生命周期
 * 
 * 1. **Creation**: Created by MachineComponentType.createComponent()
 *    **创建**: 由 MachineComponentType.createComponent() 创建
 * 
 * 2. **Loading**: onLoad() called when machine loads
 *    **加载**: 机械加载时调用 onLoad()
 * 
 * 3. **Processing**: Systems process component each tick
 *    **处理**: 系统每 tick 处理组件
 * 
 * 4. **Unloading**: onUnload() called when machine unloads
 *    **卸载**: 机械卸载时调用 onUnload()
 * 
 * ## Serialization / 序列化
 * 
 * Components that need to persist data should implement [Serializable].
 * 需要持久化数据的组件应实现 [Serializable]。
 * 
 * ## Related Classes / 相关类
 * 
 * - [MachineComponentType] - Component type definition and factory
 * - [MachineComponentMap] - Container for all components in a machine
 * - [github.kasuminova.prototypemachinery.api.machine.component.system.MachineSystem] - Processes components
 * - [MachineInstance] - Owner entity
 * 
 * @see MachineComponentType
 * @see MachineComponentMap
 * @see github.kasuminova.prototypemachinery.api.machine.component.system.MachineSystem
 */
public interface MachineComponent {

    /**
     * The type definition of this component.
     * Determines which System processes this component.
     * 
     * 此组件的类型定义。
     * 决定哪个系统处理此组件。
     */
    public val type: MachineComponentType<*>

    /**
     * The machine instance that owns this component.
     * 
     * 拥有此组件的机械实例。
     */
    public val owner: MachineInstance

    /**
     * Optional provider object (e.g., TileEntity).
     * Used for components that need to interact with the world.
     * 
     * 可选的提供者对象 (例如: TileEntity)。
     * 用于需要与世界交互的组件。
     */
    public val provider: Any?

    /**
     * Called when the component is loaded.
     * Use this to initialize state or register capabilities.
     * 
     * 组件加载时调用。
     * 用于初始化状态或注册能力。
     */
    public fun onLoad() {}

    /**
     * Called when the component is unloaded.
     * Use this to clean up resources or unregister handlers.
     * 
     * 组件卸载时调用。
     * 用于清理资源或注销处理器。
     */
    public fun onUnload() {}

    /**
     * # Serializable - NBT Serialization Support
     * # Serializable - NBT 序列化支持
     * 
     * Marker interface for components that need to save/load data.
     * 
     * 需要保存/加载数据的组件的标记接口。
     */
    public interface Serializable : MachineComponent {

        /**
         * Serialize component data to NBT.
         * 
         * 将组件数据序列化为 NBT。
         * 
         * @return NBT tag compound containing component data / 包含组件数据的 NBT 标签复合
         */
        public fun writeNBT(): NBTTagCompound

        /**
         * Deserialize component data from NBT.
         * 
         * 从 NBT 反序列化组件数据。
         * 
         * @param nbt NBT tag compound to read from / 要读取的 NBT 标签复合
         */
        public fun readNBT(nbt: NBTTagCompound)

    }

}