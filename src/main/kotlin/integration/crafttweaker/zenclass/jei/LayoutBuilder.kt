package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.jei

import crafttweaker.annotations.ZenRegister
import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiMachineLayoutDefinition
import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiRequirementRole
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotRole
import github.kasuminova.prototypemachinery.integration.jei.builtin.JeiBackgroundSpec
import github.kasuminova.prototypemachinery.integration.jei.builtin.decorator.ProgressArrowJeiDecorator
import github.kasuminova.prototypemachinery.integration.jei.builtin.decorator.ProgressModuleJeiDecorator
import github.kasuminova.prototypemachinery.integration.jei.layout.script.AddDecoratorRule
import github.kasuminova.prototypemachinery.integration.jei.layout.script.AutoPlaceRemainingSpec
import github.kasuminova.prototypemachinery.integration.jei.layout.script.CountAtLeastCondition
import github.kasuminova.prototypemachinery.integration.jei.layout.script.PlaceAllLinearRule
import github.kasuminova.prototypemachinery.integration.jei.layout.script.PlaceByNodeIdRule
import github.kasuminova.prototypemachinery.integration.jei.layout.script.PlaceFirstRule
import github.kasuminova.prototypemachinery.integration.jei.layout.script.PlaceFixedSlotRule
import github.kasuminova.prototypemachinery.integration.jei.layout.script.PlaceGridRule
import github.kasuminova.prototypemachinery.integration.jei.layout.script.ScriptJeiLayoutCondition
import github.kasuminova.prototypemachinery.integration.jei.layout.script.ScriptJeiLayoutRule
import github.kasuminova.prototypemachinery.integration.jei.layout.script.ScriptJeiLayoutSpec
import github.kasuminova.prototypemachinery.integration.jei.layout.script.ScriptJeiMachineLayoutDefinition
import net.minecraft.util.ResourceLocation
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod
import java.util.Locale

@ZenClass("mods.prototypemachinery.jei.LayoutBuilder")
@ZenRegister
public class LayoutBuilder {

    private var width: Int = 160
    private var height: Int = 80

    private val rules: MutableList<ScriptJeiLayoutRule> = ArrayList()

    private var autoPlaceRemaining: AutoPlaceRemainingSpec? = null

    /**
     * If enabled, role="INPUT" matches INPUT + INPUT_PER_TICK; role="OUTPUT" matches OUTPUT + OUTPUT_PER_TICK.
     *
     * Default is false to preserve the ability to place per-tick nodes separately.
     */
    private var mergePerTickRoles: Boolean = false

    internal fun isMergePerTickRolesEnabled(): Boolean = mergePerTickRoles

    @ZenMethod
    public fun setSize(width: Int, height: Int): LayoutBuilder {
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
        return this
    }

    @ZenMethod
    public fun mergePerTickRoles(enabled: Boolean): LayoutBuilder {
        this.mergePerTickRoles = enabled
        return this
    }

    @ZenMethod
    public fun placeNode(nodeId: String, x: Int, y: Int): LayoutBuilder {
        addRule(PlaceByNodeIdRule(nodeId = nodeId, x = x, y = y))
        return this
    }

    @ZenMethod
    public fun placeNodeWithVariant(nodeId: String, x: Int, y: Int, variantId: String): LayoutBuilder {
        addRule(PlaceByNodeIdRule(nodeId = nodeId, x = x, y = y, variantId = ResourceLocation(variantId)))
        return this
    }

    /**
     * Place a node with a variant and renderer-specific data.
     *
     * Example for energy led y offset override:
     * data = {"energyLedYOffset": -1}
     */
    @ZenMethod
    public fun placeNodeWithVariantAndData(nodeId: String, x: Int, y: Int, variantId: String, data: java.util.Map<*, *>?): LayoutBuilder {
        val map = coerceDataMap(data)
        addRule(
            PlaceByNodeIdRule(
                nodeId = nodeId,
                x = x,
                y = y,
                variantId = ResourceLocation(variantId),
                data = map,
            )
        )
        return this
    }

