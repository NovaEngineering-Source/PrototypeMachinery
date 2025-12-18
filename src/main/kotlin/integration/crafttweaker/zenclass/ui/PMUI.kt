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
