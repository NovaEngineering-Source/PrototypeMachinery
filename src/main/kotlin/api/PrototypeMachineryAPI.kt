package github.kasuminova.prototypemachinery.api

import github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI.machineTypeRegistry
import github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI.recipeIndexRegistry
import github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI.recipeManager
import github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI.recipeRequirementRegistry
import github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI.selectiveModifierRegistry
import github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI.structureRegistry
import github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI.taskScheduler
import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.api.machine.MachineTypeRegistry
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeRegistry
import github.kasuminova.prototypemachinery.api.machine.structure.StructureRegistry
import github.kasuminova.prototypemachinery.api.machine.structure.logic.StructureValidatorRegistry
import github.kasuminova.prototypemachinery.api.recipe.RecipeManager
import github.kasuminova.prototypemachinery.api.recipe.index.IRecipeIndexRegistry
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementRegistry
import github.kasuminova.prototypemachinery.api.recipe.scanning.RecipeParallelismConstraintRegistry
import github.kasuminova.prototypemachinery.api.recipe.selective.SelectiveModifierRegistry
import github.kasuminova.prototypemachinery.api.scheduler.TaskScheduler
import github.kasuminova.prototypemachinery.api.ui.action.UIActionRegistry
import github.kasuminova.prototypemachinery.api.ui.binding.UIBindingRegistry
import github.kasuminova.prototypemachinery.api.ui.registry.MachineUIRegistry
import github.kasuminova.prototypemachinery.common.registry.MachineTypeRegisterer
import github.kasuminova.prototypemachinery.impl.machine.MachineTypeRegistryImpl
import github.kasuminova.prototypemachinery.impl.machine.structure.StructureRegistryImpl
import github.kasuminova.prototypemachinery.impl.recipe.RecipeManagerImpl
import github.kasuminova.prototypemachinery.impl.recipe.index.RecipeIndexRegistry
import github.kasuminova.prototypemachinery.impl.scheduler.TaskSchedulerImpl
import github.kasuminova.prototypemachinery.impl.ui.action.UIActionRegistryImpl
import github.kasuminova.prototypemachinery.impl.ui.binding.UIBindingRegistryImpl
import github.kasuminova.prototypemachinery.impl.ui.registry.MachineUIRegistryImpl
import github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.ZenMachineRegistry

/**
 * # PrototypeMachineryAPI - Main API Entry Point
 * # PrototypeMachineryAPI - 主 API 入口点
 * 
 * Central access point for all PrototypeMachinery public APIs. This singleton provides
 * convenient access to registries and utility methods.
 * 
 * PrototypeMachinery 所有公共 API 的中央访问点。此单例提供对注册表和实用方法的便捷访问。
 * 
 * ## Design Philosophy / 设计理念
 * 
 * - **Facade Pattern**: Provides simplified interface to complex subsystems
 *   **外观模式**: 为复杂的子系统提供简化的接口
 * 
 * - **Static Access**: All methods are @JvmStatic for easy Java/Kotlin interop
 *   **静态访问**: 所有方法都是 @JvmStatic 以便 Java/Kotlin 互操作
 * 
 * - **Immutability**: Returns immutable collections to prevent external modification
 *   **不可变性**: 返回不可变集合以防止外部修改
 * 
 * ## Key Components / 核心组件
 *
 * - **[machineTypeRegistry]**:
 *   Manages machine definitions and lookups.
 *   管理机器定义和查找。
 *
 * - **[structureRegistry]**:
 *   Handles multiblock structure definitions and validation.
 *   处理多方块结构定义和验证。
 *
 * - **[recipeManager]**:
 *   Central registry for all machine recipes.
 *   所有机器配方的中央注册表。
 *
 * - **[recipeIndexRegistry]**:
 *   Optimizes recipe lookups using pre-calculated indices.
 *   使用预计算索引优化配方查找。
 *
 * - **[recipeRequirementRegistry]**:
 *   Registers custom recipe requirement types (e.g., Item, Fluid, Energy).
 *   注册自定义配方需求类型（例如：物品、流体、能量）。
 *
 * - **[selectiveModifierRegistry]**:
 *   Manages dynamic modifiers for recipe logic.
 *   管理配方逻辑的动态修改器。
 *
 * - **[taskScheduler]**:
 *   Provides scheduling for synchronous and asynchronous tasks.
 *   提供同步和异步任务的调度。
 *
 * ## Usage Example / 使用示例
 *
 * ```kotlin
 * // Accessing registries / 访问注册表
 * val machineType = PrototypeMachineryAPI.machineTypeRegistry.get(id)
 * val structure = PrototypeMachineryAPI.structureRegistry.get(id)
 * ```
 * 
 * ## Thread Safety / 线程安全
 * 
 * All methods delegate to thread-safe registry implementations.
 * Safe to call from any thread.
 * 
 * 所有方法都委托给线程安全的注册表实现。
 * 可以从任何线程安全调用。
 * 
 * ## Related Classes / 相关类
 * 
 * - [MachineTypeRegistry] - Machine type registration and lookup
 * - [MachineType] - Machine type definitions
 * - [StructureRegistry] - Structure registration
 * - [RecipeManager] - Recipe management
 * - [IRecipeIndexRegistry] - Recipe indexing
 * - [MachineTypeRegisterer] - Queue-based registration
 * - [ZenMachineRegistry] - CraftTweaker bridge
 * 
 * @see MachineTypeRegistry
 * @see StructureRegistry
 * @see RecipeManager
 */
