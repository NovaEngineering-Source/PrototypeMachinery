package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.ui

import crafttweaker.annotations.ZenRegister
import github.kasuminova.prototypemachinery.api.ui.definition.FactoryRecipeProgressListDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

@ZenClass("mods.prototypemachinery.ui.RecipeProgressListBuilder")
@ZenRegister
public class RecipeProgressListBuilder : IWidgetBuilder {

    private var x: Int = 0
    private var y: Int = 0
    private var width: Int = 89
    private var height: Int = 238

    private var syncKey: String = "factoryRecipeProgress"

    private var entryHeight: Int = 33
    private var visibleRows: Int = 7

    private var scrollOffsetX: Int = 4
    private var scrollOffsetY: Int = 0

    private var showSlotIndex: Boolean = true
    private var showPercent: Boolean = true

    private var idleKey: String = "prototypemachinery.gui.recipe_progress.idle"
    private var runningKey: String = "prototypemachinery.gui.recipe_progress.running"
    private var errorKey: String = "prototypemachinery.gui.recipe_progress.error"
    private var errorWithMessageKey: String = "prototypemachinery.gui.recipe_progress.error_with_message"

    @ZenMethod
    override fun setPos(x: Int, y: Int): RecipeProgressListBuilder {
        this.x = x
        this.y = y
        return this
    }

    @ZenMethod
    override fun setSize(width: Int, height: Int): RecipeProgressListBuilder {
        this.width = width
        this.height = height
        return this
    }

    /**
     * Set sync key used to register the GUI-scoped sync handler in PanelSyncManager.
     * If you add multiple recipe lists, make sure they have different keys.
     */
    @ZenMethod
    public fun setSyncKey(key: String): RecipeProgressListBuilder {
        this.syncKey = key
        return this
    }

    @ZenMethod
    public fun setEntryHeight(h: Int): RecipeProgressListBuilder {
        this.entryHeight = h
        return this
    }

    @ZenMethod
    public fun setVisibleRows(rows: Int): RecipeProgressListBuilder {
        this.visibleRows = rows
        return this
    }

    @ZenMethod
    public fun setScrollOffset(x: Int, y: Int): RecipeProgressListBuilder {
        this.scrollOffsetX = x
        this.scrollOffsetY = y
        return this
    }

    @ZenMethod
    public fun showSlotIndex(show: Boolean): RecipeProgressListBuilder {
        this.showSlotIndex = show
        return this
    }

    @ZenMethod
    public fun showPercent(show: Boolean): RecipeProgressListBuilder {
        this.showPercent = show
        return this
    }

    @ZenMethod
    public fun setStatusI18nKeys(idle: String, running: String, error: String, errorWithMessage: String): RecipeProgressListBuilder {
        this.idleKey = idle
        this.runningKey = running
        this.errorKey = error
        this.errorWithMessageKey = errorWithMessage
        return this
    }

    override fun build(): WidgetDefinition {
        return FactoryRecipeProgressListDefinition(
            x = x,
            y = y,
            width = width,
            height = height,
            syncKey = syncKey,
            entryHeight = entryHeight,
            visibleRows = visibleRows,
            scrollOffsetX = scrollOffsetX,
            scrollOffsetY = scrollOffsetY,
            showSlotIndex = showSlotIndex,
            showPercent = showPercent,
            idleKey = idleKey,
            runningKey = runningKey,
            errorKey = errorKey,
            errorWithMessageKey = errorWithMessageKey
        )
    }
}
