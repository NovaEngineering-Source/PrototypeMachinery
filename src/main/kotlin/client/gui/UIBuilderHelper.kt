package github.kasuminova.prototypemachinery.client.gui

import com.cleanroommc.modularui.api.widget.IWidget
import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.value.sync.PanelSyncManager
import github.kasuminova.prototypemachinery.api.ui.definition.PanelDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import github.kasuminova.prototypemachinery.client.gui.builder.UIBindings
import github.kasuminova.prototypemachinery.client.gui.builder.UIBuildContext
import github.kasuminova.prototypemachinery.client.gui.builder.UITextures
import github.kasuminova.prototypemachinery.client.gui.builder.factory.DisplayWidgetFactory
import github.kasuminova.prototypemachinery.client.gui.builder.factory.InteractiveWidgetFactory
import github.kasuminova.prototypemachinery.client.gui.builder.factory.LayoutWidgetFactory
import github.kasuminova.prototypemachinery.client.gui.builder.factory.SlotWidgetFactory
import github.kasuminova.prototypemachinery.client.gui.builder.factory.UtilityWidgetFactory
import github.kasuminova.prototypemachinery.client.gui.builder.factory.WidgetFactoryRegistry
import github.kasuminova.prototypemachinery.common.block.entity.MachineBlockEntity

/**
 * Helper class to build ModularUI widgets from WidgetDefinition data classes.
 * 用于从 WidgetDefinition 数据类构建 ModularUI 组件的辅助类。
 */
public object UIBuilderHelper {

    private val textures: UITextures = UITextures()
    private val bindings: UIBindings = UIBindings()

    private val registry: WidgetFactoryRegistry = WidgetFactoryRegistry(
        listOf(
            LayoutWidgetFactory(),
            InteractiveWidgetFactory(),
            DisplayWidgetFactory(),
            SlotWidgetFactory(),
            UtilityWidgetFactory()
        )
    )

    /**
     * Build a ModularPanel from a PanelDefinition.
     * 从 PanelDefinition 构建 ModularPanel。
     */
    public fun buildPanel(
        def: PanelDefinition,
        syncManager: PanelSyncManager,
        machineTile: MachineBlockEntity
    ): ModularPanel {
        val ctx = UIBuildContext(syncManager, machineTile, textures, bindings)
        val panel = ModularPanel.defaultPanel("machine_panel")
            .size(def.width, def.height)

        val bgPath = ctx.textures.normalizeTexturePath(def.backgroundTexture)
        if (bgPath != null) {
            panel.background(ctx.textures.parseTexture(bgPath))
        }

        def.children.forEach { childDef ->
            val widget = buildWidget(childDef, ctx)
            if (widget != null) {
                panel.child(widget)
            }
        }

        return panel
    }

    /**
     * Build a widget from any WidgetDefinition.
     * 从任意 WidgetDefinition 构建组件。
     */
    public fun buildWidget(
        def: WidgetDefinition,
        syncManager: PanelSyncManager,
        machineTile: MachineBlockEntity
    ): IWidget? {
        val ctx = UIBuildContext(syncManager, machineTile, textures, bindings)
        return buildWidget(def, ctx)
    }

    private fun buildWidget(def: WidgetDefinition, ctx: UIBuildContext): IWidget? {
        return registry.buildWidget(def, ctx) { childDef ->
            buildWidget(childDef, ctx)
        }
    }
}