    @ZenMethod
    public fun placeFirst(typeId: String, role: String?, x: Int, y: Int): LayoutBuilder {
        addRule(
            PlaceFirstRule(
                typeId = ResourceLocation(typeId),
                role = parseRole(role),
                mergePerTick = mergePerTickRoles,
                x = x,
                y = y,
            )
        )
        return this
    }

    @ZenMethod
    public fun placeFirstWithVariant(typeId: String, role: String?, x: Int, y: Int, variantId: String): LayoutBuilder {
        addRule(
            PlaceFirstRule(
                typeId = ResourceLocation(typeId),
                role = parseRole(role),
                mergePerTick = mergePerTickRoles,
                x = x,
                y = y,
                variantId = ResourceLocation(variantId),
            )
        )
        return this
    }

    /**
     * Place the first matching node with a variant and renderer-specific data.
     *
     * Example for energy led y offset override:
     * data = {"energyLedYOffset": -1}
     */
    @ZenMethod
    public fun placeFirstWithVariantAndData(typeId: String, role: String?, x: Int, y: Int, variantId: String, data: java.util.Map<*, *>?): LayoutBuilder {
        val map = coerceDataMap(data)
        addRule(
            PlaceFirstRule(
                typeId = ResourceLocation(typeId),
                role = parseRole(role),
                mergePerTick = mergePerTickRoles,
                x = x,
                y = y,
                variantId = ResourceLocation(variantId),
                data = map,
            )
        )
        return this
    }

    @ZenMethod
    public fun placeAllLinear(typeId: String, role: String?, startX: Int, startY: Int, stepX: Int, stepY: Int): LayoutBuilder {
        addRule(
            PlaceAllLinearRule(
                typeId = ResourceLocation(typeId),
                role = parseRole(role),
                mergePerTick = mergePerTickRoles,
                startX = startX,
                startY = startY,
                stepX = stepX,
                stepY = stepY,
            )
        )
        return this
    }

    @ZenMethod
    public fun placeAllLinearMax(typeId: String, role: String?, startX: Int, startY: Int, stepX: Int, stepY: Int, maxCount: Int): LayoutBuilder {
        addRule(
            PlaceAllLinearRule(
                typeId = ResourceLocation(typeId),
                role = parseRole(role),
                mergePerTick = mergePerTickRoles,
                startX = startX,
                startY = startY,
                stepX = stepX,
                stepY = stepY,
                maxCount = maxCount.coerceAtLeast(0),
            )
        )
        return this
    }

    @ZenMethod
    public fun placeAllLinearWithVariant(typeId: String, role: String?, startX: Int, startY: Int, stepX: Int, stepY: Int, variantId: String): LayoutBuilder {
        addRule(
            PlaceAllLinearRule(
                typeId = ResourceLocation(typeId),
                role = parseRole(role),
                mergePerTick = mergePerTickRoles,
                startX = startX,
                startY = startY,
                stepX = stepX,
                stepY = stepY,
                variantId = ResourceLocation(variantId),
            )
        )
        return this
    }

    @ZenMethod
    public fun placeAllLinearWithVariantAndData(typeId: String, role: String?, startX: Int, startY: Int, stepX: Int, stepY: Int, variantId: String, data: java.util.Map<*, *>?): LayoutBuilder {
        val map = coerceDataMap(data)
        addRule(
            PlaceAllLinearRule(
                typeId = ResourceLocation(typeId),
                role = parseRole(role),
                mergePerTick = mergePerTickRoles,
                startX = startX,
                startY = startY,
                stepX = stepX,
                stepY = stepY,
                variantId = ResourceLocation(variantId),
                data = map,
            )
        )
        return this
    }

    @ZenMethod
    public fun placeGrid(typeId: String, role: String?, startX: Int, startY: Int, cols: Int, rows: Int, cellW: Int, cellH: Int): LayoutBuilder {
        addRule(
            PlaceGridRule(
                typeId = ResourceLocation(typeId),
                role = parseRole(role),
                mergePerTick = mergePerTickRoles,
                startX = startX,
                startY = startY,
                cols = cols,
                rows = rows,
                cellW = cellW,
                cellH = cellH,
            )
        )
        return this
    }

