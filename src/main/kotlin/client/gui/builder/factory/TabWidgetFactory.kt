package github.kasuminova.prototypemachinery.client.gui.builder.factory

import com.cleanroommc.modularui.api.ITheme
import com.cleanroommc.modularui.api.drawable.IDrawable
import com.cleanroommc.modularui.api.widget.IWidget
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import com.cleanroommc.modularui.utils.Alignment
import com.cleanroommc.modularui.widget.ParentWidget
import com.cleanroommc.modularui.widgets.ButtonWidget
import com.cleanroommc.modularui.widgets.PageButton
import com.cleanroommc.modularui.widgets.PagedWidget
import com.cleanroommc.modularui.widgets.TextWidget
import github.kasuminova.prototypemachinery.api.ui.definition.TabContainerDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.TabDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import github.kasuminova.prototypemachinery.client.gui.builder.UIBuildContext

/**
 * Minimal runtime tab container.
 *
 * This is intentionally simple and primarily aimed at supporting runtime JSON interpreter output.
 * Tabs are client-side only: switching does not require server sync.
 */
public class TabWidgetFactory : WidgetFactory {

    private companion object {
        private const val DEFAULT_TAB_BAR_W = 22
        private const val DEFAULT_TAB_BTN_W = 21
        private const val DEFAULT_TAB_BTN_H = 22
        private const val DEFAULT_TAB_GAP = 2
    }

    override fun build(def: WidgetDefinition, ctx: UIBuildContext, buildChild: (WidgetDefinition) -> IWidget?): IWidget? {
        if (def !is TabContainerDefinition) return null

        val tabs = def.tabs
        if (tabs.isEmpty()) return null

        val root = ParentWidget()
            .pos(def.x, def.y)
            .size(def.width, def.height)

        val pos = def.tabPosition.trim().uppercase()

        // DefaultMachineUI-like behavior (LEFT tabs): use PagedWidget + PageButton.
        // This also ensures inactive pages are not rendered.
        if (pos == "LEFT") {
            val controller = PagedWidget.Controller()
            val paged = PagedWidget()
                .pos(0, 0)
                .size(def.width, def.height)
                .controller(controller)

            tabs.forEach { t ->
                // Keep page indices aligned with tab indices.
                // If a tab content fails to build, fall back to an empty placeholder page.
                val page = buildChild(t.content)
                    ?: ParentWidget().pos(0, 0).size(def.width, def.height)
                paged.addPage(page)
            }

            // If no pages were built, bail out.
            if (paged.pages.isEmpty()) return null

            // Tabs bar (icons)
            val tabBar = ParentWidget()
                // Match DefaultMachineUI: tab bar lives inside the panel at x=0.
                // The controller background textures already reserve this strip.
                .pos(0, 0)
                .size(DEFAULT_TAB_BAR_W, def.height)

            tabs.forEachIndexed { idx, t ->
                // UX tweak: lift the second tab button ("Components") by 2px to match editor/layout expectations.
                val baseY = idx * (DEFAULT_TAB_BTN_H + DEFAULT_TAB_GAP)
                val y = if (idx == 1) baseY - 2 else baseY
                tabBar.child(
                    buildDefaultTabIconButton(
                        ctx,
                        idx,
                        controller,
                        x = 0,
                        y = y,
                        tabTitle = t.title
                    )
                )
            }

            // Background switching: match DefaultMachineUI A/B controller backgrounds if the root is a panel.
            // Note: parent panel background changes are handled elsewhere; this is best-effort.
            paged.onPageChange { _ ->
                // no-op here; actual panel background is commonly driven by per-page panel backgrounds.
            }

            root.child(paged)
            root.child(tabBar)
            return root
        }

        // Fallback: simple buttons (legacy).
        var activeIndex = 0
        val contentWidgets = ArrayList<IWidget?>(tabs.size)
        tabs.forEach { t ->
            val child = buildChild(t.content)
            root.child(child)
            contentWidgets.add(child)
        }

        fun applyActive() {
            contentWidgets.forEachIndexed { idx, w ->
                if (w != null) {
                    val next = idx == activeIndex
                    if (w.isEnabled != next) w.isEnabled = next
                }
            }
        }
        applyActive()

        tabs.forEachIndexed { idx, t ->
            root.child(buildTabButton(def, ctx, t, idx) { clicked ->
                if (clicked != activeIndex) {
                    activeIndex = clicked
                    applyActive()
                }
            })
        }

        root.onUpdateListener { applyActive() }
        return root
    }

