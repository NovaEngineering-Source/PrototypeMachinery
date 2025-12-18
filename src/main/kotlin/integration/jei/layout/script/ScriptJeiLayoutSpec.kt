package github.kasuminova.prototypemachinery.integration.jei.layout.script

import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiRequirementRole
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotRole
import net.minecraft.util.ResourceLocation

/**
 * Immutable, data-driven JEI layout spec.
 *
 * This spec is intentionally JEI-API agnostic. It can be produced by:
 * - ZenScript builders (CraftTweaker)
 * - config-driven layouts
 * - other integrations
 */
public data class ScriptJeiLayoutSpec(
    public val width: Int,
    public val height: Int,
    public val rules: List<ScriptJeiLayoutRule>,
    public val autoPlaceRemaining: AutoPlaceRemainingSpec? = null,
)

/** Rules are executed sequentially. */
public sealed interface ScriptJeiLayoutRule {

    public val condition: ScriptJeiLayoutCondition

    public fun apply(input: ScriptJeiLayoutInput, out: ScriptJeiLayoutOutput)
}

public data class ScriptJeiLayoutInput(
    public val requirementNodes: List<ScriptJeiLayoutNodeView>,
)

public data class ScriptJeiLayoutNodeView(
    public val nodeId: String,
    public val typeId: ResourceLocation,
    public val role: PMJeiRequirementRole,
    public val index: Int,
)

public data class ScriptJeiLayoutOutput(
    public val placedNodeIds: MutableSet<String>,
    public val placeNode: (nodeId: String, x: Int, y: Int, variantId: ResourceLocation?) -> Unit,
    public val placeFixedSlot: (providerId: ResourceLocation, role: JeiSlotRole, x: Int, y: Int, width: Int, height: Int) -> Unit,
    public val addDecorator: (decoratorId: ResourceLocation, x: Int, y: Int, data: Map<String, Any>) -> Unit,
)

public sealed interface ScriptJeiLayoutCondition {
    public fun test(input: ScriptJeiLayoutInput): Boolean
}

public object AlwaysCondition : ScriptJeiLayoutCondition {
    override fun test(input: ScriptJeiLayoutInput): Boolean = true
}

public data class CountAtLeastCondition(
    public val typeId: ResourceLocation? = null,
    public val role: PMJeiRequirementRole? = null,
    /** If true, role INPUT/OUTPUT will also match their *_PER_TICK counterparts. */
    public val mergePerTick: Boolean = false,
    public val min: Int,
) : ScriptJeiLayoutCondition {
    override fun test(input: ScriptJeiLayoutInput): Boolean {
        val count = input.requirementNodes.asSequence()
            .filter { n -> typeId == null || n.typeId == typeId }
            .filter { n -> role == null || roleMatches(n.role, role, mergePerTick) }
            .count()
        return count >= min
    }
}

public data class AutoPlaceRemainingSpec(
    public val startX: Int = 0,
    public val startY: Int = 0,
    public val gapX: Int = 2,
    public val gapY: Int = 2,
    public val pad: Int = 4,
)

public data class PlaceByNodeIdRule(
    override val condition: ScriptJeiLayoutCondition = AlwaysCondition,
    public val nodeId: String,
    public val x: Int,
    public val y: Int,
    public val variantId: ResourceLocation? = null,
) : ScriptJeiLayoutRule {
    override fun apply(input: ScriptJeiLayoutInput, out: ScriptJeiLayoutOutput) {
        out.placedNodeIds += nodeId
        out.placeNode(nodeId, x, y, variantId)
    }
}

public data class PlaceFirstRule(
    override val condition: ScriptJeiLayoutCondition = AlwaysCondition,
    public val typeId: ResourceLocation? = null,
    public val role: PMJeiRequirementRole? = null,
    /** If true, role INPUT/OUTPUT will also match their *_PER_TICK counterparts. */
    public val mergePerTick: Boolean = false,
    public val x: Int,
    public val y: Int,
    public val variantId: ResourceLocation? = null,
    public val skipPlaced: Boolean = true,
) : ScriptJeiLayoutRule {
    override fun apply(input: ScriptJeiLayoutInput, out: ScriptJeiLayoutOutput) {
        val node = input.requirementNodes
            .asSequence()
            .filter { n -> typeId == null || n.typeId == typeId }
            .filter { n -> role == null || roleMatches(n.role, role, mergePerTick) }
            .firstOrNull { n -> !skipPlaced || n.nodeId !in out.placedNodeIds }
            ?: return

        out.placedNodeIds += node.nodeId
        out.placeNode(node.nodeId, x, y, variantId)
    }
}

