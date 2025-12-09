package github.kasuminova.prototypemachinery.api.machine.structure

import net.minecraft.util.EnumFacing

/**
 * # StructureRegistry - Machine Structure Registration System
 * # StructureRegistry - 机器结构注册系统
 *
 * Central registry for all machine structure definitions. Supports registration,
 * lookup by ID, and transformation/caching for different orientations.
 *
 * 所有机器结构定义的中央注册表。支持注册、按 ID 查找以及不同朝向的变换/缓存。
 *
 * ## Usage / 用法
 * ```kotlin
 * // Register a structure
 * structureRegistry.register(myStructure)
 *
 * // Get base structure
 * val structure = structureRegistry.get("my_structure")
 *
 * // Get transformed structure for specific orientation
 * val transformed = structureRegistry.get(
 *     "my_structure",
 *     StructureOrientation.NORTH_UP,
 *     EnumFacing.EAST
 * )
 * ```
 *
 * @see MachineStructure
 * @see StructureOrientation
 */
public interface StructureRegistry {

    /**
     * Register a machine structure.
     * 注册一个机器结构。
     *
     * @param structure The structure to register / 要注册的结构
     * @throws IllegalArgumentException if structure with same ID already exists / 如果相同 ID 的结构已存在
     */
    public fun register(structure: MachineStructure)

    /**
     * Get a structure by its ID (base orientation).
     * 通过 ID 获取结构（基础朝向）。
     *
     * @param id The structure ID / 结构 ID
     * @return The structure, or null if not found / 结构，如果未找到则为 null
     */
    public fun get(id: String): MachineStructure?

    /**
     * Get a structure transformed to specific orientation and facing.
     * 获取变换到特定朝向和面向的结构。
     *
     * Uses internal caching for performance.
     * 使用内部缓存以提升性能。
     *
     * @param id The structure ID / 结构 ID
     * @param orientation Target orientation / 目标朝向
     * @param horizontalFacing Horizontal facing direction / 水平面向方向
     * @return The transformed structure, or null if base not found / 变换后的结构，如果基础结构未找到则为 null
     */
    public fun get(id: String, orientation: StructureOrientation, horizontalFacing: EnumFacing): MachineStructure?

    /**
     * Get all registered structures.
     * 获取所有已注册的结构。
     *
     * @return Immutable collection of all structures / 所有结构的不可变集合
     */
    public fun getAll(): Collection<MachineStructure>

    /**
     * Check if a structure is registered.
     * 检查结构是否已注册。
     *
     * @param id The structure ID / 结构 ID
     * @return true if registered / 如果已注册则返回 true
     */
    public fun contains(id: String): Boolean

}