    @ZenMethod
    public fun placeGridSpaced(typeId: String, role: String?, startX: Int, startY: Int, cols: Int, rows: Int, cellW: Int, cellH: Int, gapX: Int, gapY: Int): LayoutBuilder {
        addRule(
            PlaceGridRule(
                typeId = ResourceLocation(typeId),
                role = parseRole(role),
                mergePerTick = mergePerTickRoles,
                startX = startX,
                startY = startY,
                cols = cols,
                rows = rows,
                cellW = cellW,
                cellH = cellH,
                gapX = gapX,
                gapY = gapY,
            )
        )
        return this
    }

    @ZenMethod
    public fun placeGridWithVariant(typeId: String, role: String?, startX: Int, startY: Int, cols: Int, rows: Int, cellW: Int, cellH: Int, variantId: String): LayoutBuilder {
        addRule(
            PlaceGridRule(
                typeId = ResourceLocation(typeId),
                role = parseRole(role),
                mergePerTick = mergePerTickRoles,
                startX = startX,
                startY = startY,
                cols = cols,
                rows = rows,
                cellW = cellW,
                cellH = cellH,
                variantId = ResourceLocation(variantId),
            )
        )
        return this
    }

    @ZenMethod
    public fun placeGridWithVariantAndData(typeId: String, role: String?, startX: Int, startY: Int, cols: Int, rows: Int, cellW: Int, cellH: Int, variantId: String, data: java.util.Map<*, *>?): LayoutBuilder {
        val map = coerceDataMap(data)
        addRule(
            PlaceGridRule(
                typeId = ResourceLocation(typeId),
                role = parseRole(role),
                mergePerTick = mergePerTickRoles,
                startX = startX,
                startY = startY,
                cols = cols,
                rows = rows,
                cellW = cellW,
                cellH = cellH,
                variantId = ResourceLocation(variantId),
                data = map,
            )
        )
        return this
    }

    @ZenMethod
    public fun addDecorator(decoratorId: String, x: Int, y: Int): LayoutBuilder {
        addRule(AddDecoratorRule(decoratorId = ResourceLocation(decoratorId), x = x, y = y))
        return this
    }

    @ZenMethod
    public fun addDecoratorWithData(decoratorId: String, x: Int, y: Int, data: java.util.Map<*, *>?): LayoutBuilder {
        val map = coerceDataMap(data)
        addRule(AddDecoratorRule(decoratorId = ResourceLocation(decoratorId), x = x, y = y, data = map))
        return this
    }

    /**
     * Override the default JEI panel background.
     *
     * @param texture Either:
     * - a path relative to `prototypemachinery:textures/gui/jei_recipeicons/` (e.g. "jei_base.png")
     * - or a full resource location string (e.g. "mymod:textures/gui/foo.png")
     * @param borderPx Border thickness in pixels (default 2).
     * @param fillCenter If true, fill center with sampled solid color instead of stretching.
     */
    @ZenMethod
    public fun setBackgroundNineSlice(texture: String, borderPx: Int, fillCenter: Boolean): LayoutBuilder {
        val data = HashMap<String, Any>()
        data["texture"] = texture
        data["borderPx"] = borderPx
        data["fillCenter"] = fillCenter
        addRule(AddDecoratorRule(decoratorId = JeiBackgroundSpec.id, x = 0, y = 0, data = data))
        return this
    }

    @ZenMethod
    public fun setBackgroundNineSlice(texture: String): LayoutBuilder {
        return setBackgroundNineSlice(texture, 2, false)
    }

    @ZenMethod
    public fun addProgressArrow(x: Int, y: Int, style: String?, direction: String?): LayoutBuilder {
        val data = HashMap<String, Any>()
        if (style != null) data["style"] = style
        if (direction != null) data["direction"] = direction
        addRule(AddDecoratorRule(decoratorId = ProgressArrowJeiDecorator.id, x = x, y = y, data = data))
        return this
    }

