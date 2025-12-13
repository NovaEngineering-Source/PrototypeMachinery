package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.ui

import crafttweaker.annotations.ZenRegister
import github.kasuminova.prototypemachinery.api.ui.definition.FluidSlotDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.ItemSlotDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.ItemSlotGroupDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.PlayerInventoryDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

/**
 * Builder for ItemSlot widget - single item slot.
 * 物品槽位组件构建器 - 单个物品槽位。
 */
@ZenClass("mods.prototypemachinery.ui.ItemSlotBuilder")
@ZenRegister
public class ItemSlotBuilder() : IWidgetBuilder {
    private var x: Int = 0
    private var y: Int = 0
    private var width: Int = 18
    private var height: Int = 18
    private var slotKey: String = "default"
    private var slotIndex: Int = 0
    private var canInsert: Boolean = true
    private var canExtract: Boolean = true
    private var phantom: Boolean = false

    // Secondary constructor for backward compatibility
    public constructor(slotKey: String, slotIndex: Int) : this() {
        this.slotKey = slotKey
        this.slotIndex = slotIndex
    }

    @ZenMethod
    override fun setPos(x: Int, y: Int): ItemSlotBuilder {
        this.x = x
        this.y = y
        return this
    }

    @ZenMethod
    override fun setSize(width: Int, height: Int): ItemSlotBuilder {
        this.width = width
        this.height = height
        return this
    }

    @ZenMethod
    public fun setSlotKey(key: String): ItemSlotBuilder {
        this.slotKey = key
        return this
    }

    @ZenMethod
    public fun setSlotIndex(index: Int): ItemSlotBuilder {
        this.slotIndex = index
        return this
    }

    @ZenMethod
    public fun setInsert(canInsert: Boolean): ItemSlotBuilder {
        this.canInsert = canInsert
        return this
    }

    @ZenMethod
    public fun setExtract(canExtract: Boolean): ItemSlotBuilder {
        this.canExtract = canExtract
        return this
    }

    @ZenMethod
    public fun setPhantom(phantom: Boolean): ItemSlotBuilder {
        this.phantom = phantom
        return this
    }

    override fun build(): WidgetDefinition {
        return ItemSlotDefinition(x, y, width, height, slotKey, slotIndex, canInsert, canExtract, phantom)
    }
}

/**
 * Builder for FluidSlot widget - fluid tank slot.
 * 流体槽位组件构建器 - 流体罐槽位。
 */
@ZenClass("mods.prototypemachinery.ui.FluidSlotBuilder")
@ZenRegister
public class FluidSlotBuilder() : IWidgetBuilder {
    private var x: Int = 0
    private var y: Int = 0
    private var width: Int = 18
    private var height: Int = 50
    private var tankKey: String = "default"
    private var tankIndex: Int = 0
    private var capacity: Int = 16000
    private var canFill: Boolean = true
    private var canDrain: Boolean = true

    @ZenMethod
    override fun setPos(x: Int, y: Int): FluidSlotBuilder {
        this.x = x
        this.y = y
        return this
    }

    @ZenMethod
    override fun setSize(width: Int, height: Int): FluidSlotBuilder {
        this.width = width
        this.height = height
        return this
    }

    @ZenMethod
    public fun setTankKey(key: String): FluidSlotBuilder {
        this.tankKey = key
        return this
    }

    @ZenMethod
    public fun setTankIndex(index: Int): FluidSlotBuilder {
        this.tankIndex = index
        return this
    }

    @ZenMethod
    public fun setCapacity(capacity: Int): FluidSlotBuilder {
        this.capacity = capacity
        return this
    }

    @ZenMethod
    public fun setFill(canFill: Boolean): FluidSlotBuilder {
        this.canFill = canFill
        return this
    }

    @ZenMethod
    public fun setDrain(canDrain: Boolean): FluidSlotBuilder {
        this.canDrain = canDrain
        return this
    }

    override fun build(): WidgetDefinition {
        return FluidSlotDefinition(x, y, width, height, tankKey, tankIndex, canFill, canDrain)
    }
}

/**
 * Builder for ItemSlotGroup widget - grid of item slots.
 * 物品槽位组构建器 - 物品槽位网格。
 */
@ZenClass("mods.prototypemachinery.ui.ItemSlotGroupBuilder")
@ZenRegister
public class ItemSlotGroupBuilder(
    private val slotKey: String,
    private val startIndex: Int,
    private val columns: Int,
    private val rows: Int
) : IWidgetBuilder {
    private var x: Int = 0
    private var y: Int = 0
    private var canInsert: Boolean = true
    private var canExtract: Boolean = true

    @ZenMethod
    override fun setPos(x: Int, y: Int): ItemSlotGroupBuilder {
        this.x = x
        this.y = y
        return this
    }

    @ZenMethod
    override fun setSize(width: Int, height: Int): ItemSlotGroupBuilder {
        // Size is calculated from columns/rows, ignore explicit size
        return this
    }

    @ZenMethod
    public fun setInsert(canInsert: Boolean): ItemSlotGroupBuilder {
        this.canInsert = canInsert
        return this
    }

    @ZenMethod
    public fun setExtract(canExtract: Boolean): ItemSlotGroupBuilder {
        this.canExtract = canExtract
        return this
    }

    override fun build(): WidgetDefinition {
        return ItemSlotGroupDefinition(x, y, columns * 18, rows * 18, slotKey, startIndex, columns, rows, canInsert, canExtract)
    }
}

/**
 * Builder for PlayerInventory widget - standard player inventory layout.
 * 玩家物品栏组件构建器 - 标准玩家物品栏布局。
 */
@ZenClass("mods.prototypemachinery.ui.PlayerInventoryBuilder")
@ZenRegister
public class PlayerInventoryBuilder : IWidgetBuilder {
    private var x: Int = 7
    private var y: Int = 84

    @ZenMethod
    override fun setPos(x: Int, y: Int): PlayerInventoryBuilder {
        this.x = x
        this.y = y
        return this
    }

    @ZenMethod
    override fun setSize(width: Int, height: Int): PlayerInventoryBuilder {
        // Size is fixed for player inventory
        return this
    }

    override fun build(): WidgetDefinition {
        return PlayerInventoryDefinition(x, y)
    }
}
