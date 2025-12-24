package github.kasuminova.prototypemachinery.integration.jei.runtime

import github.kasuminova.prototypemachinery.integration.jei.runtime.JeiRenderOptions.withEffective


/**
 * Runtime render options for JEI.
 *
 * These are intentionally kept as simple in-memory flags:
 * - default values should be sensible for end-users
 * - CraftTweaker scripts may toggle them during load/reload
 */
public object JeiRenderOptions {

    /**
     * Resolved (effective) render options for one JEI runtime build.
     *
     * This is used to scope layout-specific options without mutating global defaults.
     */
    public data class Effective(
        public val renderChanceOverlayOnSlots: Boolean,
        public val candidateSlotRenderMode: CandidateSlotRenderMode,
    )

    /**
     * Optional per-layout overrides. Any null field falls back to the current global default.
     */
    public data class LayoutOverrides(
        public val renderChanceOverlayOnSlots: Boolean? = null,
        public val candidateSlotRenderMode: CandidateSlotRenderMode? = null,
    ) {
        public fun resolve(): Effective {
            return Effective(
                renderChanceOverlayOnSlots = renderChanceOverlayOnSlots ?: JeiRenderOptions.renderChanceOverlayOnSlots,
                candidateSlotRenderMode = candidateSlotRenderMode ?: JeiRenderOptions.candidateSlotRenderMode,
            )
        }
    }

    /**
     * Implemented by layout definitions that provide layout-scoped JEI render options.
     */
    public interface Provider {
        public val jeiRenderOptions: LayoutOverrides
    }

    /**
     * How to render fuzzy-input / random-output candidates in JEI.
     *
     * - [ALTERNATIVES]: current behavior; one slot shows a list of alternatives (JEI cycles).
     * - [EXPANDED]: split into N slots; each slot shows one fixed candidate, and tooltips indicate grouping.
     */
    public enum class CandidateSlotRenderMode {
        ALTERNATIVES,
        EXPANDED,
    }

    /**
     * If true, render chance/probability info as a tiny label on the top-left of the corresponding JEI slot.
     *
     * Default: enabled.
     */
    @Volatile
    @JvmField
    public var renderChanceOverlayOnSlots: Boolean = true

    /**
     * Candidate slot render mode for fuzzy inputs / random outputs.
     *
     * Default: [CandidateSlotRenderMode.ALTERNATIVES] (backward compatible).
     */
    @Volatile
    @JvmField
    public var candidateSlotRenderMode: CandidateSlotRenderMode = CandidateSlotRenderMode.ALTERNATIVES

    private val effectiveLocal: ThreadLocal<Effective?> = ThreadLocal()

    /** Current effective options (layout-scoped if within a [withEffective] block). */
    public fun current(): Effective {
        return effectiveLocal.get() ?: Effective(
            renderChanceOverlayOnSlots = renderChanceOverlayOnSlots,
            candidateSlotRenderMode = candidateSlotRenderMode,
        )
    }

    /**
     * Run [block] with the given [effective] options as the current layout-scoped options.
     *
     * This is thread-safe and supports nesting.
     */
    public fun <T> withEffective(effective: Effective, block: () -> T): T {
        val prev = effectiveLocal.get()
        effectiveLocal.set(effective)
        return try {
            block()
        } finally {
            effectiveLocal.set(prev)
        }
    }
}
