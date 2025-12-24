package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.jei

import crafttweaker.annotations.ZenRegister
import github.kasuminova.prototypemachinery.integration.jei.runtime.JeiRenderOptions
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

/**
 * ZenScript helper factory for building JEI layouts.
 */
@ZenClass("mods.prototypemachinery.jei.PMJEI")
@ZenRegister
public object PMJEI {

    @ZenMethod
    @JvmStatic
    public fun createLayout(): LayoutBuilder {
        return LayoutBuilder()
    }

    @ZenMethod
    @JvmStatic
    public fun createLayoutSized(width: Int, height: Int): LayoutBuilder {
        return LayoutBuilder().setSize(width, height)
    }

    /**
     * Enable/disable rendering a small chance label on the top-left of JEI slots.
     *
     * Default is enabled.
     */
    @ZenMethod
    @JvmStatic
    public fun setChanceOverlayEnabled(enabled: Boolean) {
        JeiRenderOptions.renderChanceOverlayOnSlots = enabled
    }

    /**
     * @return true if the small chance label on JEI slots is enabled.
     */
    @ZenMethod
    @JvmStatic
    public fun isChanceOverlayEnabled(): Boolean {
        return JeiRenderOptions.renderChanceOverlayOnSlots
    }

    /**
     * Controls how fuzzy-input / random-output candidates are displayed in JEI.
     *
     * Supported values (case-insensitive):
     * - "alternatives": one slot with cycling alternatives (default)
     * - "expanded": split into N slots, each slot shows one fixed candidate
     */
    @ZenMethod
    @JvmStatic
    public fun setCandidateSlotRenderMode(mode: String) {
        val m = mode.trim().lowercase()
        JeiRenderOptions.candidateSlotRenderMode = when (m) {
            "expanded" -> JeiRenderOptions.CandidateSlotRenderMode.EXPANDED
            "alternatives", "alternative", "cycle", "cycling" -> JeiRenderOptions.CandidateSlotRenderMode.ALTERNATIVES
            else -> JeiRenderOptions.candidateSlotRenderMode
        }
    }

    /**
     * @return current candidate slot render mode name: "alternatives" or "expanded".
     */
    @ZenMethod
    @JvmStatic
    public fun getCandidateSlotRenderMode(): String {
        return when (JeiRenderOptions.candidateSlotRenderMode) {
            JeiRenderOptions.CandidateSlotRenderMode.EXPANDED -> "expanded"
            JeiRenderOptions.CandidateSlotRenderMode.ALTERNATIVES -> "alternatives"
        }
    }
}