    @ZenMethod
    public fun addProgressModule(type: String, x: Int, y: Int, direction: String?): LayoutBuilder {
        val data = HashMap<String, Any>()
        data["type"] = type
        if (direction != null) data["direction"] = direction
        addRule(AddDecoratorRule(decoratorId = ProgressModuleJeiDecorator.id, x = x, y = y, data = data))
        return this
    }

    /**
     * Place a fixed (node-less) ingredient slot.
     *
     * Values are provided by providerId via JeiFixedSlotProviderRegistry.
     */
    @ZenMethod
    public fun placeFixedSlot(providerId: String, role: String?, x: Int, y: Int, width: Int, height: Int): LayoutBuilder {
        addRule(
            PlaceFixedSlotRule(
                providerId = ResourceLocation(providerId),
                role = parseSlotRole(role),
                x = x,
                y = y,
                width = width,
                height = height,
            )
        )
        return this
    }

    @ZenMethod
    public fun autoPlaceRemaining(startX: Int, startY: Int): LayoutBuilder {
        this.autoPlaceRemaining = AutoPlaceRemainingSpec(startX = startX, startY = startY)
        return this
    }

    @ZenMethod
    public fun autoPlaceRemainingWithSpacing(startX: Int, startY: Int, gapX: Int, gapY: Int, pad: Int): LayoutBuilder {
        this.autoPlaceRemaining = AutoPlaceRemainingSpec(startX = startX, startY = startY, gapX = gapX, gapY = gapY, pad = pad)
        return this
    }

    /**
     * Create a conditional view of this builder.
     *
     * Example:
     * whenCountAtLeast("prototypemachinery:fluid", "INPUT", 2).placeGrid(...).then()
     */
    @ZenMethod
    public fun whenCountAtLeast(typeId: String?, role: String?, min: Int): ConditionalLayoutBuilder {
        val condition: ScriptJeiLayoutCondition = CountAtLeastCondition(
            typeId = typeId?.let { ResourceLocation(it) },
            role = parseRole(role),
            mergePerTick = mergePerTickRoles,
            min = min.coerceAtLeast(0),
        )
        return ConditionalLayoutBuilder(this, condition)
    }

    internal fun addRule(rule: ScriptJeiLayoutRule) {
        rules += rule
    }

    internal fun build(): PMJeiMachineLayoutDefinition {
        return ScriptJeiMachineLayoutDefinition(
            ScriptJeiLayoutSpec(
                width = width,
                height = height,
                rules = rules.toList(),
                autoPlaceRemaining = autoPlaceRemaining,
            )
        )
    }

    private fun parseRole(raw: String?): PMJeiRequirementRole? {
        if (raw == null) return null
        val s = raw.trim()
        if (s.isEmpty()) return null

        return when (s.uppercase(Locale.ROOT)) {
            "INPUT" -> PMJeiRequirementRole.INPUT
            "OUTPUT" -> PMJeiRequirementRole.OUTPUT
            "INPUT_PER_TICK", "INPUTPERTICK" -> PMJeiRequirementRole.INPUT_PER_TICK
            "OUTPUT_PER_TICK", "OUTPUTPERTICK" -> PMJeiRequirementRole.OUTPUT_PER_TICK
            "OTHER" -> PMJeiRequirementRole.OTHER
            "ANY", "*" -> null
            else -> null
        }
    }

    private fun parseSlotRole(raw: String?): JeiSlotRole {
        if (raw == null) return JeiSlotRole.CATALYST
        val s = raw.trim()
        if (s.isEmpty()) return JeiSlotRole.CATALYST

        return when (s.uppercase(Locale.ROOT)) {
            "INPUT" -> JeiSlotRole.INPUT
            "OUTPUT" -> JeiSlotRole.OUTPUT
            "CATALYST" -> JeiSlotRole.CATALYST
            "OTHER", "ANY", "*" -> JeiSlotRole.CATALYST
            else -> JeiSlotRole.CATALYST
        }
    }

