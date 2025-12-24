package github.kasuminova.prototypemachinery.integration.jei.builtin.ingredient

import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.DynamicItemInputGroup
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.FuzzyInputGroup
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.RandomOutputPool
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.RequirementPropertyKeys
import github.kasuminova.prototypemachinery.impl.recipe.requirement.ItemRequirementComponent
import github.kasuminova.prototypemachinery.integration.jei.api.ingredient.PMJeiIngredientKindHandler
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiNodeMetaKeys
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlot
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotKind
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotKinds
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementNode
import github.kasuminova.prototypemachinery.integration.jei.builtin.ingredient.JeiChanceOverlay.ChanceOverlayItemRenderer
import mezz.jei.api.IGuiHelper
import mezz.jei.api.gui.IRecipeLayout
import mezz.jei.api.gui.ITooltipCallback
import mezz.jei.api.ingredients.VanillaTypes
import mezz.jei.api.recipe.IIngredientType
import net.minecraft.client.resources.I18n
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap

public object VanillaItemKindHandler : PMJeiIngredientKindHandler<ItemStack> {

    private data class SlotHint(
        val chancePercent: Double?,
        val chanceAttributeId: String?,
        val chanceOverlayLabel: String?,
        val fuzzyCandidateCount: Int?,
        val fuzzyRequiredCount: Long?,
        val fuzzyGroupIndex: Int?,
        val fuzzyCandidateIndex: Int?,
        val dynamicMatcherId: String?,
        val dynamicCandidateCount: Int?,
        val dynamicRequiredCount: Long?,
        val dynamicGroupIndex: Int?,
        val dynamicCandidateIndex: Int?,
        val randomPickCount: Int?,
        val randomCandidateCount: Int?,
        val randomCandidateIndex: Int?,
        val randomWeightSignatures: List<Pair<ItemStack, Int>>?,
    )

    // One tooltip callback per recipe layout. We keep per-layout slot hints in weak maps.
    private val hookedLayouts: MutableSet<IRecipeLayout> = Collections.newSetFromMap(WeakHashMap())
    private val hintByLayout: MutableMap<IRecipeLayout, MutableMap<Int, SlotHint>> = Collections.synchronizedMap(WeakHashMap())

    override val kind: JeiSlotKind = JeiSlotKinds.ITEM

    override val ingredientType: IIngredientType<ItemStack> = VanillaTypes.ITEM

    override fun init(recipeLayout: IRecipeLayout, guiHelper: IGuiHelper, slot: JeiSlot, node: PMJeiRequirementNode<*>?) {
        // Attach tooltip callback once per layout, and keep a map from slotIndex -> hint data.
        if (hookedLayouts.add(recipeLayout)) {
            recipeLayout.itemStacks.addTooltipCallback(object : ITooltipCallback<ItemStack> {
                override fun onTooltip(slotIndex: Int, input: Boolean, ingredient: ItemStack, tooltip: MutableList<String>) {
                    val hint = hintByLayout[recipeLayout]?.get(slotIndex) ?: return

                    val extra = buildTooltipLines(hint, ingredient)
                    if (extra.isEmpty()) return

                    // Insert after the item name if possible; otherwise append.
                    val insertAt = if (tooltip.isNotEmpty()) 1 else 0
                    if (insertAt >= 0 && insertAt <= tooltip.size) {
                        tooltip.addAll(insertAt, extra)
                    } else {
                        tooltip.addAll(extra)
                    }
                }
            })
        }

        // Record hint for this slot if we have a requirement node.
        val comp = node?.component as? ItemRequirementComponent
        if (node != null && comp != null) {
            val hint = buildSlotHint(node, comp)
            if (hint != null) {
                val map = hintByLayout.getOrPut(recipeLayout) { HashMap() }
                map[slot.index] = hint
            }
        }

        val isInput = slot.role == github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotRole.INPUT

        // Render chance label in JEI's ingredient renderer so it stays above the item stack.
        val hint = hintByLayout[recipeLayout]?.get(slot.index)
        val label = hint?.chanceOverlayLabel

        if (!label.isNullOrBlank() && slot.width >= 16 && slot.height >= 16) {
            // Match JEI default: 16x16 item + 1px padding each side => 18x18.
            val padX = ((slot.width - 16) / 2).coerceAtLeast(0)
            val padY = ((slot.height - 16) / 2).coerceAtLeast(0)
            recipeLayout.itemStacks.init(
                slot.index,
                isInput,
                ChanceOverlayItemRenderer(label),
                slot.x,
                slot.y,
                slot.width,
                slot.height,
                padX,
                padY,
            )
        } else {
            recipeLayout.itemStacks.init(slot.index, isInput, slot.x, slot.y)
        }
    }

