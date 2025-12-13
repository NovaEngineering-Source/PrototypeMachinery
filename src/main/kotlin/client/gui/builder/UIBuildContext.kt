package github.kasuminova.prototypemachinery.client.gui.builder

import com.cleanroommc.modularui.value.sync.PanelSyncManager
import github.kasuminova.prototypemachinery.common.block.entity.MachineBlockEntity

public data class UIBuildContext(
    val syncManager: PanelSyncManager,
    val machineTile: MachineBlockEntity,
    val textures: UITextures,
    val bindings: UIBindings
)
