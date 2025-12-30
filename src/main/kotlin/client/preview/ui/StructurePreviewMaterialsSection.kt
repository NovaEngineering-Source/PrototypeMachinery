package github.kasuminova.prototypemachinery.client.preview.ui

import com.cleanroommc.modularui.api.IThemeApi
import com.cleanroommc.modularui.widgets.ItemDisplayWidget
import com.cleanroommc.modularui.widgets.TransformWidget
import com.cleanroommc.modularui.widgets.layout.Column
import github.kasuminova.prototypemachinery.api.machine.structure.preview.AnyOfRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.BlockRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ExactBlockStateRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.StructurePreviewModel
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ui.StructurePreviewEntryStatus
import github.kasuminova.prototypemachinery.client.preview.ui.widget.ScissorGroupWidget
import github.kasuminova.prototypemachinery.client.util.ItemStackDisplayUtil
import net.minecraft.block.Block
import net.minecraft.init.Blocks
import net.minecraft.item.ItemStack

internal object StructurePreviewMaterialsSection {

    /**
     * Transparent click-catcher that can wrap a child widget.
     * Used to make display-only widgets (like ItemDisplayWidget) clickable without changing their rendering.
     */
    private class ClickableOverlay(
        private val onLeftClick: () -> Unit
    ) : com.cleanroommc.modularui.widget.SingleChildWidget<ClickableOverlay>(), com.cleanroommc.modularui.api.widget.Interactable {
        override fun onMousePressed(mouseButton: Int): com.cleanroommc.modularui.api.widget.Interactable.Result {
            if (mouseButton != 0) return com.cleanroommc.modularui.api.widget.Interactable.Result.ACCEPT
            onLeftClick.invoke()
            return com.cleanroommc.modularui.api.widget.Interactable.Result.SUCCESS
        }
    }

