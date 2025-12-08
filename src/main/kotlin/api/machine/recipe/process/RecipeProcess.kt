package github.kasuminova.prototypemachinery.api.machine.recipe.process

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeMap
import github.kasuminova.prototypemachinery.api.machine.recipe.MachineRecipe
import github.kasuminova.prototypemachinery.api.machine.recipe.process.component.RecipeProcessComponent
import github.kasuminova.prototypemachinery.api.machine.recipe.process.component.RecipeProcessComponentType
import net.minecraft.nbt.NBTTagCompound

/**
 * # RecipeProcess - Runtime Recipe Execution
 * # RecipeProcess - 运行时配方执行实例
 *
 * Represents a running instance of a recipe on a machine. Tracks state, components,
 * attributes, and serialization for persistence.
 *
 * 表示在机械上运行的配方实例。跟踪状态、组件、属性，并负责持久化序列化。
 *
 * ## Lifecycle / 生命周期
 * 1) **Creation**: Spawned when a recipe starts
 *    **创建**: 当配方开始时创建
 * 2) **Ticking**: Systems advance process each tick using requirement transactions
 *    **Tick**: 系统使用需求事务在每个 tick 推进进程
 * 3) **Completion/Failure**: Status updated to SUCCESS/FAILURE and cleaned up
 *    **完成/失败**: 状态更新为成功/失败并清理
 * 4) **Serialization**: State stored to NBT for chunk save / load
 *    **序列化**: 状态存储到 NBT 以用于区块保存/加载
 *
 * ## Components / 组件
 * - Process components (e.g., progress timer, output buffer) are keyed by [RecipeProcessComponentType]
 * - 进程组件（如进度计时器、输出缓冲）通过 [RecipeProcessComponentType] 进行索引
 *
 * ## Attributes / 属性
 * Uses [MachineAttributeMap] to provide per-process attributes (speed, efficiency) decoupled from machine base
 * 使用 [MachineAttributeMap] 提供与机械基础属性解耦的每进程属性（速度、效率）
 *
 * @see MachineRecipe
 * @see RecipeProcessComponent
 * @see RecipeProcessStatus
 */
public interface RecipeProcess {

    /**
     * The machine instance owning this process.
     * 运行此进程的机械实例。
     */
    public val owner: MachineInstance

    /**
     * The recipe being executed.
     * 正在执行的配方。
     */
    public val recipe: MachineRecipe

    /**
     * Attribute map affecting this process (speed, efficiency, etc.).
     * 影响此进程的属性映射（速度、效率等）。
     */
    public val attributeMap: MachineAttributeMap

    /**
     * Current status of the process (e.g., RUNNING, SUCCESS, FAILURE).
     * 进程的当前状态（运行中/成功/失败）。
     */
    public var status: RecipeProcessStatus

    /**
     * Components attached to this process, indexed by component type.
     * 附加到此进程的组件，按组件类型索引。
     */
    public val components: Map<RecipeProcessComponentType<*>, RecipeProcessComponent>

    /**
     * Convenience lookup for a specific process component by type.
     * 通过类型便捷获取特定进程组件。
     */
    public fun <C : RecipeProcessComponent> get(type: RecipeProcessComponentType<C>): C? {
        @Suppress("UNCHECKED_CAST")
        return components[type] as? C
    }

    /**
     * Serialize process state to NBT for persistence.
     * 将进程状态序列化到 NBT 以进行持久化。
     */
    public fun serializeNBT(): NBTTagCompound

    /**
     * Deserialize process state from NBT.
     * 从 NBT 反序列化进程状态。
     */
    public fun deserializeNBT(nbt: NBTTagCompound)

}