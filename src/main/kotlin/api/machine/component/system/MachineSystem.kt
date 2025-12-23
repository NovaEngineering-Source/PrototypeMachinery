@file:Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")

package github.kasuminova.prototypemachinery.api.machine.component.system

import github.kasuminova.prototypemachinery.api.ecs.ComponentSystem
import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent
import github.kasuminova.prototypemachinery.api.machine.event.MachineEvent
import github.kasuminova.prototypemachinery.api.machine.event.MachineTickEvent

/**
 * # MachineSystem - Component Processing System (ECS System)
 * # MachineSystem - 组件处理系统 (ECS 系统)
 * 
 * Base interface for all systems that process machine components. This is the "S" in Entity-Component-System (ECS).
 * Systems contain logic and behavior, processing components during ticks and events.
 * 
 * 处理机械组件的所有系统的基础接口。这是实体-组件-系统 (ECS) 中的 "S"。
 * 系统包含逻辑和行为，在 tick 和事件期间处理组件。
 * 
 * ## Event-Driven Architecture / 事件驱动架构
 * 
 * Modern approach using events instead of direct tick methods:
 * - Tick phases (Pre/Normal/Post) are converted to MachineTickEvent
 * - Systems override onEvent() to handle all event types uniformly
 * - Allows custom events to be dispatched alongside tick events
 * 
 * 使用事件而非直接 tick 方法的现代方法:
 * - Tick 阶段 (Pre/Normal/Post) 转换为 MachineTickEvent
 * - 系统重写 onEvent() 以统一处理所有事件类型
 * - 允许自定义事件与 tick 事件一起分发
 * 
 * ## Tick Phases / Tick 阶段
 * 
 * 1. **Pre-Tick**: Preparation phase (input validation, resource checking)
 *    **前置 Tick**: 准备阶段（输入验证、资源检查）
 * 
 * 2. **Normal-Tick**: Main processing phase (recipe execution, energy transfer)
 *    **正常 Tick**: 主处理阶段（配方执行、能量传输）
 * 
 * 3. **Post-Tick**: Cleanup phase (output handling, state updates)
 *    **后置 Tick**: 清理阶段（输出处理、状态更新）
 * 
 * ## System Examples / 系统示例
 * 
 * - RecipeProcessorSystem: Executes recipes
 * - EnergyRequirementSystem: Manages energy consumption
 * - ItemRequirementSystem: Handles item inputs/outputs
 * 
 * ## Related Classes / 相关类
 * 
 * - [MachineComponent] - Components processed by this system
 * - [github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType] - Links components to systems
 * - [MachineEvent] - Events dispatched to systems
 * - [MachineTickEvent] - Tick event hierarchy
 * - [github.kasuminova.prototypemachinery.api.machine.MachineInstance] - Entity being processed
 * 
 * ## Example / 示例
 * 
 * ```kotlin
 * object MyComponentSystem : MachineSystem<MyComponent> {
 *     override fun onEvent(machine: MachineInstance, component: MyComponent, event: MachineEvent) {
 *         when (event) {
 *             is MachineTickEvent.Normal -> {
 *                 // Process component during normal tick
 *             }
 *             is CustomMachineEvent -> {
 *                 // Handle custom event
 *             }
 *         }
 *     }
 * }
 * ```
 * 
 * @param C The component type this system processes / 此系统处理的组件类型
 * @see MachineComponent
 * @see MachineEvent
 * @see MachineTickEvent
 */
public interface MachineSystem<C : MachineComponent> : ComponentSystem<MachineInstance, C> {

    /**
     * Pre-tick phase processing.
     * Converted to MachineTickEvent.Pre by default.
     * 
     * 前置 tick 阶段处理。
     * 默认转换为 MachineTickEvent.Pre。
     */
    override fun onPreTick(machine: MachineInstance, component: C) {
        onEvent(machine, component, MachineTickEvent.Pre(machine))
    }

    /**
     * Normal tick phase processing.
     * Converted to MachineTickEvent.Normal by default.
     * 
     * 正常 tick 阶段处理。
     * 默认转换为 MachineTickEvent.Normal。
     */
    override fun onTick(machine: MachineInstance, component: C) {
        onEvent(machine, component, MachineTickEvent.Normal(machine))
    }

    /**
     * Post-tick phase processing.
     * Converted to MachineTickEvent.Post by default.
     * 
     * 后置 tick 阶段处理。
     * 默认转换为 MachineTickEvent.Post。
     */
    override fun onPostTick(machine: MachineInstance, component: C) {
        onEvent(machine, component, MachineTickEvent.Post(machine))
    }

    /**
     * Event handler for all machine events.
     * 
     * Override this method to handle tick events and custom events uniformly.
     * Default implementation does nothing.
     * 
     * 所有机械事件的事件处理器。
     * 
     * 重写此方法以统一处理 tick 事件和自定义事件。
     * 默认实现不执行任何操作。
     * 
     * @param machine The machine instance being processed / 正在处理的机械实例
     * @param component The component being processed / 正在处理的组件
     * @param event The event being dispatched / 正在分发的事件
     */
    public fun onEvent(machine: MachineInstance, component: C, event: MachineEvent) {}

    /**
     * Systems that must run before this system.
     * This system depends on them.
     *
     * 必须在此系统之前运行的系统。
     * 此系统依赖于它们。
     */
    public val runAfter: Set<Class<out MachineSystem<*>>> get() = emptySet()

    /**
     * Systems that must run after this system.
     * They depend on this system.
     *
     * 必须在此系统之后运行的系统。
     * 它们依赖于此系统。
     */
    public val runBefore: Set<Class<out MachineSystem<*>>> get() = emptySet()

}