    fun build(
        root: Column,
        host: StructurePreviewUiHostConfig,
        model: StructurePreviewModel,
        rt: RootRuntime
    ): MaterialsUiParts {
        // 默认不打开材料界面。
        // When not pinned, materials UI is auto-shown only when a block is selected.
        val materialPinnedOpen = com.cleanroommc.modularui.value.BoolValue(false)
        val materialPreviewUiBg = StructurePreviewUiScreen.guiTex("base_material_preview_ui").asWidget().pos(29, 31).size(134, 163)
        val materialPreviewUiContent: ClearableColumn = run {
            val c = ClearableColumn()
            c.pos(33, 35)
            c.size(111, 160)
            c.childPadding(2)
            c.flex().coverChildrenWidth()
            c.flex().coverChildrenHeight()
            c
        }

        fun makeSlot(
            stack: ItemStack,
            displayAmount: Boolean,
            tooltipLines: () -> List<String>,
            onLeftClick: (() -> Unit)? = null
        ): com.cleanroommc.modularui.widget.Widget<*> {
            val slot = ItemDisplayWidget()
                .item(stack)
                .displayAmount(displayAmount)
                .size(18)
                // Avoid theme-provided slot background; we draw our own.
                .widgetTheme(IThemeApi.FALLBACK)
                .background(StructurePreviewUiScreen.ITEM_SLOT_BG)
                .disableHoverBackground()

            val attachTooltip: (com.cleanroommc.modularui.widget.Widget<*>) -> Unit = { ttWidget ->
                ttWidget.tooltipDynamic { tt ->
                    for (line in tooltipLines()) {
                        tt.addLine(line)
                    }
                }
                ttWidget.tooltipAutoUpdate(true)
            }

            return if (onLeftClick != null) {
                val overlay = ClickableOverlay(onLeftClick)
                    .size(18)
                    .child(slot)
                attachTooltip(overlay)
                overlay
            } else {
                attachTooltip(slot)
                slot
            }
        }

        fun stackForExact(req: ExactBlockStateRequirement, count: Int): ItemStack? {
            val block = Block.REGISTRY.getObject(req.blockId) ?: return null
            if (block == Blocks.AIR) return null
            return ItemStackDisplayUtil.stackForBlock(block, req.meta, count)
        }

        fun resolveAnyOfOption(anyOf: AnyOfRequirement): ExactBlockStateRequirement {
            val reqKey = anyOf.stableKey()
            val chosenKey = host.anyOfSelectionProvider(reqKey)
            return anyOf.options.firstOrNull { it.stableKey() == chosenKey } ?: anyOf.options.first()
        }

        fun displayStackForRequirement(req: BlockRequirement, count: Int): ItemStack? {
            return when (req) {
                is ExactBlockStateRequirement -> stackForExact(req, count)
                is AnyOfRequirement -> stackForExact(resolveAnyOfOption(req), count)
                else -> null
            }
        }

        fun computeDisplayBom(): List<Pair<BlockRequirement, Int>> {
            // When a block is selected, show only that position's requirement.
            val selected = rt.clickedRequirement
            if (selected != null) {
                return listOf(selected to 1)
            }

            if (host.materialsMode == StructurePreviewUiHostConfig.MaterialsMode.REMAINING && rt.statusSnapshot.isNotEmpty()) {
                data class Acc(var req: BlockRequirement, var count: Int)
                val byKey = LinkedHashMap<String, Acc>()
                for ((relPos, req) in model.blocks) {
                    val st = rt.statusSnapshot[relPos] ?: continue
                    if (st != StructurePreviewEntryStatus.MISSING && st != StructurePreviewEntryStatus.WRONG) continue
                    val key = req.stableKey()
                    val acc = byKey.getOrPut(key) { Acc(req, 0) }
                    acc.count++
                }
                return byKey.values
                    .filter { it.count > 0 }
                    .sortedByDescending { it.count }
                    .map { it.req to it.count }
            }

            return model.bom
                .asSequence()
                .map { it.requirement to it.count }
                .sortedByDescending { it.second }
                .toList()
        }

        // --- Left: replace preview (collapsible like top/bottom menus) ---
        // Spec note: treat this as a self-contained clipped component that slides out when component_switch is toggled.
        val leftReplaceClip = ScissorGroupWidget()
            .pos(9, 31)
            .size(18, 181)

        val leftReplaceContent: ClearableColumn = run {
            val c = ClearableColumn()
            c.pos(0, 0)
            c.size(18, 181)
            c
        }

        leftReplaceContent.child(StructurePreviewUiScreen.guiTex("left_replace_preview").asWidget().pos(0, 0).size(18, 181))

        val leftReplaceSlide = TransformWidget(leftReplaceContent)
            .pos(0, 0)
            .size(18, 181)
            .transform { stack ->
                // Auto-hide side bar when no selection; show it when user clicked any block.
                // Also respects the component_switch collapse animation.
                val switchT = rt.collapseNow.coerceIn(0.0f, 1.0f)
                val selectionT = rt.sidebarNow.coerceIn(0.0f, 1.0f)
                val t = kotlin.math.max(switchT, selectionT)
                // Slide out to the left within the clip window.
                stack.translate(-18.0f * t, 0.0f)
            }

        leftReplaceClip.child(leftReplaceSlide)
        root.child(leftReplaceClip)

        // --- Material preview UI (dynamic; toggled by bottom button) ---
        root.child(materialPreviewUiBg)
        root.child(materialPreviewUiContent)

        var lastMatKey: String? = null
        var lastReplaceKey: String? = null

        rt.rebuildMaterialsUi = {
            run {
                val bom = computeDisplayBom()
                    .asSequence()
                    .mapNotNull { (req, cnt) ->
                        val stack = displayStackForRequirement(req, cnt) ?: return@mapNotNull null
                        Triple(req, stack, cnt)
                    }
                    .take(24)
                    .toList()

                val key = bom.joinToString("|") { (_, s, _) ->
                    val rn = s.item.registryName?.toString() ?: s.item.javaClass.name
                    "$rn:${s.metadata}:${s.count}"
                }
                if (key == lastMatKey) return@run
                lastMatKey = key

                materialPreviewUiContent.clearChildrenSafe()
                for ((req, s, cnt) in bom) {
                    val isRemaining = host.materialsMode == StructurePreviewUiHostConfig.MaterialsMode.REMAINING
                    val status = rt.clickedPos?.let { pos -> rt.statusSnapshot[pos] }
                    val lines = mutableListOf<String>()

                    // Item name
                    val name = if (s.isEmpty) StructurePreviewUiScreen.tr("pm.preview.ui.empty") else s.displayName
                    lines.add(name)

                    // Requirement + count
                    if (rt.clickedRequirement != null) {
                        lines.add(StructurePreviewUiScreen.tr("pm.preview.ui.requirement", StructurePreviewUiScreen.formatRequirementShort(req)))
                        if (status != null) lines.add(StructurePreviewUiScreen.tr("pm.preview.ui.status", StructurePreviewUiScreen.entryStatusText(status)))
                        lines.add(StructurePreviewUiScreen.tr("pm.preview.ui.count", cnt))
                    } else {
                        lines.add(StructurePreviewUiScreen.tr(if (isRemaining) "pm.preview.ui.remaining" else "pm.preview.ui.required", cnt))
                        lines.add(StructurePreviewUiScreen.tr("pm.preview.ui.key", req.stableKey()))
                    }

                    if (req is AnyOfRequirement) {
                        lines.add(StructurePreviewUiScreen.tr("pm.preview.ui.replaceable_hint", req.options.size))
                    }

                    materialPreviewUiContent.child(
                        makeSlot(
                            stack = s,
                            displayAmount = true,
                            tooltipLines = { lines }
                        )
                    )
                }
            }
        }

        rt.rebuildReplaceUi = {
            run {
                val clicked = rt.clickedRequirement
                val anyOf = clicked as? AnyOfRequirement
                val key = clicked?.stableKey() ?: "<none>"
                if (key == lastReplaceKey) return@run
                lastReplaceKey = key

                leftReplaceContent.clearChildrenSafe()
                leftReplaceContent.child(StructurePreviewUiScreen.guiTex("left_replace_preview").asWidget().pos(0, 0).size(18, 181))

                if (clicked == null) {
                    leftReplaceContent.child(
                        makeSlot(
                            stack = ItemStack.EMPTY,
                            displayAmount = false,
                            tooltipLines = {
                                listOf(
                                    StructurePreviewUiScreen.tr("pm.preview.ui.no_selection.title"),
                                    StructurePreviewUiScreen.tr("pm.preview.ui.no_selection.hint")
                                )
                            }
                        ).pos(0, 0)
                    )
                    return@run
                }

                // Non-AnyOf requirement: show the required stack in the top slot only.
                if (anyOf == null) {
                    val stack = displayStackForRequirement(clicked, 1) ?: ItemStack.EMPTY
                    leftReplaceContent.child(
                        makeSlot(
                            stack = stack,
                            displayAmount = false,
                            tooltipLines = {
                                listOf(
                                    if (!stack.isEmpty) stack.displayName else StructurePreviewUiScreen.tr("pm.preview.ui.empty"),
                                    StructurePreviewUiScreen.tr("pm.preview.ui.requirement", StructurePreviewUiScreen.formatRequirementShort(clicked)),
                                    StructurePreviewUiScreen.tr("pm.preview.ui.key", clicked.stableKey())
                                )
                            }
                        ).pos(0, 0)
                    )
                    return@run
                }

                val reqKey = anyOf.stableKey()
                val selected = resolveAnyOfOption(anyOf)
                val selectedStack = stackForExact(selected, 1) ?: ItemStack.EMPTY

                leftReplaceContent.child(
                    makeSlot(
                        stack = selectedStack,
                        displayAmount = false,
                        tooltipLines = {
                            listOf(
                                StructurePreviewUiScreen.tr("pm.preview.ui.current_selection", selectedStack.displayName),
                                StructurePreviewUiScreen.tr("pm.preview.ui.anyof_choose_one", anyOf.options.size),
                                StructurePreviewUiScreen.tr("pm.preview.ui.key", reqKey)
                            )
                        }
                    ).pos(0, 0)
                )

                val optionsColumn = ClearableColumn()
                    .pos(0, 18)
                    .size(18, 163)
                    .apply { flex().coverChildrenWidth(); flex().coverChildrenHeight() }

                var added = 0
                for (opt in anyOf.options) {
                    if (opt.stableKey() == selected.stableKey()) continue
                    val st = stackForExact(opt, 1) ?: continue

                    val optKey = opt.stableKey()
                    val onClick = host.anyOfSelectionSetter?.let { setter ->
                        { setter.invoke(reqKey, optKey) }
                    }

                    optionsColumn.child(
                        makeSlot(
                            stack = st,
                            displayAmount = false,
                            tooltipLines = {
                                listOf(
                                    StructurePreviewUiScreen.tr("pm.preview.ui.candidate", st.displayName),
                                    if (onClick != null) StructurePreviewUiScreen.tr("pm.preview.ui.click_to_select") else StructurePreviewUiScreen.tr("pm.preview.ui.read_only"),
                                    StructurePreviewUiScreen.tr("pm.preview.ui.key", optKey)
                                )
                            },
                            onLeftClick = onClick
                        )
                    )
                    added++
                    if (added >= 9) break
                }

                leftReplaceContent.child(optionsColumn)
            }
        }

        // Initial build.
        rt.rebuildMaterialsUi?.invoke()
        rt.rebuildReplaceUi?.invoke()

        return MaterialsUiParts(
            materialPinnedOpen = materialPinnedOpen,
            materialPreviewUiBg = materialPreviewUiBg,
            materialPreviewUiContent = materialPreviewUiContent
        )
    }
}