public object PrototypeMachineryAPI {

    /**
     * The machine type registry.
     * 
     * Primary entry point for machine type operations.
     * 
     * 机械类型注册表。
     * 
     * 机械类型操作的主要入口点。
     */
    @get:JvmStatic
    public val machineTypeRegistry: MachineTypeRegistry = MachineTypeRegistryImpl

    /**
     * The structure registry.
     *
     * Registry for machine structures.
     *
     * 结构注册表。
     *
     * 机器结构的注册表。
     */
    @get:JvmStatic
    public val structureRegistry: StructureRegistry = StructureRegistryImpl

    /**
     * Structure validator registry.
     *
     * Used by JSON-loaded structures (`StructureData.validators`).
     */
    @get:JvmStatic
    public val structureValidatorRegistry: StructureValidatorRegistry = StructureValidatorRegistry

    /**
     * Global machine attribute registry.
     *
     * This is the authoritative registry for attribute type resolution (including NBT).
     */
    @get:JvmStatic
    public val machineAttributeRegistry: MachineAttributeRegistry = MachineAttributeRegistry

    /**
     * The recipe manager.
     *
     * Manager for machine recipes.
     *
     * 配方管理器。
     *
     * 机器配方的管理器。
     */
    @get:JvmStatic
    public val recipeManager: RecipeManager = RecipeManagerImpl

    /**
     * The recipe index registry.
     *
     * Registry for recipe indices.
     *
     * 配方索引注册表。
     *
     * 配方索引的注册表。
     */
    @get:JvmStatic
    public val recipeIndexRegistry: IRecipeIndexRegistry = RecipeIndexRegistry

    /**
     * The recipe requirement registry.
     *
     * Registry for recipe requirements.
     *
     * 配方需求注册表。
     *
     * 配方需求的注册表。
     */
    @get:JvmStatic
    public val recipeRequirementRegistry: RecipeRequirementRegistry = RecipeRequirementRegistry

    /**
     * Recipe scan-time parallelism constraint registry.
     *
     * This is used by the scanning system to determine the maximum safe effective parallelism.
     */
    @get:JvmStatic
    public val recipeParallelismConstraintRegistry: RecipeParallelismConstraintRegistry = RecipeParallelismConstraintRegistry

    /**
     * The selective modifier registry.
     *
     * Registry for selective modifiers.
     *
     * 选择性修改器注册表。
     *
     * 选择性修改器的注册表。
     */
    @get:JvmStatic
    public val selectiveModifierRegistry: SelectiveModifierRegistry = SelectiveModifierRegistry

    /**
     * The task scheduler.
     *
     * Scheduler for tasks.
     *
     * 任务调度器。
     *
     * 任务的调度器。
     */
    @get:JvmStatic
    public val taskScheduler: TaskScheduler = TaskSchedulerImpl

    /**
     * The machine UI registry.
     *
     * This registry allows scripts/mods to register or override UIs independently
     * from machine type construction.
     */
    @get:JvmStatic
    public val machineUIRegistry: MachineUIRegistry = MachineUIRegistryImpl

    /**
     * UI binding registry.
     *
     * 用于把 UI 中的字符串 key 解析为服务端 getter / setter（可写）并接入 ModularUI 同步。
     */
    @get:JvmStatic
    public val uiBindingRegistry: UIBindingRegistry = UIBindingRegistryImpl

    /**
     * UI action registry.
     *
     * 用于处理 client -> server 的 UI 行为（按钮点击、快捷操作等）。
     */
    @get:JvmStatic
    public val uiActionRegistry: UIActionRegistry = UIActionRegistryImpl

}
