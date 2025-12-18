package github.kasuminova.prototypemachinery.api.ui.definition

/**
 * Base class for all UI definitions created via ZenScript.
 * ZenScript 通过 Builder 构建出的 UI 定义基类。
 *
 * These definitions are serialized/stored in the MachineType and used by the client
 * to instantiate actual ModularUI widgets.
 */
public interface WidgetDefinition {
    public val x: Int
    public val y: Int
    public val width: Int
    public val height: Int
    // Future: Flex layout support

    // The type identifier for serialization/factory lookup
    public val type: String
}

// ============================================================================
// Container / Layout Widgets
// 容器 / 布局组件
// ============================================================================

public data class PanelDefinition(
    override val x: Int = 0,
    override val y: Int = 0,
    override val width: Int = 176,
    override val height: Int = 166,
    public val backgroundTexture: String? = null,
    public val children: List<WidgetDefinition> = emptyList()
) : WidgetDefinition {
    override val type: String = "panel"
}

/**
 * Row layout - arranges children horizontally.
 * 行布局 - 水平排列子组件。
 */
public data class RowDefinition(
    override val x: Int = 0,
    override val y: Int = 0,
    override val width: Int = 0, // 0 = auto
    override val height: Int = 0, // 0 = auto
    public val spacing: Int = 0,
    public val children: List<WidgetDefinition> = emptyList()
) : WidgetDefinition {
    override val type: String = "row"
}

/**
 * Column layout - arranges children vertically.
 * 列布局 - 垂直排列子组件。
 */
public data class ColumnDefinition(
    override val x: Int = 0,
    override val y: Int = 0,
    override val width: Int = 0, // 0 = auto
    override val height: Int = 0, // 0 = auto
    public val spacing: Int = 0,
    public val children: List<WidgetDefinition> = emptyList()
) : WidgetDefinition {
    override val type: String = "column"
}

/**
 * Grid layout - arranges children in a grid pattern.
 * 网格布局 - 按网格模式排列子组件。
 */
public data class GridDefinition(
    override val x: Int = 0,
    override val y: Int = 0,
    override val width: Int = 0,
    override val height: Int = 0,
    public val columns: Int = 1,
    public val rowSpacing: Int = 0,
    public val columnSpacing: Int = 0,
    public val children: List<WidgetDefinition> = emptyList()
) : WidgetDefinition {
    override val type: String = "grid"
}

// ============================================================================
// Interactive Widgets
// 交互组件
// ============================================================================

public data class ButtonDefinition(
    override val x: Int = 0,
    override val y: Int = 0,
    override val width: Int = 27,
    override val height: Int = 15,
    public val text: String? = null,
    public val actionKey: String? = null
) : WidgetDefinition {
    override val type: String = "button"
}

/**
 * Toggle button - a button that can be toggled on/off.
 * 切换按钮 - 可以切换开/关状态的按钮。
 */
public data class ToggleButtonDefinition(
    override val x: Int = 0,
    override val y: Int = 0,
    override val width: Int = 27,
    override val height: Int = 15,
    public val stateKey: String? = null, // Data binding key for boolean state
    public val textOn: String? = null,
    public val textOff: String? = null,
    public val textureOn: String? = null,
    public val textureOff: String? = null
) : WidgetDefinition {
    override val type: String = "toggle_button"
}

/**
 * Slider widget for numeric input.
 * 用于数值输入的滑块组件。
 */
public data class SliderDefinition(
    override val x: Int = 0,
    override val y: Int = 0,
    override val width: Int = 100,
    override val height: Int = 14,
    public val min: Double = 0.0,
    public val max: Double = 100.0,
    public val step: Double = 1.0,
    public val valueKey: String? = null, // Data binding key
    public val horizontal: Boolean = true
) : WidgetDefinition {
    override val type: String = "slider"
}

// ============================================================================
// Display Widgets
// 显示组件
// ============================================================================

public data class TextDefinition(
    override val x: Int = 0,
    override val y: Int = 0,
    override val width: Int = 0,
    override val height: Int = 0,
    public val textKey: String? = null, // Static text or binding key
    public val color: Int = 0xFFFFFF,
    public val shadow: Boolean = true,
    public val alignment: String = "LEFT"
) : WidgetDefinition {
    override val type: String = "text"
}

/**
 * Dynamic text that updates from a data binding.
 * 从数据绑定更新的动态文本。
 */
public data class DynamicTextDefinition(
    override val x: Int = 0,
    override val y: Int = 0,
    override val width: Int = 0,
    override val height: Int = 0,
    public val textKey: String? = null, // Data binding key
    public val formatPattern: String? = null, // e.g., "Energy: %d FE"
    public val color: Int = 0xFFFFFF,
    public val shadow: Boolean = true,
    public val alignment: String = "LEFT"
) : WidgetDefinition {
    override val type: String = "dynamic_text"
}

public data class ProgressBarDefinition(
    override val x: Int = 0,
    override val y: Int = 0,
    override val width: Int = 55,
    override val height: Int = 12,
    public val direction: String = "RIGHT", // "LEFT", "RIGHT", "UP", "DOWN"
    public val texture: String? = null,
    public val progressKey: String? = null, // Data binding key
    public val tooltipTemplate: String? = null
) : WidgetDefinition {
    override val type: String = "progress_bar"
}

/**
 * Image/texture display widget.
 * 图像/纹理显示组件。
 */
public data class ImageDefinition(
    override val x: Int,
    override val y: Int,
    override val width: Int,
    override val height: Int,
    public val texture: String
) : WidgetDefinition {
    override val type: String = "image"
}

