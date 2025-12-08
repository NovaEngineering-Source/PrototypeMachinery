package github.kasuminova.prototypemachinery.api.machine

import net.minecraft.util.ResourceLocation

/**
 * # MachineTypeRegistry - Machine Type Registration System
 * # MachineTypeRegistry - 机械类型注册系统
 * 
 * Central registry for all machine types in the game. Provides thread-safe registration
 * and lookup of machine type definitions.
 * 
 * 游戏中所有机械类型的中央注册表。提供线程安全的机械类型定义注册和查找。
 * 
 * ## Registration Timing / 注册时机
 * 
 * Machine types should be registered during FMLPreInitializationEvent:
 * - Direct API: Use [github.kasuminova.prototypemachinery.common.registry.MachineTypeRegisterer]
 * - CraftTweaker: Scripts are processed automatically during PreInit
 * 
 * 机械类型应在 FMLPreInitializationEvent 期间注册:
 * - 直接 API: 使用 [github.kasuminova.prototypemachinery.common.registry.MachineTypeRegisterer]
 * - CraftTweaker: 脚本在 PreInit 期间自动处理
 * 
 * ## Thread Safety / 线程安全
 * 
 * All methods are thread-safe and can be called from any thread.
 * Internal implementation uses ConcurrentHashMap for safe concurrent access.
 * 
 * 所有方法都是线程安全的，可以从任何线程调用。
 * 内部实现使用 ConcurrentHashMap 以实现安全的并发访问。
 * 
 * ## Related Classes / 相关类
 * 
 * - [MachineType] - Machine type definition
 * - [github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI] - Main API access point
 * - [github.kasuminova.prototypemachinery.common.registry.MachineTypeRegisterer] - Queue-based registerer
 * - [github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.ZenMachineRegistry] - CraftTweaker registration
 * 
 * ## Example / 示例
 * 
 * ```kotlin
 * // Register via API
 * PrototypeMachineryAPI.machineTypeRegistry.register(myMachineType)
 * 
 * // Lookup
 * val machine = PrototypeMachineryAPI.machineTypeRegistry.get(
 *     ResourceLocation("mymod", "my_machine")
 * )
 * 
 * // Check existence
 * if (registry.contains(id)) {
 *     // Machine type exists
 * }
 * ```
 * 
 * @see MachineType
 * @see github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI
 */
public interface MachineTypeRegistry {

    /**
     * Register a machine type.
     * 
     * Registration is idempotent - registering the same type multiple times has no effect.
     * If a different machine type with the same ID is registered, an exception is thrown.
     * 
     * 注册一个机械类型。
     * 
     * 注册是幂等的 - 多次注册相同类型不会有影响。
     * 如果注册了具有相同 ID 的不同机械类型，则会抛出异常。
     * 
     * @param machineType The machine type to register / 要注册的机械类型
     * @throws IllegalArgumentException if a different machine type with the same ID already exists / 如果已存在具有相同 ID 的不同机械类型
     */
    public fun register(machineType: MachineType)

    /**
     * Get a machine type by its ID.
     * 
     * 通过 ID 获取机械类型。
     * 
     * @param id The resource location ID of the machine type / 机械类型的资源位置 ID
     * @return The machine type, or null if not found / 机械类型，如果未找到则为 null
     */
    public fun get(id: ResourceLocation): MachineType?

    /**
     * Check if a machine type is registered.
     * 
     * 检查机械类型是否已注册。
     * 
     * @param id The resource location ID to check / 要检查的资源位置 ID
     * @return true if the machine type is registered / 如果机械类型已注册则返回 true
     */
    public fun contains(id: ResourceLocation): Boolean

    /**
     * Get all registered machine types.
     * 
     * Returns an immutable snapshot of registered types.
     * Modifications to the collection will not affect the registry.
     * 
     * 获取所有已注册的机械类型。
     * 
     * 返回已注册类型的不可变快照。
     * 对集合的修改不会影响注册表。
     * 
     * @return An immutable collection of all registered machine types / 所有已注册机械类型的不可变集合
     */
    public fun all(): Collection<MachineType>

    /**
     * Get all registered machine type IDs.
     * 
     * Returns an immutable snapshot of registered IDs.
     * Useful for iteration and debugging.
     * 
     * 获取所有已注册的机械类型 ID。
     * 
     * 返回已注册 ID 的不可变快照。
     * 对于迭代和调试很有用。
     * 
     * @return An immutable set of all registered machine type IDs / 所有已注册机械类型 ID 的不可变集合
     */
    public fun allIds(): Set<ResourceLocation>

}
