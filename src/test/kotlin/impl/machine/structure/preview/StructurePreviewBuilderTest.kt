package github.kasuminova.prototypemachinery.impl.machine.structure.preview

import github.kasuminova.prototypemachinery.api.machine.structure.SliceLikeMachineStructure
import github.kasuminova.prototypemachinery.api.machine.structure.StructureOrientation
import github.kasuminova.prototypemachinery.api.machine.structure.TemplateLikeMachineStructure
import github.kasuminova.prototypemachinery.api.machine.structure.logic.StructureValidator
import github.kasuminova.prototypemachinery.api.machine.structure.match.StructureMatchContext
import github.kasuminova.prototypemachinery.api.machine.structure.pattern.StructurePattern
import github.kasuminova.prototypemachinery.api.machine.structure.pattern.predicate.PreviewableBlockPredicate
import github.kasuminova.prototypemachinery.api.machine.structure.preview.BlockRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.LiteralRequirement
import github.kasuminova.prototypemachinery.impl.machine.structure.pattern.SimpleStructurePattern
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StructurePreviewBuilderTest {

    private class DummyPredicate(private val key: String) : PreviewableBlockPredicate {
        override fun matches(context: StructureMatchContext, pos: BlockPos): Boolean = true
        override fun transform(rotation: (EnumFacing) -> EnumFacing): PreviewableBlockPredicate = this
        override fun toRequirement(): BlockRequirement = LiteralRequirement(key)
    }

    private class DummyTemplate(
        override val id: String,
        override val offset: BlockPos,
        private val blocks: Map<BlockPos, PreviewableBlockPredicate>,
        override val children: List<github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure> = emptyList()
    ) : TemplateLikeMachineStructure {
        override val orientation: StructureOrientation = StructureOrientation(EnumFacing.NORTH, EnumFacing.UP)
        override val validators: List<StructureValidator> = emptyList()
        override val pattern: StructurePattern = SimpleStructurePattern(blocks)
        override fun createData() = object : github.kasuminova.prototypemachinery.api.machine.structure.StructureInstanceData {
            override val orientation: StructureOrientation = this@DummyTemplate.orientation
        }
        override fun transform(rotation: (EnumFacing) -> EnumFacing) = this
        override fun matches(context: StructureMatchContext, origin: BlockPos): Boolean = true
    }

    private class DummySlice(
        override val id: String,
        override val offset: BlockPos,
        override val minCount: Int,
        override val maxCount: Int,
        override val sliceOffset: BlockPos,
        private val blocks: Map<BlockPos, PreviewableBlockPredicate>,
        override val children: List<github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure> = emptyList()
    ) : SliceLikeMachineStructure {
        override val orientation: StructureOrientation = StructureOrientation(EnumFacing.NORTH, EnumFacing.UP)
        override val validators: List<StructureValidator> = emptyList()
        override val pattern: StructurePattern = SimpleStructurePattern(blocks)
        override fun createData() = object : github.kasuminova.prototypemachinery.api.machine.structure.StructureInstanceData {
            override val orientation: StructureOrientation = this@DummySlice.orientation
        }
        override fun transform(rotation: (EnumFacing) -> EnumFacing) = this
        override fun matches(context: StructureMatchContext, origin: BlockPos): Boolean = true
    }

    @Test
    fun `template expands with offset`() {
        val s = DummyTemplate(
            id = "t",
            offset = BlockPos(1, 2, 3),
            blocks = mapOf(
                BlockPos(0, 0, 0) to DummyPredicate("a"),
                BlockPos(1, 0, 0) to DummyPredicate("b"),
            )
        )

        val model = StructurePreviewBuilder.build(s)

        assertEquals(2, model.blocks.size)
        assertTrue(model.blocks.containsKey(BlockPos(1, 2, 3)))
        assertTrue(model.blocks.containsKey(BlockPos(2, 2, 3)))

        val bom = model.bom.associate { it.requirement.stableKey() to it.count }
        assertEquals(1, bom[LiteralRequirement("a").stableKey()])
        assertEquals(1, bom[LiteralRequirement("b").stableKey()])
    }

    @Test
    fun `slice repeats pattern and children anchor at last slice`() {
        val child = DummyTemplate(
            id = "child",
            offset = BlockPos(0, 0, 1),
            blocks = mapOf(BlockPos(0, 0, 0) to DummyPredicate("c"))
        )

        val s = DummySlice(
            id = "s",
            offset = BlockPos(0, 0, 0),
            minCount = 1,
            maxCount = 3,
            sliceOffset = BlockPos(0, 1, 0),
            blocks = mapOf(BlockPos(0, 0, 0) to DummyPredicate("a")),
            children = listOf(child)
        )

        val model = StructurePreviewBuilder.build(
            s,
            StructurePreviewBuilder.Options(sliceCountSelector = { 3 })
        )

        // 3 slices at y=0,1,2.
        assertTrue(model.blocks.containsKey(BlockPos(0, 0, 0)))
        assertTrue(model.blocks.containsKey(BlockPos(0, 1, 0)))
        assertTrue(model.blocks.containsKey(BlockPos(0, 2, 0)))
        // child anchored at last slice (y=2) then offset z+1
        assertTrue(model.blocks.containsKey(BlockPos(0, 2, 1)))

        val bom = model.bom.associate { it.requirement.stableKey() to it.count }
        assertEquals(3, bom[LiteralRequirement("a").stableKey()])
        assertEquals(1, bom[LiteralRequirement("c").stableKey()])
    }
}