    private fun coerceDataMap(raw: java.util.Map<*, *>?): Map<String, Any> {
        if (raw == null) return emptyMap()

        val out = LinkedHashMap<String, Any>()
        for (entry in raw.entrySet()) {
            val key = entry.key as? String ?: continue
            val v = entry.value ?: continue
            out[key] = v
        }
        return out
    }
}

@ZenClass("mods.prototypemachinery.jei.ConditionalLayoutBuilder")
@ZenRegister
public class ConditionalLayoutBuilder internal constructor(
    private val parent: LayoutBuilder,
    private val condition: ScriptJeiLayoutCondition,
) {

    @ZenMethod
    public fun then(): LayoutBuilder = parent

    @ZenMethod
    public fun placeFirst(typeId: String, role: String?, x: Int, y: Int): ConditionalLayoutBuilder {
        parent.addRule(
            PlaceFirstRule(
                condition = condition,
                typeId = ResourceLocation(typeId),
                role = parentWhenParseRole(role),
                mergePerTick = parentWhenMergePerTick(),
                x = x,
                y = y,
            )
        )
        return this
    }

    @ZenMethod
    public fun placeFirstWithVariantAndData(typeId: String, role: String?, x: Int, y: Int, variantId: String, data: java.util.Map<*, *>?): ConditionalLayoutBuilder {
        val map = parentCoerceDataMap(data)
        parent.addRule(
            PlaceFirstRule(
                condition = condition,
                typeId = ResourceLocation(typeId),
                role = parentWhenParseRole(role),
                mergePerTick = parentWhenMergePerTick(),
                x = x,
                y = y,
                variantId = ResourceLocation(variantId),
                data = map,
            )
        )
        return this
    }

    @ZenMethod
    public fun placeGrid(typeId: String, role: String?, startX: Int, startY: Int, cols: Int, rows: Int, cellW: Int, cellH: Int): ConditionalLayoutBuilder {
        parent.addRule(
            PlaceGridRule(
                condition = condition,
                typeId = ResourceLocation(typeId),
                role = parentWhenParseRole(role),
                mergePerTick = parentWhenMergePerTick(),
                startX = startX,
                startY = startY,
                cols = cols,
                rows = rows,
                cellW = cellW,
                cellH = cellH,
            )
        )
        return this
    }

    @ZenMethod
    public fun placeGridWithVariantAndData(typeId: String, role: String?, startX: Int, startY: Int, cols: Int, rows: Int, cellW: Int, cellH: Int, variantId: String, data: java.util.Map<*, *>?): ConditionalLayoutBuilder {
        val map = parentCoerceDataMap(data)
        parent.addRule(
            PlaceGridRule(
                condition = condition,
                typeId = ResourceLocation(typeId),
                role = parentWhenParseRole(role),
                mergePerTick = parentWhenMergePerTick(),
                startX = startX,
                startY = startY,
                cols = cols,
                rows = rows,
                cellW = cellW,
                cellH = cellH,
                variantId = ResourceLocation(variantId),
                data = map,
            )
        )
        return this
    }

    @ZenMethod
    public fun addDecorator(decoratorId: String, x: Int, y: Int): ConditionalLayoutBuilder {
        parent.addRule(
            AddDecoratorRule(
                condition = condition,
                decoratorId = ResourceLocation(decoratorId),
                x = x,
                y = y,
            )
        )
        return this
    }

    /**
     * Override the default JEI panel background.
     *
     * @param texture Either:
     * - a path relative to `prototypemachinery:textures/gui/jei_recipeicons/` (e.g. "jei_base.png")
     * - or a full resource location string (e.g. "mymod:textures/gui/foo.png")
     * @param borderPx Border thickness in pixels (default 2).
     * @param fillCenter If true, fill center with sampled solid color instead of stretching.
     */
    @ZenMethod
    public fun setBackgroundNineSlice(texture: String, borderPx: Int, fillCenter: Boolean): ConditionalLayoutBuilder {
        val data = HashMap<String, Any>()
        data["texture"] = texture
        data["borderPx"] = borderPx
        data["fillCenter"] = fillCenter
        parent.addRule(
            AddDecoratorRule(
                condition = condition,
                decoratorId = JeiBackgroundSpec.id,
                x = 0,
                y = 0,
                data = data
            )
        )
        return this
    }

    @ZenMethod
    public fun setBackgroundNineSlice(texture: String): ConditionalLayoutBuilder {
        return setBackgroundNineSlice(texture, 2, false)
    }

    @ZenMethod
    public fun addProgressArrow(x: Int, y: Int, style: String?, direction: String?): ConditionalLayoutBuilder {
        val data = HashMap<String, Any>()
        if (style != null) data["style"] = style
        if (direction != null) data["direction"] = direction
        parent.addRule(
            AddDecoratorRule(
                condition = condition,
                decoratorId = ProgressArrowJeiDecorator.id,
                x = x,
                y = y,
                data = data
            )
        )
        return this
    }

    @ZenMethod
    public fun addProgressModule(type: String, x: Int, y: Int, direction: String?): ConditionalLayoutBuilder {
        val data = HashMap<String, Any>()
        data["type"] = type
        if (direction != null) data["direction"] = direction
        parent.addRule(
            AddDecoratorRule(
                condition = condition,
                decoratorId = ProgressModuleJeiDecorator.id,
                x = x,
                y = y,
                data = data
            )
        )
        return this
    }

    @ZenMethod
    public fun placeFixedSlot(providerId: String, role: String?, x: Int, y: Int, width: Int, height: Int): ConditionalLayoutBuilder {
        parent.addRule(
            PlaceFixedSlotRule(
                condition = condition,
                providerId = ResourceLocation(providerId),
                role = parentWhenParseSlotRole(role),
                x = x,
                y = y,
                width = width,
                height = height,
            )
        )
        return this
    }

    private fun parentWhenParseRole(raw: String?): PMJeiRequirementRole? {
        // Keep parsing logic in sync with LayoutBuilder#parseRole; duplicate is fine for ZenScript surface.
        if (raw == null) return null
        val s = raw.trim()
        if (s.isEmpty()) return null

        return when (s.uppercase(Locale.ROOT)) {
            "INPUT" -> PMJeiRequirementRole.INPUT
            "OUTPUT" -> PMJeiRequirementRole.OUTPUT
            "INPUT_PER_TICK", "INPUTPERTICK" -> PMJeiRequirementRole.INPUT_PER_TICK
            "OUTPUT_PER_TICK", "OUTPUTPERTICK" -> PMJeiRequirementRole.OUTPUT_PER_TICK
            "OTHER" -> PMJeiRequirementRole.OTHER
            "ANY", "*" -> null
            else -> null
        }
    }

    private fun parentWhenParseSlotRole(raw: String?): JeiSlotRole {
        if (raw == null) return JeiSlotRole.CATALYST
        val s = raw.trim()
        if (s.isEmpty()) return JeiSlotRole.CATALYST

        return when (s.uppercase(Locale.ROOT)) {
            "INPUT" -> JeiSlotRole.INPUT
            "OUTPUT" -> JeiSlotRole.OUTPUT
            "CATALYST" -> JeiSlotRole.CATALYST
            "OTHER", "ANY", "*" -> JeiSlotRole.CATALYST
            else -> JeiSlotRole.CATALYST
        }
    }

    private fun parentWhenMergePerTick(): Boolean {
        return parent.isMergePerTickRolesEnabled()
    }

    private fun parentCoerceDataMap(raw: java.util.Map<*, *>?): Map<String, Any> {
        // Keep coercion logic in sync with LayoutBuilder#coerceDataMap; duplicate is fine for ZenScript surface.
        if (raw == null) return emptyMap()

        val out = LinkedHashMap<String, Any>()
        for (entry in raw.entrySet()) {
            val key = entry.key as? String ?: continue
            val v = entry.value ?: continue
            out[key] = v
        }
        return out
    }
}
