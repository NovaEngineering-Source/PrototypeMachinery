package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.ui

import crafttweaker.annotations.ZenRegister
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

/**
 * PMUI - PrototypeMachinery UI Factory
 * PMUI - PrototypeMachinery UI 工厂
 *
 * Main entry point for creating UI widgets in ZenScript.
 * ZenScript 中创建 UI 组件的主要入口点。
 *
 * Usage / 用法:
 * ```zenscript
 * import mods.prototypemachinery.ui.PMUI;
 *
 * val panel = PMUI.createPanel()
 *     .setSize(176, 166)
 *     .addChild(PMUI.button("Click Me").setPos(10, 10));
 * ```
 */
@ZenClass("mods.prototypemachinery.ui.PMUI")
@ZenRegister
public object PMUI {

    // ========================================================================
    // Container / Layout Builders
    // 容器 / 布局构建器
    // ========================================================================

    /**
     * Create a new panel (root container for UI).
     * 创建新面板（UI 的根容器）。
     */
    @ZenMethod
    @JvmStatic
    public fun createPanel(): PanelBuilder {
        return PanelBuilder()
    }

    /**
     * Create a row layout (horizontal arrangement).
     * 创建行布局（水平排列）。
     */
    @ZenMethod
    @JvmStatic
    public fun row(): RowBuilder {
        return RowBuilder()
    }

    /**
     * Create a column layout (vertical arrangement).
     * 创建列布局（垂直排列）。
     */
    @ZenMethod
    @JvmStatic
    public fun column(): ColumnBuilder {
        return ColumnBuilder()
    }

    /**
     * Create a grid layout with specified number of columns.
     * 创建具有指定列数的网格布局。
     */
    @ZenMethod
    @JvmStatic
    public fun grid(columns: Int): GridBuilder {
        return GridBuilder(columns)
    }

    /**
     * Create a grid layout (columns set via setColumns()).
     * 创建网格布局（通过 setColumns() 设置列数）。
     */
    @ZenMethod
    @JvmStatic
    public fun grid(): GridBuilder {
        return GridBuilder()
    }

    /**
     * Create a scrollable container.
     * 创建可滚动容器。
     */
    @ZenMethod
    @JvmStatic
    public fun scrollContainer(): ScrollContainerBuilder {
        return ScrollContainerBuilder()
    }

    /**
     * Wrap a widget with conditional visibility/enabled expressions.
     * 用条件表达式包装组件的可见性/可用性。
     */
    @ZenMethod
    @JvmStatic
    public fun conditional(child: IWidgetBuilder): ConditionalBuilder {
        return ConditionalBuilder(child)
    }

    /**
     * Create a tab container.
     * 创建标签容器。
     */
    @ZenMethod
    @JvmStatic
    public fun tabContainer(): TabContainerBuilder {
        return TabContainerBuilder()
    }

    /**
     * Create a tab definition (content should be set via setContent()).
     * 创建标签页（内容通过 setContent() 设置）。
     */
    @ZenMethod
    @JvmStatic
    public fun tab(tabKey: String, title: String): TabBuilder {
        return TabBuilder(tabKey, title)
    }

    // ========================================================================
    // Interactive Widget Builders
    // 交互组件构建器
    // ========================================================================

    /**
     * Create a button with text.
     * 创建带文本的按钮。
     */
    @ZenMethod
    @JvmStatic
    public fun button(text: String): ButtonBuilder {
        return ButtonBuilder(text)
    }

    /**
     * Create a button without text.
     * 创建不带文本的按钮。
     */
    @ZenMethod
    @JvmStatic
    public fun button(): ButtonBuilder {
        return ButtonBuilder(null)
    }

    /**
     * Create a toggle button.
     * 创建切换按钮。
     */
    @ZenMethod
    @JvmStatic
    public fun toggleButton(): ToggleButtonBuilder {
        return ToggleButtonBuilder()
    }

    /**
     * Create a slider for numeric input.
     * 创建用于数值输入的滑块。
     */
    @ZenMethod
    @JvmStatic
    public fun slider(): SliderBuilder {
        return SliderBuilder()
    }

    /**
     * Create a text field.
     * 创建文本输入框。
     */
    @ZenMethod
    @JvmStatic
    public fun textField(): TextFieldBuilder {
        return TextFieldBuilder()
    }

    // ========================================================================
    // gui_states Templates (开箱即用模板)
    // ========================================================================