public data class PlaceAllLinearRule(
    override val condition: ScriptJeiLayoutCondition = AlwaysCondition,
    public val typeId: ResourceLocation? = null,
    public val role: PMJeiRequirementRole? = null,
    /** If true, role INPUT/OUTPUT will also match their *_PER_TICK counterparts. */
    public val mergePerTick: Boolean = false,
    public val startX: Int,
    public val startY: Int,
    public val stepX: Int,
    public val stepY: Int,
    public val maxCount: Int = Int.MAX_VALUE,
    public val variantId: ResourceLocation? = null,
    public val skipPlaced: Boolean = true,
) : ScriptJeiLayoutRule {
    override fun apply(input: ScriptJeiLayoutInput, out: ScriptJeiLayoutOutput) {
        val nodes = input.requirementNodes.asSequence()
            .filter { n -> typeId == null || n.typeId == typeId }
            .filter { n -> role == null || roleMatches(n.role, role, mergePerTick) }
            .filter { n -> !skipPlaced || n.nodeId !in out.placedNodeIds }
            .take(maxCount)
            .toList()

        var x = startX
        var y = startY

        for (n in nodes) {
            out.placedNodeIds += n.nodeId
            out.placeNode(n.nodeId, x, y, variantId)
            x += stepX
            y += stepY
        }
    }
}

public data class PlaceGridRule(
    override val condition: ScriptJeiLayoutCondition = AlwaysCondition,
    public val typeId: ResourceLocation? = null,
    public val role: PMJeiRequirementRole? = null,
    /** If true, role INPUT/OUTPUT will also match their *_PER_TICK counterparts. */
    public val mergePerTick: Boolean = false,
    public val startX: Int,
    public val startY: Int,
    public val cols: Int,
    public val rows: Int,
    public val cellW: Int,
    public val cellH: Int,
    public val gapX: Int = 2,
    public val gapY: Int = 2,
    public val variantId: ResourceLocation? = null,
    public val skipPlaced: Boolean = true,
) : ScriptJeiLayoutRule {
    override fun apply(input: ScriptJeiLayoutInput, out: ScriptJeiLayoutOutput) {
        if (cols <= 0 || rows <= 0) return

        val nodes = input.requirementNodes.asSequence()
            .filter { n -> typeId == null || n.typeId == typeId }
            .filter { n -> role == null || roleMatches(n.role, role, mergePerTick) }
            .filter { n -> !skipPlaced || n.nodeId !in out.placedNodeIds }
            .take(cols * rows)
            .toList()

        var idx = 0
        for (r in 0 until rows) {
            val y = startY + r * (cellH + gapY)
            for (c in 0 until cols) {
                if (idx >= nodes.size) return
                val x = startX + c * (cellW + gapX)

                val n = nodes[idx++]
                out.placedNodeIds += n.nodeId
                out.placeNode(n.nodeId, x, y, variantId)
            }
        }
    }
}

public data class AddDecoratorRule(
    override val condition: ScriptJeiLayoutCondition = AlwaysCondition,
    public val decoratorId: ResourceLocation,
    public val x: Int = 0,
    public val y: Int = 0,
    public val data: Map<String, Any> = emptyMap(),
) : ScriptJeiLayoutRule {
    override fun apply(input: ScriptJeiLayoutInput, out: ScriptJeiLayoutOutput) {
        out.addDecorator(decoratorId, x, y, data)
    }
}

/**
 * Place a fixed (node-less) JEI ingredient slot.
 *
 * Values are provided by providerId via JeiFixedSlotProviderRegistry.
 */
public data class PlaceFixedSlotRule(
    override val condition: ScriptJeiLayoutCondition = AlwaysCondition,
    public val providerId: ResourceLocation,
    public val role: JeiSlotRole = JeiSlotRole.CATALYST,
    public val x: Int,
    public val y: Int,
    public val width: Int,
    public val height: Int,
) : ScriptJeiLayoutRule {
    override fun apply(input: ScriptJeiLayoutInput, out: ScriptJeiLayoutOutput) {
        out.placeFixedSlot(providerId, role, x, y, width, height)
    }
}

private fun roleMatches(node: PMJeiRequirementRole, want: PMJeiRequirementRole, mergePerTick: Boolean): Boolean {
    if (!mergePerTick) return node == want
    return when (want) {
        PMJeiRequirementRole.INPUT -> node == PMJeiRequirementRole.INPUT || node == PMJeiRequirementRole.INPUT_PER_TICK
        PMJeiRequirementRole.OUTPUT -> node == PMJeiRequirementRole.OUTPUT || node == PMJeiRequirementRole.OUTPUT_PER_TICK
        else -> node == want
    }
}