    private fun buildDefaultTabIconButton(
        ctx: UIBuildContext,
        index: Int,
        controller: PagedWidget.Controller,
        x: Int,
        y: Int,
        tabTitle: String
    ): IWidget {
        val inactive: IDrawable
        val hover: IDrawable
        val active: IDrawable

        // Match DefaultMachineUI tab textures for first two tabs.
        if (index == 0) {
            inactive = ctx.textures.defaultTab1Inactive
            hover = ctx.textures.defaultTab1Hover
            active = ctx.textures.defaultTab1Active
        } else if (index == 1) {
            inactive = ctx.textures.defaultTab2Inactive
            hover = ctx.textures.defaultTab2Hover
            active = ctx.textures.defaultTab2Active
        } else {
            // Fallback: use default button textures
            inactive = ctx.textures.defaultButtonNormal
            hover = ctx.textures.defaultButtonHover
            active = ctx.textures.defaultButtonPressed
        }

        val btn = object : PageButton(index, controller) {
            override fun getCurrentBackground(theme: ITheme, widgetTheme: WidgetThemeEntry<*>): IDrawable? {
                if (isActive) return active
                if (isHovering) return hover
                return inactive
            }
        }

        btn.pos(x, y)
        btn.size(DEFAULT_TAB_BTN_W, DEFAULT_TAB_BTN_H)

        // Avoid rendering long titles inside the narrow tab bar.
        // Use tooltip instead (optional; safe no-op if tooltip isn't shown).
        if (tabTitle.isNotBlank()) {
            btn.addTooltipLine(tabTitle)
        }

        return btn
    }

    private fun buildTabButton(
        container: TabContainerDefinition,
        ctx: UIBuildContext,
        tab: TabDefinition,
        index: Int,
        onClickIndex: (Int) -> Unit
    ): IWidget {
        val label = tab.title
        val button = ButtonWidget()

        // Default look from states.png
        button.background(ctx.textures.defaultButtonNormal)
        button.hoverBackground(ctx.textures.defaultButtonHover)

        val pos = container.tabPosition.trim().uppercase()
        val x: Int
        val y: Int
        val w: Int
        val h: Int

        when (pos) {
            "TOP" -> {
                w = DEFAULT_TAB_BTN_W
                h = DEFAULT_TAB_BTN_H
                x = index * (w + DEFAULT_TAB_GAP)
                y = 0
            }

            "BOTTOM" -> {
                w = DEFAULT_TAB_BTN_W
                h = DEFAULT_TAB_BTN_H
                x = index * (w + DEFAULT_TAB_GAP)
                y = (container.height - h).coerceAtLeast(0)
            }

            "RIGHT" -> {
                w = DEFAULT_TAB_BTN_W
                h = DEFAULT_TAB_BTN_H
                x = (container.width - w).coerceAtLeast(0)
                y = index * (h + DEFAULT_TAB_GAP)
            }

            else -> { // LEFT
                w = DEFAULT_TAB_BTN_W
                h = DEFAULT_TAB_BTN_H
                x = 0
                y = index * (h + DEFAULT_TAB_GAP)
            }
        }

        button.pos(x, y)
        button.size(w, h)

        if (label.isNotBlank()) {
            button.child(TextWidget(label).alignment(Alignment.Center))
        }

        button.onMousePressed { _: Int ->
            onClickIndex(index)
            true
        }

        return button
    }
}