    /**
     * gui_states 空按钮（无阴影，固定尺寸 16x19）。
     */
    @ZenMethod
    @JvmStatic
    public fun emptyButton(): ButtonBuilder {
        return ButtonBuilder(null)
            .setSize(16, 19)
            .setSkin("gui_states/empty_button/normal")
    }

    /**
     * gui_states 空按钮（有阴影，固定尺寸 16x19）。
     */
    @ZenMethod
    @JvmStatic
    public fun emptyButtonShadow(): ButtonBuilder {
        return ButtonBuilder(null)
            .setSize(16, 19)
            .setSkin("gui_states/empty_button/shadow")
    }

    /**
     * gui_states 扩展按钮（无阴影，9-slice，可缩放）。
     */
    @ZenMethod
    @JvmStatic
    public fun expandButton(): ButtonBuilder {
        return ButtonBuilder(null)
            .setSize(16, 19)
            .setSkin("gui_states/expand_button/normal")
    }

    /**
     * gui_states 扩展按钮（有阴影，9-slice，可缩放）。
     */
    @ZenMethod
    @JvmStatic
    public fun expandButtonShadow(): ButtonBuilder {
        return ButtonBuilder(null)
            .setSize(16, 19)
            .setSkin("gui_states/expand_button/shadow")
    }

    /**
     * gui_states 开关按钮（switch，默认尺寸 28x14）。
     */
    @ZenMethod
    @JvmStatic
    public fun switchButton(): ToggleButtonBuilder {
        return ToggleButtonBuilder()
            .setSize(28, 14)
            .setSkin("gui_states/switch/normal")
    }

    /** gui_states 开关按钮（switch，有阴影，默认尺寸 28x14）。 */
    @ZenMethod
    @JvmStatic
    public fun switchButtonShadow(): ToggleButtonBuilder {
        return ToggleButtonBuilder()
            .setSize(28, 14)
            .setSkin("gui_states/switch/shadow")
    }

    /** gui_states 窄横滑块 slider_s_x（无阴影，默认 56x10）。 */
    @ZenMethod
    @JvmStatic
    public fun sliderSX(): SliderBuilder {
        return SliderBuilder()
            .setSize(56, 10)
            .setHorizontal(true)
            .setSkin("gui_states/slider/s/x/normal")
    }

    /** gui_states 窄横滑块 slider_s_x（有阴影，默认 56x10）。 */
    @ZenMethod
    @JvmStatic
    public fun sliderSXShadow(): SliderBuilder {
        return SliderBuilder()
            .setSize(56, 10)
            .setHorizontal(true)
            .setSkin("gui_states/slider/s/x/shadow")
    }

    /** gui_states 扩展窄横滑块 slider_s_x（无阴影，最小贴图 14x10，组件可横向缩放）。 */
    @ZenMethod
    @JvmStatic
    public fun sliderSXExpand(): SliderBuilder {
        return SliderBuilder()
            .setSize(14, 10)
            .setHorizontal(true)
            .setSkin("gui_states/slider/s/x_expand/normal")
    }

    /** gui_states 扩展窄横滑块 slider_s_x（有阴影）。 */
    @ZenMethod
    @JvmStatic
    public fun sliderSXExpandShadow(): SliderBuilder {
        return SliderBuilder()
            .setSize(14, 10)
            .setHorizontal(true)
            .setSkin("gui_states/slider/s/x_expand/shadow")
    }

    /** gui_states 宽横滑块 slider_m_x（无阴影，默认 56x13）。 */
    @ZenMethod
    @JvmStatic
    public fun sliderMX(): SliderBuilder {
        return SliderBuilder()
            .setSize(56, 13)
            .setHorizontal(true)
            .setSkin("gui_states/slider/m/x/normal")
    }

    /** gui_states 宽横滑块 slider_m_x（有阴影，默认 56x13）。 */
    @ZenMethod
    @JvmStatic
    public fun sliderMXShadow(): SliderBuilder {
        return SliderBuilder()
            .setSize(56, 13)
            .setHorizontal(true)
            .setSkin("gui_states/slider/m/x/shadow")
    }

    /** gui_states 扩展宽横滑块 slider_m_x（无阴影，最小贴图 17x13，组件可横向缩放）。 */
    @ZenMethod
    @JvmStatic
    public fun sliderMXExpand(): SliderBuilder {
        return SliderBuilder()
            .setSize(17, 13)
            .setHorizontal(true)
            .setSkin("gui_states/slider/m/x_expand/normal")
    }

