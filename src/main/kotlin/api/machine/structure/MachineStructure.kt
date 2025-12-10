package github.kasuminova.prototypemachinery.api.machine.structure

import github.kasuminova.prototypemachinery.api.machine.structure.logic.StructureValidator
import github.kasuminova.prototypemachinery.api.machine.structure.match.StructureMatchContext
import github.kasuminova.prototypemachinery.api.machine.structure.pattern.StructurePattern
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos

/**
 * # MachineStructure - Multi-block Structure Definition
 * # MachineStructure - 多方块结构定义
 *
 * Describes the layout, orientation support, and validation logic for a machine's structure.
 * Can be hierarchical (children) and supports 24-way rotation via [StructureOrientation].
 *
 * 描述机械结构的布局、朝向支持和验证逻辑。
 * 可分层（children），并通过 [StructureOrientation] 支持 24 向旋转。
 *
 * ## Fields / 字段
 * - **orientation**: Base orientation (front/top) of the structure
 * - **offset**: Origin offset relative to controller block
 * - **validators**: Additional validation steps (capabilities, block states)
 * - **children**: Nested structures for complex layouts
 *
 * ## Related Classes / 相关类
 * - [StructureOrientation]
 * - [StructureValidator]
 * - [StructurePattern]
 * - [StructureInstanceData]
 */
public interface MachineStructure {

    /** Unique identifier for this structure / 此结构的唯一 ID */
    public val id: String

    /** Base orientation (front, top) / 基础朝向（正面、顶部） */
    public val orientation: StructureOrientation

    /** Offset from controller/origin / 相对于控制器/原点的偏移 */
    public val offset: BlockPos

    /** Validators run after pattern matching / 模式匹配后的验证器列表 */
    public val validators: List<StructureValidator>

    /** Child structures for hierarchical definitions / 分层定义的子结构 */
    public val children: List<MachineStructure>

    /** Create runtime data container / 创建运行时数据容器 */
    public fun createData(): StructureInstanceData

    /** Transform structure by rotating directions / 通过旋转方向来变换结构 */
    public fun transform(rotation: (EnumFacing) -> EnumFacing): MachineStructure

    /**
     * Check if this structure matches at the given position in the world.
     * 检查此结构是否在世界中的给定位置匹配。
     *
     * @param context The structure matching context / 结构匹配上下文
     * @param origin The origin position (controller position) / 原点位置（控制器位置）
     * @return true if the structure matches, false otherwise / 如果结构匹配则返回 true，否则返回 false
     */
    public fun matches(context: StructureMatchContext, origin: BlockPos): Boolean

}