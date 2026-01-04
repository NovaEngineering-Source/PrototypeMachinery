package github.kasuminova.prototypemachinery.impl.machine.structure.pattern.predicate

import github.kasuminova.prototypemachinery.api.machine.structure.match.StructureMatchContext
import github.kasuminova.prototypemachinery.api.machine.structure.pattern.predicate.BlockPredicate
import github.kasuminova.prototypemachinery.api.machine.structure.pattern.predicate.PreviewRequirementProvider
import github.kasuminova.prototypemachinery.api.machine.structure.preview.BlockRequirement
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos

/**
 * Wraps an inner predicate and provides a preview requirement descriptor.
 *
 * 通过 wrapper 挂载“显示用 requirement”，而不改变匹配逻辑。
 */
public class DisplayOverridePredicate(
    public val inner: BlockPredicate,
    private val requirement: BlockRequirement,
) : PreviewRequirementProvider {

    override fun matches(context: StructureMatchContext, pos: BlockPos): Boolean = inner.matches(context, pos)

    override fun transform(rotation: (EnumFacing) -> EnumFacing): BlockPredicate {
        return DisplayOverridePredicate(inner.transform(rotation), requirement)
    }

    override fun previewRequirement(): BlockRequirement = requirement
}