    /** gui_states 扩展宽横滑块 slider_m_x（有阴影）。 */
    @ZenMethod
    @JvmStatic
    public fun sliderMXExpandShadow(): SliderBuilder {
        return SliderBuilder()
            .setSize(17, 13)
            .setHorizontal(true)
            .setSkin("gui_states/slider/m/x_expand/shadow")
    }

    /** gui_states 窄竖滑块 slider_s_y（无阴影，默认 10x77）。 */
    @ZenMethod
    @JvmStatic
    public fun sliderSY(): SliderBuilder {
        return SliderBuilder()
            .setSize(10, 77)
            .setHorizontal(false)
            .setSkin("gui_states/slider/s/y/normal")
    }

    /** gui_states 窄竖滑块 slider_s_y（有阴影，默认 10x77）。 */
    @ZenMethod
    @JvmStatic
    public fun sliderSYShadow(): SliderBuilder {
        return SliderBuilder()
            .setSize(10, 77)
            .setHorizontal(false)
            .setSkin("gui_states/slider/s/y/shadow")
    }

    /** gui_states 扩展窄竖滑块 slider_s_y（无阴影，最小贴图 10x16，组件可纵向缩放）。 */
    @ZenMethod
    @JvmStatic
    public fun sliderSYExpand(): SliderBuilder {
        return SliderBuilder()
            .setSize(10, 16)
            .setHorizontal(false)
            .setSkin("gui_states/slider/s/y_expand/normal")
    }

    /** gui_states 扩展窄竖滑块 slider_s_y（有阴影）。 */
    @ZenMethod
    @JvmStatic
    public fun sliderSYExpandShadow(): SliderBuilder {
        return SliderBuilder()
            .setSize(10, 16)
            .setHorizontal(false)
            .setSkin("gui_states/slider/s/y_expand/shadow")
    }

    /** gui_states 宽竖滑块 slider_m_y（无阴影，默认 13x56）。 */
    @ZenMethod
    @JvmStatic
    public fun sliderMY(): SliderBuilder {
        return SliderBuilder()
            .setSize(13, 56)
            .setHorizontal(false)
            .setSkin("gui_states/slider/m/y/normal")
    }

    /** gui_states 宽竖滑块 slider_m_y（有阴影，默认 13x56）。 */
    @ZenMethod
    @JvmStatic
    public fun sliderMYShadow(): SliderBuilder {
        return SliderBuilder()
            .setSize(13, 56)
            .setHorizontal(false)
            .setSkin("gui_states/slider/m/y/shadow")
    }

    /** gui_states 扩展宽竖滑块 slider_m_y（无阴影，最小贴图 13x18，组件可纵向缩放）。 */
    @ZenMethod
    @JvmStatic
    public fun sliderMYExpand(): SliderBuilder {
        return SliderBuilder()
            .setSize(13, 18)
            .setHorizontal(false)
            .setSkin("gui_states/slider/m/y_expand/normal")
    }

    /** gui_states 扩展宽竖滑块 slider_m_y（有阴影）。 */
    @ZenMethod
    @JvmStatic
    public fun sliderMYExpandShadow(): SliderBuilder {
        return SliderBuilder()
            .setSize(13, 18)
            .setHorizontal(false)
            .setSkin("gui_states/slider/m/y_expand/shadow")
    }

    /** gui_states 默认输入框（无阴影，默认 56x13）。 */
    @ZenMethod
    @JvmStatic
    public fun inputBox(): TextFieldBuilder {
        return TextFieldBuilder()
            .setSize(56, 13)
            .setSkin("gui_states/input_box/normal")
    }

    /** gui_states 默认输入框（有阴影，默认 56x13）。 */
    @ZenMethod
    @JvmStatic
    public fun inputBoxShadow(): TextFieldBuilder {
        return TextFieldBuilder()
            .setSize(56, 13)
            .setSkin("gui_states/input_box/shadow")
    }

    /** gui_states 扩展输入框（无阴影，最小 5x10，9-slice，可缩放）。 */
    @ZenMethod
    @JvmStatic
    public fun inputBoxExpand(): TextFieldBuilder {
        return TextFieldBuilder()
            .setSize(5, 10)
            .setSkin("gui_states/input_box/expand/normal")
    }

