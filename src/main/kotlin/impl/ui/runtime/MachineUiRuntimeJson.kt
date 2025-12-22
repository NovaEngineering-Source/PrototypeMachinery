package github.kasuminova.prototypemachinery.impl.ui.runtime

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import github.kasuminova.prototypemachinery.api.ui.definition.ButtonDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.ColumnDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.ConditionalDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.GridDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.ImageDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.ItemSlotGroupDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.PanelDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.PlayerInventoryDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.ProgressBarDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.RowDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.ScrollContainerDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.SliderDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.TabContainerDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.TabDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.TextDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.TextFieldDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.ToggleButtonDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition

internal object MachineUiRuntimeJson {

    internal fun parsePanelDefinition(json: String): PanelDefinition? {
        val root = runCatching { JsonParser().parse(json) }.getOrNull() as? JsonObject ?: return null
        val canvas = root.obj("canvas") ?: return null
        val canvasW = canvas.int("width") ?: return null
        val canvasH = canvas.int("height") ?: return null

        val options = root.obj("options")
        val activeBackground = options?.str("activeBackground")?.trim()?.takeIf { it.isNotEmpty() }
        val activeTabId = options?.str("activeTabId")?.trim()?.takeIf { it.isNotEmpty() }

        val backgroundA = options?.obj("backgroundA")?.str("texturePath")?.trim()?.takeIf { it.isNotEmpty() }
        val backgroundB = options?.obj("backgroundB")?.str("texturePath")?.trim()?.takeIf { it.isNotEmpty() }

        val widgets = root.arr("widgets")?.mapNotNull { parseWidget(it, allowTabTag = true, depth = 0) }.orEmpty()

        val usedTabIds = widgets.mapNotNull { (it as? RuntimeTaggedWidget)?.tabId?.trim()?.takeIf(String::isNotEmpty) }.toSet()

        val tabsFromOptions = options?.arr("tabs")?.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o.str("id")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val label = o.str("label")?.trim()?.takeIf { it.isNotEmpty() }
            val tex = o.str("texturePath")?.trim()?.takeIf { it.isNotEmpty() }
            RuntimeTab(id = id, label = label, texturePath = tex)
        }.orEmpty()

        val tabs = if (tabsFromOptions.isNotEmpty()) {
            tabsFromOptions
        } else {
            // Legacy A/B: only keep tabs that are actually referenced, unless needed as fallback.
            val legacy = ArrayList<RuntimeTab>(2)
            if (backgroundA != null || usedTabIds.contains("A") || (backgroundB == null && !usedTabIds.contains("B"))) {
                legacy.add(RuntimeTab("A", "Main", backgroundA))
            }
            if (backgroundB != null || usedTabIds.contains("B")) {
                legacy.add(RuntimeTab("B", "Extension", backgroundB))
            }
            if (legacy.isEmpty()) {
                legacy.add(RuntimeTab("A", "Main", backgroundA))
            }
            legacy
        }

        val initial = activeTabId ?: activeBackground ?: tabs.firstOrNull()?.id ?: "A"
        val orderedTabs = tabs.sortedWith(compareByDescending<RuntimeTab> { it.id == initial }.thenBy { it.id })

        // Partition by tab
        val globalWidgets = ArrayList<WidgetDefinition>()
        val byTab = HashMap<String, MutableList<WidgetDefinition>>()
        for (w in widgets) {
            val tabId = (w as? RuntimeTaggedWidget)?.tabId?.trim().orEmpty()
            if (tabId.isEmpty()) {
                globalWidgets.add(w)
            } else {
                byTab.computeIfAbsent(tabId) { ArrayList() }.add(w)
            }
        }

        // Tab container content panels
        val tabDefs = orderedTabs.map { t ->
            val bg = normalizePmUiTextureArgForRuntime(t.texturePath)
            val children = byTab[t.id]?.toList() ?: emptyList()
            val content = PanelDefinition(
                x = 0,
                y = 0,
                width = canvasW,
                height = canvasH,
                backgroundTexture = bg,
                children = children
            )
            TabDefinition(
                x = 0,
                y = 0,
                width = 0,
                height = 0,
                tabKey = t.id,
                title = t.label ?: t.id,
                icon = null,
                content = content
            )
        }

        val tabContainer = TabContainerDefinition(
            x = 0,
            y = 0,
            width = canvasW,
            height = canvasH,
            tabPosition = "LEFT",
            tabs = tabDefs
        )