    override fun set(recipeLayout: IRecipeLayout, slot: JeiSlot, values: List<ItemStack>) {
        recipeLayout.itemStacks.set(slot.index, values)
    }

    private fun buildTooltipLines(hint: SlotHint, ingredient: ItemStack): List<String> {
        val out = ArrayList<String>(4)

        val chance = hint.chancePercent
        if (chance != null && chance.isFinite() && (chance != 100.0 || !hint.chanceAttributeId.isNullOrBlank())) {
            out += I18n.format("prototypemachinery.jei.requirement.chance", formatPercent(chance))
        }

        val attr = hint.chanceAttributeId
        if (!attr.isNullOrBlank()) {
            out += I18n.format("prototypemachinery.jei.requirement.chance_attribute", attr)
        }

        if (hint.fuzzyCandidateCount != null && hint.fuzzyRequiredCount != null) {
            out += I18n.format(
                "prototypemachinery.jei.requirement.fuzzy_input",
                hint.fuzzyCandidateCount.toString(),
                hint.fuzzyRequiredCount.toString(),
            )

            // Expanded mode: each slot is one candidate, but they are part of the same fuzzy requirement.
            if (hint.fuzzyCandidateIndex != null && hint.fuzzyGroupIndex != null) {
                out += I18n.format(
                    "prototypemachinery.jei.requirement.fuzzy_input.group",
                    (hint.fuzzyGroupIndex + 1).toString(),
                )
                out += I18n.format(
                    "prototypemachinery.jei.requirement.fuzzy_input.candidate",
                    (hint.fuzzyCandidateIndex + 1).toString(),
                    hint.fuzzyCandidateCount.toString(),
                )
            }
        }

        if (hint.dynamicCandidateCount != null && hint.dynamicRequiredCount != null) {
            out += I18n.format(
                "prototypemachinery.jei.requirement.dynamic_input",
                hint.dynamicCandidateCount.toString(),
                hint.dynamicRequiredCount.toString(),
            )

            val mid = hint.dynamicMatcherId
            if (!mid.isNullOrBlank()) {
                out += I18n.format("prototypemachinery.jei.requirement.dynamic_input.matcher", mid)
            }

            if (hint.dynamicCandidateIndex != null && hint.dynamicGroupIndex != null) {
                out += I18n.format(
                    "prototypemachinery.jei.requirement.dynamic_input.group",
                    (hint.dynamicGroupIndex + 1).toString(),
                )
                out += I18n.format(
                    "prototypemachinery.jei.requirement.dynamic_input.candidate",
                    (hint.dynamicCandidateIndex + 1).toString(),
                    hint.dynamicCandidateCount.toString(),
                )
            }
        }

        if (hint.randomPickCount != null && hint.randomCandidateCount != null) {
            out += I18n.format(
                "prototypemachinery.jei.requirement.random_output",
                hint.randomPickCount.toString(),
                hint.randomCandidateCount.toString(),
            )

            if (hint.randomCandidateIndex != null) {
                out += I18n.format(
                    "prototypemachinery.jei.requirement.random_output.group",
                    "1",
                )
                out += I18n.format(
                    "prototypemachinery.jei.requirement.random_output.candidate",
                    (hint.randomCandidateIndex + 1).toString(),
                    hint.randomCandidateCount.toString(),
                )
            }

            val w = resolveRandomWeight(hint, ingredient)
            if (w != null) {
                out += I18n.format("prototypemachinery.jei.requirement.random_output.weight", w.toString())
            }
        }

        return out
    }

    private fun parseCandidateIndex(nodeId: String, marker: String): Int? {
        val pos = nodeId.indexOf(marker)
        if (pos < 0) return null
        val tail = nodeId.substring(pos + marker.length)
        val parts = tail.split(':')
        // expected:
        // - fuzzy_input:<group>
        // - fuzzy_input:<group>:<candidate>
        // - random_output:<pick>
        // - random_output:<pool>:<candidate>
        return parts.getOrNull(1)?.toIntOrNull()
    }

