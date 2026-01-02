package github.kasuminova.prototypemachinery.api.machine.component.type

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType
import github.kasuminova.prototypemachinery.api.machine.component.system.MachineSystem
import github.kasuminova.prototypemachinery.impl.machine.component.StructureRenderDataComponentImpl
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos

/**
 * Server-authoritative structure render data, synced to client.
 *
 * Used by client renderers/hide systems to avoid doing structure matching on the render thread.
 *
 * This component is intentionally lightweight:
 * - structure bounds (min/max)
 * - slice matchedCount per structure id
 *
 * Sync strategy:
 * - FULL: send current snapshot (for chunk load)
 * - INCREMENTAL: when dirty, send a snapshot with the same schema as FULL (simplified)
 */
public object StructureRenderDataComponentType : MachineComponentType<StructureRenderDataComponent> {

    override val id: ResourceLocation = ResourceLocation("prototypemachinery", "structure_render_data")

    override val system: MachineSystem<StructureRenderDataComponent>? = null

    override fun createComponent(machine: MachineInstance): StructureRenderDataComponent {
        return StructureRenderDataComponentImpl(machine, this)
    }
}

public interface StructureRenderDataComponent : MachineComponent, MachineComponent.Synchronizable {

    /** Monotonic version bumped when data changes. */
    public val dataVersion: Int

    /** World-space structure bounds (block coords). */
    public val structureMin: BlockPos?

    /** World-space structure bounds (block coords). */
    public val structureMax: BlockPos?

    /** SliceStructure matchedCount per structure id. */
    public val sliceCounts: Map<String, Int>

    /**
     * Server-side: update data from a structure refresh result.
     *
     * @return true if anything changed (and should be synced)
     */
    public fun updateFromServer(
        formed: Boolean,
        min: BlockPos?,
        max: BlockPos?,
        sliceCounts: Map<String, Int>,
    ): Boolean
}