        // Root panel: add tab container first (background), then globals on top.
        return PanelDefinition(
            x = 0,
            y = 0,
            width = canvasW,
            height = canvasH,
            backgroundTexture = null,
            children = listOf(tabContainer) + globalWidgets
        )
    }

    private data class RuntimeTab(val id: String, val label: String?, val texturePath: String?)

    /**
     * Marker interface so we can keep the tabId when partitioning.
     * The runtime JSON interpreter uses wrapper implementations below.
     */
    private interface RuntimeTaggedWidget : WidgetDefinition {
        val tabId: String?
    }

    private const val MAX_NESTING_DEPTH: Int = 32

    private fun parseWidget(el: JsonElement, allowTabTag: Boolean, depth: Int): WidgetDefinition? {
        if (depth > MAX_NESTING_DEPTH) return null
        val o = el as? JsonObject ?: return null
        val type = o.str("type")?.trim()?.lowercase() ?: return null

        val tabId = if (allowTabTag) o.str("tabId")?.trim() else null
        val visibleIf = o.str("visibleIf")?.trim()?.takeIf { it.isNotEmpty() }
        val enabledIf = o.str("enabledIf")?.trim()?.takeIf { it.isNotEmpty() }

        val x = o.int("x") ?: return null
        val y = o.int("y") ?: return null

        val base: WidgetDefinition? = when (type) {
            // ============================================================================
            // Container / layout widgets (recursive)
            // ============================================================================

            "panel" -> {
                val w = o.int("w") ?: o.int("width") ?: return null
                val h = o.int("h") ?: o.int("height") ?: return null
                val bg = normalizePmUiTextureArgForRuntime(
                    o.str("backgroundTexture")
                        ?: o.str("backgroundTexturePath")
                        ?: o.str("background")
                        ?: o.str("texturePath")
                )
                val children = o.arr("children")?.mapNotNull { parseWidget(it, allowTabTag = false, depth = depth + 1) }.orEmpty()
                RuntimePanelDef(tabId, PanelDefinition(x = x, y = y, width = w, height = h, backgroundTexture = bg, children = children))
            }

            "row" -> {
                val w = o.int("w") ?: o.int("width") ?: 0
                val h = o.int("h") ?: o.int("height") ?: 0
                val spacing = (o.int("spacing") ?: 0).coerceAtLeast(0)
                val children = o.arr("children")?.mapNotNull { parseWidget(it, allowTabTag = false, depth = depth + 1) }.orEmpty()
                RuntimeRowDef(tabId, RowDefinition(x = x, y = y, width = w, height = h, spacing = spacing, children = children))
            }

            "column" -> {
                val w = o.int("w") ?: o.int("width") ?: 0
                val h = o.int("h") ?: o.int("height") ?: 0
                val spacing = (o.int("spacing") ?: 0).coerceAtLeast(0)
                val children = o.arr("children")?.mapNotNull { parseWidget(it, allowTabTag = false, depth = depth + 1) }.orEmpty()
                RuntimeColumnDef(tabId, ColumnDefinition(x = x, y = y, width = w, height = h, spacing = spacing, children = children))
            }

            "grid" -> {
                val w = o.int("w") ?: o.int("width") ?: 0
                val h = o.int("h") ?: o.int("height") ?: 0
                val columns = (o.int("columns") ?: o.int("cols") ?: 1).coerceAtLeast(1)
                val rowSpacing = (o.int("rowSpacing") ?: o.int("row_spacing") ?: 0).coerceAtLeast(0)
                val columnSpacing = (o.int("columnSpacing") ?: o.int("column_spacing") ?: 0).coerceAtLeast(0)
                val children = o.arr("children")?.mapNotNull { parseWidget(it, allowTabTag = false, depth = depth + 1) }.orEmpty()
                RuntimeGridDef(
                    tabId,
                    GridDefinition(
                        x = x,
                        y = y,
                        width = w,
                        height = h,
                        columns = columns,
                        rowSpacing = rowSpacing,
                        columnSpacing = columnSpacing,
                        children = children
                    )
                )
            }

            "scroll_container", "scrollcontainer", "scroll", "scrollarea" -> {
                val w = o.int("w") ?: o.int("width") ?: return null
                val h = o.int("h") ?: o.int("height") ?: return null

                val dir = o.str("direction")?.trim()?.lowercase()
                val scrollX = o.bool("scrollX")
                    ?: when (dir) {
                        "horizontal", "x" -> true
                        "both", "xy" -> true
                        else -> false
                    }
                val scrollY = o.bool("scrollY")
                    ?: when (dir) {
                        "vertical", "y" -> true
                        "both", "xy" -> true
                        else -> true
                    }

                val scrollSpeed = (o.int("scrollSpeed") ?: o.int("scroll_speed") ?: 30).coerceAtLeast(1)
                val cancelScrollEdge = o.bool("cancelScrollEdge") ?: o.bool("cancel_scroll_edge") ?: true

                val scrollBarOnStartX = o.bool("scrollBarOnStartX") ?: o.bool("scrollbarOnStartX") ?: o.bool("scroll_bar_on_start_x") ?: false
                val scrollBarOnStartY = o.bool("scrollBarOnStartY") ?: o.bool("scrollbarOnStartY") ?: o.bool("scroll_bar_on_start_y") ?: false

                val scrollBarThicknessX = o.int("scrollBarThicknessX") ?: o.int("scrollbarThicknessX") ?: o.int("scroll_bar_thickness_x") ?: -1
                val scrollBarThicknessY = o.int("scrollBarThicknessY") ?: o.int("scrollbarThicknessY") ?: o.int("scroll_bar_thickness_y") ?: -1

                val children = o.arr("children")?.mapNotNull { parseWidget(it, allowTabTag = false, depth = depth + 1) }.orEmpty()

                RuntimeScrollContainerDef(
                    tabId,
                    ScrollContainerDefinition(
                        x = x,
                        y = y,
                        width = w,
                        height = h,
                        scrollX = scrollX,
                        scrollY = scrollY,
                        scrollBarOnStartX = scrollBarOnStartX,
                        scrollBarOnStartY = scrollBarOnStartY,
                        scrollBarThicknessX = scrollBarThicknessX,
                        scrollBarThicknessY = scrollBarThicknessY,
                        scrollSpeed = scrollSpeed,
                        cancelScrollEdge = cancelScrollEdge,
                        children = children
                    )
                )
            }

            // ============================================================================
            // Leaf widgets
            // ============================================================================

            "text" -> {
                val w = o.int("w") ?: return null
                val h = o.int("h") ?: return null
                val text = o.str("text") ?: ""
                val color = parseCssHexColorToInt(o.str("color")) ?: 0xFFFFFF
                val shadow = o.bool("shadow") ?: true
                val align = when (o.str("align")?.trim()?.lowercase()) {
                    "center" -> "CENTER"
                    "right" -> "RIGHT"
                    else -> "LEFT"
                }
                RuntimeTextDef(tabId, TextDefinition(x, y, w, h, text, color, shadow, align))
            }

            "progress" -> {
                val w = o.int("w") ?: return null
                val h = o.int("h") ?: return null
                val dir = when (o.str("direction")?.trim()?.lowercase()) {
                    "left" -> "LEFT"
                    "up" -> "UP"
                    "down" -> "DOWN"
                    else -> "RIGHT"
                }
                val tex = normalizePmUiTextureArgForRuntime(o.str("runTexturePath") ?: o.str("baseTexturePath"))
                val progressKey = o.str("progressKey")?.trim()?.takeIf { it.isNotEmpty() }
                val tooltipTemplate = o.str("tooltipTemplate")?.takeIf { it.isNotBlank() }
                RuntimeProgressDef(tabId, ProgressBarDefinition(x, y, w, h, dir, tex, progressKey, tooltipTemplate))
            }

            "slotgrid" -> {
                val cols = o.int("cols") ?: return null
                val rows = o.int("rows") ?: return null
                val slotKey = o.str("slotKey")?.trim()?.takeIf { it.isNotEmpty() } ?: "default"
                val startIndex = (o.int("startIndex") ?: 0).coerceAtLeast(0)
                val canInsert = o.bool("canInsert") ?: true
                val canExtract = o.bool("canExtract") ?: true
                // width/height are implied by cols/rows in existing placeholder implementation.
                RuntimeSlotGridDef(tabId, ItemSlotGroupDefinition(x, y, 0, 0, slotKey, startIndex, cols, rows, canInsert, canExtract))
            }

            // The following widget types are not yet exported by the WebEditor IR schema,
            // but are supported here so the runtime JSON can be extended without requiring
            // a mod update.

            "button" -> {
                val w = o.int("w") ?: o.int("width") ?: 27
                val h = o.int("h") ?: o.int("height") ?: 15
                val text = o.str("text")?.takeIf { it.isNotBlank() }
                val actionKey = o.str("actionKey")?.trim()?.takeIf { it.isNotEmpty() }
                val skin = o.str("skin")?.trim()?.takeIf { it.isNotEmpty() }
                RuntimeButtonDef(tabId, ButtonDefinition(x, y, w, h, skin, text, actionKey))
            }

            "toggle", "togglebutton", "toggle_button" -> {
                val w = o.int("w") ?: o.int("width") ?: 27
                val h = o.int("h") ?: o.int("height") ?: 15
                val skin = o.str("skin")?.trim()?.takeIf { it.isNotEmpty() }
                val stateKey = o.str("stateKey")?.trim()?.takeIf { it.isNotEmpty() }
                val textOn = o.str("textOn")?.takeIf { it.isNotBlank() }
                val textOff = o.str("textOff")?.takeIf { it.isNotBlank() }
                val textureOn = normalizePmUiTextureArgForRuntime(o.str("textureOn") ?: o.str("textureOnPath") ?: o.str("textureOnTexture"))
                val textureOff = normalizePmUiTextureArgForRuntime(o.str("textureOff") ?: o.str("textureOffPath") ?: o.str("textureOffTexture"))
                RuntimeToggleDef(tabId, ToggleButtonDefinition(x, y, w, h, skin, stateKey, textOn, textOff, textureOn, textureOff))
            }

            "slider" -> {
                val w = o.int("w") ?: o.int("width") ?: 100
                val h = o.int("h") ?: o.int("height") ?: 14

                val min = o.dbl("min") ?: 0.0
                val max = o.dbl("max") ?: 100.0
                val step = o.dbl("step") ?: 1.0
                val valueKey = o.str("valueKey")?.trim()?.takeIf { it.isNotEmpty() }
                val horizontal = o.bool("horizontal") ?: true
                val skin = o.str("skin")?.trim()?.takeIf { it.isNotEmpty() }

                RuntimeSliderDef(tabId, SliderDefinition(x, y, w, h, skin, min, max, step, valueKey, horizontal))
            }

            "text_field", "textfield", "input_box", "inputbox" -> {
                val w = o.int("w") ?: o.int("width") ?: 56
                val h = o.int("h") ?: o.int("height") ?: 13
                val valueKey = o.str("valueKey")?.trim()?.takeIf { it.isNotEmpty() }
                val inputType = o.str("inputType")?.trim()?.lowercase() ?: "string"
                val minLong = o.lng("minLong")
                val maxLong = o.lng("maxLong")
                val skin = o.str("skin")?.trim()?.takeIf { it.isNotEmpty() }
                RuntimeTextFieldDef(
                    tabId,
                    TextFieldDefinition(
                        x = x,
                        y = y,
                        width = w,
                        height = h,
                        valueKey = valueKey,
                        inputType = inputType,
                        minLong = minLong,
                        maxLong = maxLong,
                        skin = skin
                    )
                )
            }

            "image" -> {
                val w = o.int("w") ?: o.int("width") ?: return null
                val h = o.int("h") ?: o.int("height") ?: return null
                val texture = normalizePmUiTextureArgForRuntime(
                    o.str("texture")
                        ?: o.str("texturePath")
                        ?: o.str("path")
                ) ?: return null
                RuntimeImageDef(tabId, ImageDefinition(x, y, w, h, texture))
            }

            "playerinventory", "player_inventory" -> {
                val w = o.int("w") ?: o.int("width") ?: 162
                val h = o.int("h") ?: o.int("height") ?: 76
                RuntimePlayerInvDef(tabId, PlayerInventoryDefinition(x, y, w, h))
            }

            else -> null
        }

        return if (base != null && (visibleIf != null || enabledIf != null)) {
            RuntimeConditionalDef(
                tabId = tabId,
                inner = ConditionalDefinition(content = base, visibleIf = visibleIf, enabledIf = enabledIf)
            )
        } else {
            base
        }
    }

    private data class RuntimeTextDef(override val tabId: String?, val inner: TextDefinition) : RuntimeTaggedWidget, WidgetDefinition by inner
    private data class RuntimeProgressDef(override val tabId: String?, val inner: ProgressBarDefinition) : RuntimeTaggedWidget, WidgetDefinition by inner
    private data class RuntimeSlotGridDef(override val tabId: String?, val inner: ItemSlotGroupDefinition) : RuntimeTaggedWidget, WidgetDefinition by inner
    private data class RuntimePanelDef(override val tabId: String?, val inner: PanelDefinition) : RuntimeTaggedWidget, WidgetDefinition by inner
    private data class RuntimeRowDef(override val tabId: String?, val inner: RowDefinition) : RuntimeTaggedWidget, WidgetDefinition by inner
    private data class RuntimeColumnDef(override val tabId: String?, val inner: ColumnDefinition) : RuntimeTaggedWidget, WidgetDefinition by inner
    private data class RuntimeGridDef(override val tabId: String?, val inner: GridDefinition) : RuntimeTaggedWidget, WidgetDefinition by inner
    private data class RuntimeScrollContainerDef(override val tabId: String?, val inner: ScrollContainerDefinition) : RuntimeTaggedWidget, WidgetDefinition by inner
    private data class RuntimeButtonDef(override val tabId: String?, val inner: ButtonDefinition) : RuntimeTaggedWidget, WidgetDefinition by inner
    private data class RuntimeToggleDef(override val tabId: String?, val inner: ToggleButtonDefinition) : RuntimeTaggedWidget, WidgetDefinition by inner
    private data class RuntimeSliderDef(override val tabId: String?, val inner: SliderDefinition) : RuntimeTaggedWidget, WidgetDefinition by inner
    private data class RuntimeTextFieldDef(override val tabId: String?, val inner: TextFieldDefinition) : RuntimeTaggedWidget, WidgetDefinition by inner
    private data class RuntimeImageDef(override val tabId: String?, val inner: ImageDefinition) : RuntimeTaggedWidget, WidgetDefinition by inner
    private data class RuntimePlayerInvDef(override val tabId: String?, val inner: PlayerInventoryDefinition) : RuntimeTaggedWidget, WidgetDefinition by inner
    private data class RuntimeConditionalDef(override val tabId: String?, val inner: ConditionalDefinition) : RuntimeTaggedWidget, WidgetDefinition by inner

    private fun parseCssHexColorToInt(color: String?): Int? {
        val raw = color?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val m = Regex("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$").matchEntire(raw) ?: return null
        val hex = m.groupValues[1]
        val rgb = if (hex.length == 8) hex.substring(2) else hex
        return rgb.toInt(16)
    }

    /**
     * Normalize editor/runtime texture path into ModularUI resource location argument.
     *
     * Examples:
     * - "gui/gui_controller_a.png" -> "prototypemachinery:gui/gui_controller_a"
     * - "prototypemachinery:textures/gui/foo.png" -> "prototypemachinery:gui/foo"
     * - "mymod:gui/bar" -> "mymod:gui/bar"
     */
    private fun normalizePmUiTextureArgForRuntime(input: String?): String? {
        val raw = input?.trim().orEmpty()
        if (raw.isEmpty()) return null

        var cleaned = raw
        while (cleaned.startsWith('/')) cleaned = cleaned.substring(1)

        val colon = cleaned.indexOf(':')
        if (colon >= 0) {
            val ns = cleaned.substring(0, colon).trim()
            var path = cleaned.substring(colon + 1).trim()
            while (path.startsWith('/')) path = path.substring(1)
            if (path.startsWith("textures/")) path = path.removePrefix("textures/")
            if (path.endsWith(".png", ignoreCase = true)) path = path.removeSuffix(".png")
            return "$ns:$path"
        }

        var path = cleaned
        if (path.startsWith("textures/")) path = path.removePrefix("textures/")
        if (path.endsWith(".png", ignoreCase = true)) path = path.removeSuffix(".png")
        return "prototypemachinery:$path"
    }
}

private fun JsonObject.obj(name: String): JsonObject? = get(name) as? JsonObject
private fun JsonObject.arr(name: String): List<JsonElement>? = (get(name) as? com.google.gson.JsonArray)?.toList()
private fun JsonObject.str(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive }?.asString
private fun JsonObject.int(name: String): Int? = get(name)?.takeIf { it.isJsonPrimitive }?.asInt
private fun JsonObject.bool(name: String): Boolean? = get(name)?.takeIf { it.isJsonPrimitive }?.asBoolean
private fun JsonObject.dbl(name: String): Double? = get(name)?.takeIf { it.isJsonPrimitive }?.asDouble
private fun JsonObject.lng(name: String): Long? = get(name)?.takeIf { it.isJsonPrimitive }?.asLong