// ============================================================================
// Slot Widgets (for inventory interaction)
// 槽位组件（用于物品栏交互）
// ============================================================================

/**
 * Item slot for inventory interaction.
 * 用于物品栏交互的物品槽位。
 */
public data class ItemSlotDefinition(
    override val x: Int = 0,
    override val y: Int = 0,
    override val width: Int = 18,
    override val height: Int = 18,
    public val slotKey: String = "default", // Key to identify which slot in the machine
    public val slotIndex: Int = 0,
    public val canInsert: Boolean = true,
    public val canExtract: Boolean = true,
    public val phantom: Boolean = false // Ghost/phantom slot
) : WidgetDefinition {
    override val type: String = "item_slot"
}

/**
 * Fluid slot for fluid tank interaction.
 * 用于流体罐交互的流体槽位。
 */
public data class FluidSlotDefinition(
    override val x: Int = 0,
    override val y: Int = 0,
    override val width: Int = 18,
    override val height: Int = 18,
    public val tankKey: String = "default", // Key to identify which tank in the machine
    public val tankIndex: Int = 0,
    public val canFill: Boolean = true,
    public val canDrain: Boolean = true
) : WidgetDefinition {
    override val type: String = "fluid_slot"
}

/**
 * A group of item slots arranged in a grid.
 * 按网格排列的物品槽位组。
 */
public data class ItemSlotGroupDefinition(
    override val x: Int,
    override val y: Int,
    override val width: Int = 0,
    override val height: Int = 0,
    public val slotKey: String,
    public val startIndex: Int,
    public val columns: Int,
    public val rows: Int,
    public val canInsert: Boolean = true,
    public val canExtract: Boolean = true
) : WidgetDefinition {
    override val type: String = "item_slot_group"
}

/**
 * Player inventory slots - standard 3x9 + hotbar layout.
 * 玩家物品栏槽位 - 标准 3x9 + 快捷栏布局。
 */
public data class PlayerInventoryDefinition(
    override val x: Int,
    override val y: Int,
    override val width: Int = 162,
    override val height: Int = 76
) : WidgetDefinition {
    override val type: String = "player_inventory"
}

// ============================================================================
// Utility Widgets
// 工具组件
// ============================================================================

/**
 * Empty space widget for layout purposes.
 * 用于布局目的的空白空间组件。
 */
public data class SpacerDefinition(
    override val x: Int = 0,
    override val y: Int = 0,
    override val width: Int = 0,
    override val height: Int = 0
) : WidgetDefinition {
    override val type: String = "spacer"
}

/**
 * Tooltip-only widget (invisible, shows tooltip on hover).
 * 仅工具提示组件（不可见，悬停时显示工具提示）。
 */
public data class TooltipAreaDefinition(
    override val x: Int = 0,
    override val y: Int = 0,
    override val width: Int = 0,
    override val height: Int = 0,
    public val tooltipLines: List<String> = emptyList()
) : WidgetDefinition {
    override val type: String = "tooltip_area"
}

/**
 * Tab widget for multi-page UI.
 * 用于多页 UI 的标签页组件。
 */
public data class TabDefinition(
    override val x: Int,
    override val y: Int,
    override val width: Int,
    override val height: Int,
    public val tabKey: String,
    public val title: String,
    public val icon: String?,
    public val content: WidgetDefinition
) : WidgetDefinition {
    override val type: String = "tab"
}

/**
 * Tab container that holds multiple tabs.
 * 容纳多个标签页的标签容器。
 */
public data class TabContainerDefinition(
    override val x: Int,
    override val y: Int,
    override val width: Int,
    override val height: Int,
    public val tabPosition: String = "TOP", // "TOP", "BOTTOM", "LEFT", "RIGHT"
    public val tabs: List<TabDefinition>
) : WidgetDefinition {
    override val type: String = "tab_container"
}

// ============================================================================
// Machine-specific Widgets
// 机器特化组件（脚本可用的“官方组件”）
// ============================================================================

/**
 * Factory recipe processor progress list.
 * 工厂配方处理器的配方进度列表。
 *
 * Notes:
 * - This widget is host-aware: it only renders when the machine has a FactoryRecipeProcessorComponent.
 * - Sync is GUI-scoped and handled internally by the client widget factory.
 */
public data class FactoryRecipeProgressListDefinition(
    override val x: Int = 0,
    override val y: Int = 0,
    override val width: Int = 89,
    override val height: Int = 238,

    /** Unique sync key in the panel sync manager. Use this to avoid collisions if you add multiple lists. */
    public val syncKey: String = "factoryRecipeProgress",

    /** Row height in pixels. Default matches gui_controller_states.png entry slice height (33). */
    public val entryHeight: Int = 33,

    /** Visible rows. If <= 0, client will derive from height / entryHeight. */
    public val visibleRows: Int = 7,

    /** Scroll handle visual placement offset inside this widget. */
    public val scrollOffsetX: Int = 4,
    public val scrollOffsetY: Int = 0,

    public val showSlotIndex: Boolean = true,
    public val showPercent: Boolean = true,

    /** I18n keys for built-in statuses (used when entry.message is empty / idle / error without message). */
    public val idleKey: String = "prototypemachinery.gui.recipe_progress.idle",
    public val runningKey: String = "prototypemachinery.gui.recipe_progress.running",
    public val errorKey: String = "prototypemachinery.gui.recipe_progress.error",
    public val errorWithMessageKey: String = "prototypemachinery.gui.recipe_progress.error_with_message"
) : WidgetDefinition {
    override val type: String = "factory_recipe_progress_list"
}
