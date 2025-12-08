package github.kasuminova.prototypemachinery.api

import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.api.machine.MachineTypeRegistry
import github.kasuminova.prototypemachinery.impl.machine.MachineTypeRegistryImpl
import net.minecraft.util.ResourceLocation

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
 * ## Usage Contexts / 使用场景
 * 
 * ### From Java / 从 Java 调用
 * ```java
 * import github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI;
 * import net.minecraft.util.ResourceLocation;
 * 
 * // Get a machine type
 * MachineType machine = PrototypeMachineryAPI.getMachineType(
 *     new ResourceLocation("mymod", "my_machine")
 * );
 * 
 * // Or using string parameters
 * MachineType machine2 = PrototypeMachineryAPI.getMachineType("mymod", "my_machine");
 * ```
 * 
 * ### From Kotlin / 从 Kotlin 调用
 * ```kotlin
 * import github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI
 * import net.minecraft.util.ResourceLocation
 * 
 * // Get a machine type
 * val machine = PrototypeMachineryAPI.getMachineType(ResourceLocation("mymod", "my_machine"))
 * 
 * // Check existence
 * if (PrototypeMachineryAPI.hasMachineType(id)) {
 *     // Machine exists
 * }
 * 
 * // Get all machines
 * val allMachines = PrototypeMachineryAPI.getAllMachineTypes()
 * ```
 * 
 * ### From CraftTweaker / 从 CraftTweaker 调用
 * ```zenscript
 * import mods.prototypemachinery.MachineRegistry;
 * 
 * // Register a machine (handled by ZenMachineRegistry internally)
 * MachineRegistry.create("mymod", "my_machine")
 *     .name("My Custom Machine")
 *     .register();
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
 * - [github.kasuminova.prototypemachinery.common.registry.MachineTypeRegisterer] - Queue-based registration
 * - [github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.ZenMachineRegistry] - CraftTweaker bridge
 * 
 * @see MachineTypeRegistry
 * @see MachineType
 */
public object PrototypeMachineryAPI {

    /**
     * The machine type registry.
     * 
     * Direct access to the registry for advanced use cases.
     * Most users should use the convenience methods below instead.
     * 
     * 机械类型注册表。
     * 
     * 为高级用例提供对注册表的直接访问。
     * 大多数用户应改用下面的便捷方法。
     */
    @JvmStatic
    public val machineTypeRegistry: MachineTypeRegistry = MachineTypeRegistryImpl

    /**
     * Get a machine type by its ID.
     * 
     * 通过 ID 获取机械类型。
     * 
     * @param id The resource location ID of the machine type / 机械类型的资源位置 ID
     * @return The machine type, or null if not found / 机械类型，如果未找到则为 null
     */
    @JvmStatic
    public fun getMachineType(id: ResourceLocation): MachineType? {
        return machineTypeRegistry.get(id)
    }

    /**
     * Get a machine type by its ID components.
     * 
     * Convenience overload for Java/Kotlin code to avoid ResourceLocation construction.
     * 
     * 通过 ID 组件获取机械类型。
     * 
     * 为 Java/Kotlin 代码提供的便捷重载，避免构造 ResourceLocation。
     * 
     * @param modId The mod ID / 模组 ID
     * @param path The machine path/name / 机械路径/名称
     * @return The machine type, or null if not found / 机械类型，如果未找到则为 null
     */
    @JvmStatic
    public fun getMachineType(modId: String, path: String): MachineType? {
        return getMachineType(ResourceLocation(modId, path))
    }

    /**
     * Check if a machine type exists.
     * 
     * 检查机械类型是否存在。
     * 
     * @param id The resource location ID to check / 要检查的资源位置 ID
     * @return true if the machine type is registered / 如果机械类型已注册则返回 true
     */
    @JvmStatic
    public fun hasMachineType(id: ResourceLocation): Boolean {
        return machineTypeRegistry.contains(id)
    }

    /**
     * Get all registered machine types.
     * 
     * Returns an immutable snapshot of all registered machine types.
     * Useful for iteration, debugging, and mod integration.
     * 
     * 获取所有已注册的机械类型。
     * 
     * 返回所有已注册机械类型的不可变快照。
     * 对于迭代、调试和模组集成很有用。
     * 
     * @return An immutable collection of all registered machine types / 所有已注册机械类型的不可变集合
     */
    @JvmStatic
    public fun getAllMachineTypes(): Collection<MachineType> {
        return machineTypeRegistry.all()
    }

    /**
     * Get all registered machine type IDs.
     * 
     * Returns an immutable snapshot of all registered IDs.
     * Useful for autocomplete, validation, and debugging.
     * 
     * 获取所有已注册的机械类型 ID。
     * 
     * 返回所有已注册 ID 的不可变快照。
     * 对于自动完成、验证和调试很有用。
     * 
     * @return An immutable set of all registered machine type IDs / 所有已注册机械类型 ID 的不可变集合
     */
    @JvmStatic
    public fun getAllMachineTypeIds(): Set<ResourceLocation> {
        return machineTypeRegistry.allIds()
    }

}
