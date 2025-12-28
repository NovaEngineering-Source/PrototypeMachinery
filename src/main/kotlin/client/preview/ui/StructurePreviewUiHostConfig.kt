package github.kasuminova.prototypemachinery.client.preview.ui

import net.minecraft.client.Minecraft
import net.minecraft.util.math.BlockPos

/**
 * Host adapter config for structure preview UI.
 *
 * Purpose:
 * - Make the same preview UI reusable across different hosts (controller GUI / standalone / JEI).
 * - Keep world-dependent features strictly optional so read-only hosts can still render data safely.
 */
internal data class StructurePreviewUiHostConfig(
    /** Human-readable host name (debug/log/tooltips). */
    val hostName: String,
    /** Whether this host allows accessing client world for scanning. */
    val allowWorldScan: Boolean,
    /** Whether the UI should show scan controls. */
    val showScanControls: Boolean,
    /** Whether the UI should show a close button (JEI host should keep it hidden). */
    val showCloseButton: Boolean,
    /** Whether the UI should default to 3D view mode. */
    val defaultTo3DView: Boolean,
    /** Whether the host supports future "locate/highlight" interactions. */
    val allowLocate: Boolean = false,
    /** Whether the host supports editing/operating child instances (future). */
    val allowOperateChildren: Boolean = false,
    /** Provides a default scan anchor; null disables scan initialization. */
    val anchorProvider: (Minecraft) -> BlockPos? = { mc -> mc.player?.position },
    /** Whether scan should be enabled by default (useful when host hides scan controls). */
    val defaultEnableScan: Boolean = false,
    /** Which BOM the materials panel should show. */
    val materialsMode: MaterialsMode = MaterialsMode.FULL,
    /** AnyOf requirement selection provider: requirementKey -> selected optionKey (stableKey). */
    val anyOfSelectionProvider: (String) -> String? = { _ -> null },
    /** AnyOf requirement selection setter (optional). */
    val anyOfSelectionSetter: ((requirementKey: String, selectedOptionKey: String) -> Unit)? = null
) {
    enum class MaterialsMode {
        /** Always show the full BOM (JEI/standalone typical). */
        FULL,
        /** Show remaining required materials based on current scan/progress (Build Instrument typical). */
        REMAINING
    }

    companion object {
        fun standalone(): StructurePreviewUiHostConfig = StructurePreviewUiHostConfig(
            hostName = "standalone",
            allowWorldScan = true,
            showScanControls = true,
            showCloseButton = true,
            defaultTo3DView = false,
            allowLocate = true,
            allowOperateChildren = false
        )

        fun jeiReadOnly(): StructurePreviewUiHostConfig = StructurePreviewUiHostConfig(
            hostName = "jei",
            allowWorldScan = false,
            showScanControls = false,
            showCloseButton = false,
            defaultTo3DView = true,
            allowLocate = false,
            allowOperateChildren = false,
            anchorProvider = { _ -> null }
        )
    }
}