    private fun resolveRandomWeight(hint: SlotHint, ingredient: ItemStack): Int? {
        val list = hint.randomWeightSignatures ?: return null
        if (ingredient.isEmpty) return null
        for ((sig, w) in list) {
            if (ItemStack.areItemsEqual(sig, ingredient) && ItemStack.areItemStackTagsEqual(sig, ingredient)) {
                return w
            }
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildSlotHint(node: PMJeiRequirementNode<*>, comp: ItemRequirementComponent): SlotHint? {
        val chance = (comp.properties[RequirementPropertyKeys.CHANCE] as? Number)?.toDouble()
        val chanceAttr = when (val v = comp.properties[RequirementPropertyKeys.CHANCE_ATTRIBUTE]) {
            is ResourceLocation -> v.toString()
            is String -> v
            else -> null
        }

        // Presence of this key means overlay is enabled for this layout/node.
        val overlayLabel = node.metadata[JeiNodeMetaKeys.CHANCE_OVERLAY_LABEL] as? String

        var fuzzyCandidates: Int? = null
        var fuzzyRequired: Long? = null
        var fuzzyGroupIndex: Int? = null
        var fuzzyCandidateIndex: Int? = null
        if (node.nodeId.contains(":fuzzy_input:")) {
            val groups = comp.properties[RequirementPropertyKeys.FUZZY_INPUTS] as? List<FuzzyInputGroup<ItemStack>>
            val group = groups?.getOrNull(node.index)
            if (group != null) {
                fuzzyCandidates = group.candidates.size
                fuzzyRequired = group.count
                fuzzyGroupIndex = node.index
                fuzzyCandidateIndex = parseCandidateIndex(node.nodeId, ":fuzzy_input:")
            }
        }

        var dynamicMatcherId: String? = null
        var dynamicCandidates: Int? = null
        var dynamicRequired: Long? = null
        var dynamicGroupIndex: Int? = null
        var dynamicCandidateIndex: Int? = null
        if (node.nodeId.contains(":dynamic_input:")) {
            val groups = comp.properties[RequirementPropertyKeys.DYNAMIC_ITEM_INPUTS] as? List<DynamicItemInputGroup>
            val group = groups?.getOrNull(node.index)
            if (group != null) {
                val shown = if (group.displayedCandidates.isNotEmpty()) group.displayedCandidates else listOf(group.pattern)
                dynamicMatcherId = group.matcherId
                dynamicCandidates = shown.size
                dynamicRequired = group.count
                dynamicGroupIndex = node.index
                dynamicCandidateIndex = parseCandidateIndex(node.nodeId, ":dynamic_input:")
            }
        }

        var randomPickCount: Int? = null
        var randomCandidateCount: Int? = null
        var randomCandidateIndex: Int? = null
        var randomWeightSignatures: List<Pair<ItemStack, Int>>? = null
        if (node.nodeId.contains(":random_output:")) {
            val pool = comp.properties[RequirementPropertyKeys.RANDOM_OUTPUTS] as? RandomOutputPool<ItemStack>
            if (pool != null) {
                randomPickCount = pool.pickCount
                randomCandidateCount = pool.candidates.size
                randomCandidateIndex = parseCandidateIndex(node.nodeId, ":random_output:")
                randomWeightSignatures = pool.candidates.mapNotNull { wk ->
                    val s = wk.key.get()
                    if (s.isEmpty) return@mapNotNull null
                    val sig = s.copy()
                    sig.count = 1
                    sig to wk.weight
                }
            }
        }

        // Don't allocate per-slot data if there's nothing to show.
        if (
            chance == null &&
            chanceAttr == null &&
            fuzzyCandidates == null &&
            dynamicCandidates == null &&
            randomPickCount == null
        ) return null

        return SlotHint(
            chancePercent = chance,
            chanceAttributeId = chanceAttr,
            chanceOverlayLabel = overlayLabel,
            fuzzyCandidateCount = fuzzyCandidates,
            fuzzyRequiredCount = fuzzyRequired,
            fuzzyGroupIndex = fuzzyGroupIndex,
            fuzzyCandidateIndex = fuzzyCandidateIndex,
            dynamicMatcherId = dynamicMatcherId,
            dynamicCandidateCount = dynamicCandidates,
            dynamicRequiredCount = dynamicRequired,
            dynamicGroupIndex = dynamicGroupIndex,
            dynamicCandidateIndex = dynamicCandidateIndex,
            randomPickCount = randomPickCount,
            randomCandidateCount = randomCandidateCount,
            randomCandidateIndex = randomCandidateIndex,
            randomWeightSignatures = randomWeightSignatures,
        )
    }

    private fun formatPercent(v: Double): String {
        if (!v.isFinite()) return "0"
        val rounded = kotlin.math.round(v * 100.0) / 100.0
        val asLong = rounded.toLong()
        return if (rounded == asLong.toDouble()) {
            asLong.toString()
        } else {
            String.format(Locale.ROOT, "%.2f", rounded).trimEnd('0').trimEnd('.')
        }
    }
}
