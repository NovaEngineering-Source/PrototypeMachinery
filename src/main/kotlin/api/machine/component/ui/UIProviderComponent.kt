package github.kasuminova.prototypemachinery.api.machine.component.ui

import com.cleanroommc.modularui.api.widget.IWidget
import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.value.sync.PanelSyncManager
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent

/**
 * # UIProviderComponent - Machine UI Provider Component
 * # UIProviderComponent - 机器 UI 提供者组件
 *
 * A component that provides the UI for a machine. This decouples UI definition from MachineType,
 * allowing for dynamic UI construction and giving Java/Kotlin developers full control over
 * ModularUI widget creation.
 *
 * 为机器提供 UI 的组件。这将 UI 定义与 MachineType 解耦，
 * 允许动态 UI 构建，并给予 Java/Kotlin 开发者对 ModularUI 组件创建的完全控制。
 *
 * ## Use Cases / 使用场景
 *
 * 1. **ZenScript Defined UI**: UI defined via ZenScript builders
 *    **ZenScript 定义的 UI**: 通过 ZenScript 构建器定义的 UI
 *
 * 2. **Java/Kotlin Native UI**: UI built directly with ModularUI API
 *    **Java/Kotlin 原生 UI**: 直接使用 ModularUI API 构建的 UI
 *
 * 3. **Dynamic UI**: UI that changes based on machine state
 *    **动态 UI**: 根据机器状态变化的 UI
 *
 * ## Example / 示例
 *
 * ```kotlin
 * class MyUIProvider(override val owner: MachineInstance) : UIProviderComponent {
 *     override val type = MyUIProviderType
 *     override val provider = null
 *
 *     override fun buildPanel(syncManager: PanelSyncManager): ModularPanel {
 *         return ModularPanel.defaultPanel("my_machine")
 *             .child(ButtonWidget<*>().onMousePressed { /* ... */ true })
 *     }
 * }
 * ```
 *
 * @see MachineComponent
 */
public interface UIProviderComponent : MachineComponent {

    /**
     * Build the main UI panel for this machine.
     * 为此机器构建主 UI 面板。
     *
     * This is called when the GUI is opened. The panel should contain all widgets
     * needed for the machine's interface.
     * 当 GUI 打开时调用。面板应包含机器界面所需的所有组件。
     *
     * @param syncManager The panel sync manager for registering sync handlers
     *                    用于注册同步处理器的面板同步管理器
     * @return The built ModularPanel / 构建的 ModularPanel
     */
    public fun buildPanel(syncManager: PanelSyncManager): ModularPanel

    /**
     * Optionally provide additional widgets to be added to an existing panel.
     * 可选地提供要添加到现有面板的额外组件。
     *
     * This can be used by other components to contribute their own UI elements.
     * 这可以被其他组件用来贡献它们自己的 UI 元素。
     *
     * @param syncManager The panel sync manager
     * @return List of widgets to add, or empty list if none
     */
    public fun buildAdditionalWidgets(syncManager: PanelSyncManager): List<IWidget> = emptyList()

}
