package github.kasuminova.prototypemachinery.integration.jei.builtin.ingredient

import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.FuzzyInputGroup
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.RandomOutputPool
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.RequirementPropertyKeys
import github.kasuminova.prototypemachinery.impl.recipe.requirement.FluidRequirementComponent
import github.kasuminova.prototypemachinery.integration.jei.api.ingredient.PMJeiIngredientKindHandler
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiNodeMetaKeys
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlot
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotKind
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotKinds
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotOverlay
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementNode
import github.kasuminova.prototypemachinery.integration.jei.builtin.ingredient.JeiChanceOverlay.ChanceLabelDrawable
import github.kasuminova.prototypemachinery.integration.jei.builtin.ingredient.JeiChanceOverlay.CompositeDrawable
import github.kasuminova.prototypemachinery.integration.jei.builtin.requirement.FluidRequirementJeiRenderer
import mezz.jei.api.IGuiHelper
import mezz.jei.api.gui.IRecipeLayout
import mezz.jei.api.gui.ITooltipCallback
import mezz.jei.api.ingredients.VanillaTypes
import mezz.jei.api.recipe.IIngredientType
import net.minecraft.client.resources.I18n
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fluids.FluidStack
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap

public object VanillaFluidKindHandler : PMJeiIngredientKindHandler<FluidStack> {

    private data class FluidIoHint(
        val directionKey: String,
        val timingKey: String,
    )

    private data class SlotHint(
        val io: FluidIoHint?,
        val chancePercent: Double?,
        val chanceAttributeId: String?,
        val chanceOverlayLabel: String?,
        val fuzzyCandidateCount: Int?,
        val fuzzyRequiredCount: Long?,
        val fuzzyGroupIndex: Int?,
        val fuzzyCandidateIndex: Int?,
        val randomPickCount: Int?,
        val randomCandidateCount: Int?,
        val randomCandidateIndex: Int?,
        val randomWeightSignatures: List<Pair<FluidStack, Int>>?,
    )

    // One tooltip callback per recipe layout. We keep per-layout slot hints in weak maps.
    private val hookedLayouts: MutableSet<IRecipeLayout> = Collections.newSetFromMap(WeakHashMap())
    private val hintByLayout: MutableMap<IRecipeLayout, MutableMap<Int, SlotHint>> = Collections.synchronizedMap(WeakHashMap())

    override val kind: JeiSlotKind = JeiSlotKinds.FLUID

    override val ingredientType: IIngredientType<FluidStack> = VanillaTypes.FLUID

    override fun init(recipeLayout: IRecipeLayout, guiHelper: IGuiHelper, slot: JeiSlot, node: PMJeiRequirementNode<*>?) {
        // Attach tooltip callback once per layout, and keep a map from slotIndex -> (consume/produce, once/per-tick).
        if (hookedLayouts.add(recipeLayout)) {
            recipeLayout.fluidStacks.addTooltipCallback(object : ITooltipCallback<FluidStack> {
                override fun onTooltip(slotIndex: Int, input: Boolean, ingredient: FluidStack, tooltip: MutableList<String>) {
                    val hint = hintByLayout[recipeLayout]?.get(slotIndex) ?: return

                    val extra = buildTooltipLines(hint, ingredient)
                    if (extra.isEmpty()) return

                    // Insert after the fluid name if possible; otherwise append.
                    val insertAt = if (tooltip.isNotEmpty()) 1 else 0
                    if (insertAt >= 0 && insertAt <= tooltip.size) {
                        tooltip.addAll(insertAt, extra)
                    } else {
                        tooltip.addAll(extra)
                    }
                }
            })
        }

        // Record IO/timing hint for this slot if we have a requirement node.
        val n = node
        val comp = n?.component as? FluidRequirementComponent
        if (n != null && comp != null) {
            val ioHint = when (n.role) {
                github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiRequirementRole.INPUT ->
                    FluidIoHint(
                        directionKey = "prototypemachinery.jei.energy.direction.consume",
                        timingKey = "prototypemachinery.jei.energy.timing.once",
                    )

                github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiRequirementRole.OUTPUT ->
                    FluidIoHint(
                        directionKey = "prototypemachinery.jei.energy.direction.produce",
                        timingKey = "prototypemachinery.jei.energy.timing.once",
                    )

                github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiRequirementRole.INPUT_PER_TICK ->
                    FluidIoHint(
                        directionKey = "prototypemachinery.jei.energy.direction.consume",
                        timingKey = "prototypemachinery.jei.energy.timing.per_tick",
                    )

                github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiRequirementRole.OUTPUT_PER_TICK ->
                    FluidIoHint(
                        directionKey = "prototypemachinery.jei.energy.direction.produce",
                        timingKey = "prototypemachinery.jei.energy.timing.per_tick",
                    )

                else -> null
            }

            val hint = buildSlotHint(n, comp, ioHint)
            if (hint != null) {
                val map = hintByLayout.getOrPut(recipeLayout) { HashMap() }
                map[slot.index] = hint
            }
        }

        val cap = run {
            val n2 = node
            val comp2 = n2?.component as? FluidRequirementComponent
            if (n2 != null && comp2 != null) {
                @Suppress("UNCHECKED_CAST")
                val cast = n2 as PMJeiRequirementNode<FluidRequirementComponent>
                FluidRequirementJeiRenderer.getCapacityMb(cast)
            } else {
                1000
            }
        }

        val overlayDrawable = slot.overlay?.toDrawable(guiHelper)

        // Draw overlay (module glass/marks) first, then the chance label on top.
        val hint = hintByLayout[recipeLayout]?.get(slot.index)
        val label = hint?.chanceOverlayLabel
        val overlayWithChance = if (!label.isNullOrBlank()) {
            val base = overlayDrawable ?: guiHelper.slotDrawable
            CompositeDrawable(
                base = base,
                top = ChanceLabelDrawable(label = label, width = base.width, height = base.height),
            )
        } else {
            overlayDrawable
        }

        recipeLayout.fluidStacks.init(
            slot.index,
            slot.role == github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotRole.INPUT,
            slot.x,
            slot.y,
            slot.width,
            slot.height,
            cap,
            false,
            // If we have a custom overlay (e.g. module scale marks/glass), use it.
            // Otherwise fall back to JEI's default slot overlay for legacy tank variants.
            overlayWithChance ?: guiHelper.slotDrawable,
        )
    }

