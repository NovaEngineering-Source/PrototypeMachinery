package github.kasuminova.prototypemachinery.impl.machine.component.ui

import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.utils.Alignment
import com.cleanroommc.modularui.value.sync.PanelSyncManager
import com.cleanroommc.modularui.widgets.TextWidget
import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType
import github.kasuminova.prototypemachinery.api.machine.component.ui.UIProviderComponent
import github.kasuminova.prototypemachinery.api.machine.component.ui.UIProviderComponentType
import github.kasuminova.prototypemachinery.api.ui.definition.PanelDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import github.kasuminova.prototypemachinery.client.gui.UIBuilderHelper
import net.minecraft.util.ResourceLocation

/**
 * # DefinitionBasedUIProvider - Widget Definition Based UI Provider
 * # DefinitionBasedUIProvider - 基于 Widget 定义的 UI 提供者
 *
 * A UIProviderComponent implementation that builds UI from WidgetDefinition.
 * This is typically used when UI is defined via ZenScript.
 *
 * 从 WidgetDefinition 构建 UI 的 UIProviderComponent 实现。
 * 这通常用于通过 ZenScript 定义 UI 的情况。
 *
 * @param owner The machine instance that owns this component
 * @param definition The widget definition to build UI from
 * @param componentType The component type for this provider
 */
public class DefinitionBasedUIProvider(
    override val owner: MachineInstance,
    private val definition: WidgetDefinition,
    private val componentType: UIProviderComponentType
) : UIProviderComponent {

    override val type: MachineComponentType<*> = componentType
    override val provider: Any? = null

    override fun buildPanel(syncManager: PanelSyncManager): ModularPanel {
        return if (definition is PanelDefinition) {
            UIBuilderHelper.buildPanel(
                definition,
                syncManager,
                owner.blockEntity as github.kasuminova.prototypemachinery.common.block.entity.MachineBlockEntity
            )
        } else {
            // Wrap non-panel definition in a default panel
            val defaultPanel = ModularPanel.defaultPanel("machine_panel")
                .size(176, 166)

            val widget = UIBuilderHelper.buildWidget(
                definition,
                syncManager,
                owner.blockEntity as github.kasuminova.prototypemachinery.common.block.entity.MachineBlockEntity
            )
            if (widget != null) {
                defaultPanel.child(widget)
            }

            defaultPanel
        }
    }

}

/**
 * # FallbackUIProvider - Default Fallback UI Provider
 * # FallbackUIProvider - 默认备用 UI 提供者
 *
 * Provides a simple fallback UI when no custom UI is defined.
 * 当没有定义自定义 UI 时提供简单的备用 UI。
 */
public class FallbackUIProvider(
    override val owner: MachineInstance
) : UIProviderComponent {

    override val type: MachineComponentType<*> = FallbackUIProviderType
    override val provider: Any? = null

    override fun buildPanel(syncManager: PanelSyncManager): ModularPanel {
        return ModularPanel.defaultPanel("machine_panel")
            .size(176, 166)
            .child(
                TextWidget("Machine: ${owner.type.name}")
                    .pos(8, 6)
                    .color(0x404040)
                    .shadow(false)
            )
            .child(
                TextWidget("No custom UI defined")
                    .pos(88, 80)
                    .alignment(Alignment.Center)
                    .color(0x888888)
            )
    }
}

/**
 * Default fallback UI provider component type.
 * 默认备用 UI 提供者组件类型。
 */
public object FallbackUIProviderType : UIProviderComponentType {
    override val id: ResourceLocation = ResourceLocation("prototypemachinery", "fallback_ui")

    override fun createComponent(machine: MachineInstance): UIProviderComponent {
        return FallbackUIProvider(machine)
    }
}