    /** gui_states 扩展输入框（有阴影，最小 5x10，9-slice，可缩放）。 */
    @ZenMethod
    @JvmStatic
    public fun inputBoxExpandShadow(): TextFieldBuilder {
        return TextFieldBuilder()
            .setSize(5, 10)
            .setSkin("gui_states/input_box/expand/shadow")
    }

    // ========================================================================
    // Display Widget Builders
    // 显示组件构建器
    // ========================================================================

    /**
     * Create a text widget.
     * 创建文本组件。
     */
    @ZenMethod
    @JvmStatic
    public fun text(content: String): TextBuilder {
        return TextBuilder(content)
    }

    /**
     * Create a progress bar.
     * 创建进度条。
     */
    @ZenMethod
    @JvmStatic
    public fun progressBar(): ProgressBarBuilder {
        return ProgressBarBuilder()
    }

    /**
     * Create a dynamic text widget.
     * 创建动态文本组件。
     */
    @ZenMethod
    @JvmStatic
    public fun dynamicText(key: String): DynamicTextBuilder {
        return DynamicTextBuilder(key)
    }

    /**
     * Create an image widget.
     * 创建图像组件。
     *
     * @param texture Texture path, e.g., "modid:gui/my_image"
     */
    @ZenMethod
    @JvmStatic
    public fun image(texture: String): ImageBuilder {
        return ImageBuilder(texture)
    }

    // ========================================================================
    // Utility Widget Builders
    // 工具组件构建器
    // ========================================================================

    /**
     * Create an empty spacer widget.
     * 创建空白占位组件。
     */
    @ZenMethod
    @JvmStatic
    public fun spacer(): SpacerBuilder {
        return SpacerBuilder()
    }

    /**
     * Create a tooltip hover area widget.
     * 创建工具提示悬停区域组件。
     */
    @ZenMethod
    @JvmStatic
    public fun tooltipArea(): TooltipAreaBuilder {
        return TooltipAreaBuilder()
    }

    /**
     * Wrap any widget builder and attach tooltip metadata.
     * 为任意组件添加 tooltip（静态行 + 可选动态绑定）。
     */
    @ZenMethod
    @JvmStatic
    public fun tooltip(child: IWidgetBuilder): TooltipBuilder {
        return TooltipBuilder(child)
    }

    // ========================================================================
    // Machine Widgets
    // 机器特化组件
    // ========================================================================

    /**
     * FactoryRecipeProcessor progress list.
     * 工厂配方处理器的配方进度列表。
     */
    @ZenMethod
    @JvmStatic
    public fun recipeProgressList(): RecipeProgressListBuilder {
        return RecipeProgressListBuilder()
    }

    // ========================================================================
    // Slot Widget Builders
    // 槽位组件构建器
    // ========================================================================

    /**
     * Create an item slot.
     * 创建物品槽位。
     *
     * @param slotKey Key identifying the slot handler
     * @param slotIndex Index of the slot
     */
    @ZenMethod
    @JvmStatic
    public fun itemSlot(slotKey: String, slotIndex: Int): ItemSlotBuilder {
        return ItemSlotBuilder(slotKey, slotIndex)
    }

    /**
     * Create an item slot (configure via setSlotKey/setSlotIndex).
     * 创建物品槽位（通过 setSlotKey/setSlotIndex 配置）。
     */
    @ZenMethod
    @JvmStatic
    public fun itemSlot(): ItemSlotBuilder {
        return ItemSlotBuilder()
    }

    /**
     * Create a fluid slot.
     * 创建流体槽位。
     */
    @ZenMethod
    @JvmStatic
    public fun fluidSlot(): FluidSlotBuilder {
        return FluidSlotBuilder()
    }

    /**
     * Create a group of item slots.
     * 创建物品槽位组。
     *
     * @param slotKey Key identifying the slot handler
     * @param startIndex Starting slot index
     * @param columns Number of columns
     * @param rows Number of rows
     */
    @ZenMethod
    @JvmStatic
    public fun itemSlotGroup(slotKey: String, startIndex: Int, columns: Int, rows: Int): ItemSlotGroupBuilder {
        return ItemSlotGroupBuilder(slotKey, startIndex, columns, rows)
    }

    /**
     * Create player inventory slots.
     * 创建玩家物品栏槽位。
     */
    @ZenMethod
    @JvmStatic
    public fun playerInventory(): PlayerInventoryBuilder {
        return PlayerInventoryBuilder()
    }
}