    override fun set(recipeLayout: IRecipeLayout, slot: JeiSlot, values: List<FluidStack>) {
        recipeLayout.fluidStacks.set(slot.index, values)
    }

    private fun buildTooltipLines(hint: SlotHint, ingredient: FluidStack): List<String> {
        val out = ArrayList<String>(5)

        hint.io?.let {
            out += I18n.format(
                "prototypemachinery.jei.fluid.tooltip.io",
                I18n.format(it.directionKey),
                I18n.format(it.timingKey),
            )
        }

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
        return parts.getOrNull(1)?.toIntOrNull()
    }

    private fun resolveRandomWeight(hint: SlotHint, ingredient: FluidStack): Int? {
        val list = hint.randomWeightSignatures ?: return null
        for ((sig, w) in list) {
            if (ingredient.isFluidEqual(sig) && ingredient.tag == sig.tag) {
                return w
            }
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildSlotHint(node: PMJeiRequirementNode<*>, comp: FluidRequirementComponent, ioHint: FluidIoHint?): SlotHint? {
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
            val groups = comp.properties[RequirementPropertyKeys.FUZZY_INPUTS] as? List<FuzzyInputGroup<FluidStack>>
            val group = groups?.getOrNull(node.index)
            if (group != null) {
                fuzzyCandidates = group.candidates.size
                fuzzyRequired = group.count
                fuzzyGroupIndex = node.index
                fuzzyCandidateIndex = parseCandidateIndex(node.nodeId, ":fuzzy_input:")
            }
        }

        var randomPickCount: Int? = null
        var randomCandidateCount: Int? = null
        var randomCandidateIndex: Int? = null
        var randomWeightSignatures: List<Pair<FluidStack, Int>>? = null
        if (node.nodeId.contains(":random_output:")) {
            val pool = comp.properties[RequirementPropertyKeys.RANDOM_OUTPUTS] as? RandomOutputPool<FluidStack>
            if (pool != null) {
                randomPickCount = pool.pickCount
                randomCandidateCount = pool.candidates.size
                randomCandidateIndex = parseCandidateIndex(node.nodeId, ":random_output:")
                randomWeightSignatures = pool.candidates.mapNotNull { wk ->
                    val s = wk.key.get()
                    if (s.fluid == null) return@mapNotNull null
                    val sig = FluidStack(s, 1)
                    sig to wk.weight
                }
            }
        }

        if (ioHint == null && chance == null && chanceAttr == null && fuzzyCandidates == null && randomPickCount == null) return null

        return SlotHint(
            io = ioHint,
            chancePercent = chance,
            chanceAttributeId = chanceAttr,
            chanceOverlayLabel = overlayLabel,
            fuzzyCandidateCount = fuzzyCandidates,
            fuzzyRequiredCount = fuzzyRequired,
            fuzzyGroupIndex = fuzzyGroupIndex,
            fuzzyCandidateIndex = fuzzyCandidateIndex,
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

private fun JeiSlotOverlay.toDrawable(guiHelper: IGuiHelper): mezz.jei.api.gui.IDrawable {
    // IMPORTANT: these module textures are not 256x256; provide the real texture size.
    return guiHelper.drawableBuilder(texture, u, v, width, height)
        .setTextureSize(textureWidth, textureHeight)
        .build()
}
