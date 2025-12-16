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
    /** Whether the UI should default to 3D view mode. */
    val defaultTo3DView: Boolean,
    /** Whether the host supports future "locate/highlight" interactions. */
    val allowLocate: Boolean = false,
    /** Whether the host supports editing/operating child instances (future). */
    val allowOperateChildren: Boolean = false,
    /** Provides a default scan anchor; null disables scan initialization. */
    val anchorProvider: (Minecraft) -> BlockPos? = { mc -> mc.player?.position }
) {
    companion object {
        fun standalone(): StructurePreviewUiHostConfig = StructurePreviewUiHostConfig(
            hostName = "standalone",
            allowWorldScan = true,
            showScanControls = true,
            defaultTo3DView = false,
            allowLocate = true,
            allowOperateChildren = false
        )

        fun jeiReadOnly(): StructurePreviewUiHostConfig = StructurePreviewUiHostConfig(
            hostName = "jei",
            allowWorldScan = false,
            showScanControls = false,
            defaultTo3DView = true,
            allowLocate = false,
            allowOperateChildren = false,
            anchorProvider = { _ -> null }
        )
    }
}
