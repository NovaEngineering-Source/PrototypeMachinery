package github.kasuminova.prototypemachinery.impl.machine.structure

import github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure
import github.kasuminova.prototypemachinery.api.machine.structure.StructureInstanceData
import github.kasuminova.prototypemachinery.api.machine.structure.StructureOrientation
import github.kasuminova.prototypemachinery.api.machine.structure.logic.StructureValidator
import github.kasuminova.prototypemachinery.api.machine.structure.match.StructureMatchContext
import github.kasuminova.prototypemachinery.api.machine.structure.pattern.StructurePattern
import github.kasuminova.prototypemachinery.common.util.times
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos

public class SliceStructure(
    override val id: String,
    override val orientation: StructureOrientation,
    override val offset: BlockPos,
    override val hideWorldBlocks: Boolean = false,
    override val pattern: StructurePattern,
    override val minCount: Int,
    override val maxCount: Int,
    override val sliceOffset: BlockPos = BlockPos(0, 1, 0),
    override val validators: List<StructureValidator> = emptyList(),
    override val children: List<MachineStructure> = emptyList()
) : github.kasuminova.prototypemachinery.api.machine.structure.SliceLikeMachineStructure {

    override fun createData(): StructureInstanceData = SliceStructureInstanceData(this.orientation)

    override fun transform(rotation: (EnumFacing) -> EnumFacing): MachineStructure = SliceStructure(
        id,
        orientation.transform(rotation),
        StructureUtils.rotatePos(offset, rotation),
        hideWorldBlocks,
        pattern.transform(rotation),
        minCount,
        maxCount,
        StructureUtils.rotatePos(sliceOffset, rotation),
        validators,
        children.map { it.transform(rotation) }
    )

    override fun matches(context: StructureMatchContext, origin: BlockPos): Boolean {
        context.enterStructure(this)
        var matched = false
        try {
            // Apply offset to get the actual starting position
            // 应用偏移以获取实际的起始位置
            val offsetOrigin = origin.add(offset)

            // World must be available for chunk-loaded checks.
            val world = context.machine.blockEntity.world ?: return false

            // For slice structures, we need to count how many consecutive matches we can find
            // 对于切片结构，我们需要计算可以找到多少连续匹配
            var matchCount = 0
            var currentPos = offsetOrigin

            // Try to match up to maxCount slices
            // 尝试匹配最多 maxCount 个切片
            for (i in 0 until maxCount) {
                var sliceMatches = true

                // Fast-fail: pattern area must be loaded before checking predicates.
                // 快速失败：在检查 predicate 之前，pattern 覆盖范围必须已加载。
                if (!pattern.isAreaLoaded(world, currentPos)) {
                    return false
                }

                // Check if pattern matches at current position
                // 检查模式是否在当前位置匹配
                for ((relativePos, predicate) in pattern.blocks) {
                    val actualPos = currentPos.add(relativePos)

                    // Controller position is reserved and validated elsewhere.
                    // 控制器坐标由外部逻辑验证；pattern 不应占用控制器坐标。
                    if (actualPos == origin) {
                        continue
                    }

                    if (!predicate.matches(context, actualPos)) {
                        sliceMatches = false
                        break
                    }
                }

                if (!sliceMatches) {
                    break
                }

                matchCount++
                // Move to next slice position using sliceOffset
                // 使用 sliceOffset 移动到下一个切片位置
                currentPos = currentPos.add(sliceOffset)
            }

            // Check if match count is within valid range
            // 检查匹配数量是否在有效范围内
            if (matchCount !in minCount..maxCount) {
                return false
            }

            // Update the matched count in the instance data
            // 更新实例数据中的匹配计数
            val data = context.currentMatchingData as SliceStructureInstanceData
            data.matchedCount = matchCount

            // Run validators on the base position
            // 在基础位置运行验证器
            for (validator in validators) {
                if (!validator.validate(context, offsetOrigin)) {
                    return false
                }
            }

            // Check children structures with accumulated slice offset
            // 检查子结构，应用累积的切片偏移
            val accumulatedOffset = sliceOffset * (matchCount - 1)
            val childOrigin = offsetOrigin.add(accumulatedOffset)
            for (child in children) {
                if (!child.matches(context, childOrigin)) {
                    return false
                }
            }

            matched = true
            return true
        } finally {
            context.exitStructure(matched)
        }
    }

}

/**
 * Data container for SliceStructure instances.
 * SliceStructure 实例的数据容器。
 */
public class SliceStructureInstanceData(
    override val orientation: StructureOrientation
) : StructureInstanceData {
    /**
     * The number of matched slices.
     * 匹配的切片数量。
     */
    public var matchedCount: Int = 0
}
