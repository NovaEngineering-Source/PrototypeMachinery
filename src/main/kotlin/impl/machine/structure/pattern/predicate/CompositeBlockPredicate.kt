package github.kasuminova.prototypemachinery.impl.machine.structure.pattern.predicate

import github.kasuminova.prototypemachinery.api.machine.structure.match.StructureMatchContext
import github.kasuminova.prototypemachinery.api.machine.structure.pattern.predicate.BlockPredicate
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos

/**
 * AND-composition of multiple predicates.
 *
 * 用于把多个 predicate 组合为“全部满足才匹配”。
 */
public class CompositeBlockPredicate(
    public val predicates: List<BlockPredicate>
) : BlockPredicate {

    init {
        require(predicates.isNotEmpty()) { "CompositeBlockPredicate.predicates must not be empty" }
    }

    override fun matches(context: StructureMatchContext, pos: BlockPos): Boolean {
        for (p in predicates) {
            if (!p.matches(context, pos)) return false
        }
        return true
    }

    override fun transform(rotation: (EnumFacing) -> EnumFacing): BlockPredicate {
        return CompositeBlockPredicate(predicates.map { it.transform(rotation) })
    }
}
