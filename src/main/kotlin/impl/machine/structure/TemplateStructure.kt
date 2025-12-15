package github.kasuminova.prototypemachinery.impl.machine.structure

import github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure
import github.kasuminova.prototypemachinery.api.machine.structure.StructureInstanceData
import github.kasuminova.prototypemachinery.api.machine.structure.StructureOrientation
import github.kasuminova.prototypemachinery.api.machine.structure.logic.StructureValidator
import github.kasuminova.prototypemachinery.api.machine.structure.match.StructureMatchContext
import github.kasuminova.prototypemachinery.api.machine.structure.pattern.StructurePattern
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos

public class TemplateStructure(
    override val id: String,
    override val orientation: StructureOrientation,
    override val offset: BlockPos,
    override val pattern: StructurePattern,
    override val validators: List<StructureValidator> = emptyList(),
    override val children: List<MachineStructure> = emptyList()
) : github.kasuminova.prototypemachinery.api.machine.structure.TemplateLikeMachineStructure {
    override fun createData(): StructureInstanceData {
        return object : StructureInstanceData {
            override val orientation: StructureOrientation = this@TemplateStructure.orientation
        }
    }

    override fun transform(rotation: (EnumFacing) -> EnumFacing): MachineStructure {
        return TemplateStructure(
            id,
            orientation.transform(rotation),
            StructureUtils.rotatePos(offset, rotation),
            pattern.transform(rotation),
            validators,
            children.map { it.transform(rotation) }
        )
    }

    override fun matches(context: StructureMatchContext, origin: BlockPos): Boolean {
        context.enterStructure(this)
        var matched = false
        try {
            // Apply offset to get the actual starting position
            // 应用偏移以获取实际的起始位置
            val offsetOrigin = origin.add(offset)

            // Fast-fail: pattern area must be loaded before checking predicates.
            // 快速失败：在检查 predicate 之前，pattern 覆盖范围必须已加载。
            val world = context.machine.blockEntity.world ?: return false
            if (!pattern.isAreaLoaded(world, offsetOrigin)) {
                return false
            }

            // Check if pattern matches
            // 检查模式是否匹配
            for ((relativePos, predicate) in pattern.blocks) {
                val actualPos = offsetOrigin.add(relativePos)

                // Controller position is reserved and validated elsewhere.
                // 控制器坐标由外部逻辑验证；pattern 不应占用控制器坐标。
                if (actualPos == origin) {
                    continue
                }

                if (!predicate.matches(context, actualPos)) {
                    return false
                }
            }

            // Run validators
            // 运行验证器
            for (validator in validators) {
                if (!validator.validate(context, offsetOrigin)) {
                    return false
                }
            }

            // Check children structures
            // 检查子结构
            for (child in children) {
                if (!child.matches(context, offsetOrigin)) {
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