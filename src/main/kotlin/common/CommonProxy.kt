package github.kasuminova.prototypemachinery.common

import com.cleanroommc.modularui.value.sync.PanelSyncManager
import com.cleanroommc.modularui.widgets.layout.Flow
import github.kasuminova.prototypemachinery.common.registry.BlockRegisterer
import github.kasuminova.prototypemachinery.common.registry.HatchRegisterer
import github.kasuminova.prototypemachinery.common.registry.ItemRegisterer
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.common.MinecraftForge

internal open class CommonProxy {

    init {
        MinecraftForge.EVENT_BUS.register(BlockRegisterer)
        MinecraftForge.EVENT_BUS.register(ItemRegisterer)
        MinecraftForge.EVENT_BUS.register(HatchRegisterer)
    }

    open fun preInit() {}

    open fun init() {}

    open fun postInit() {}

    /**
     * Hook for adding client-only widgets to the Build Instrument UI.
     *
     * IMPORTANT: CommonProxy must not reference any client-only classes.
     */
    open fun addBuildInstrumentClientWidgets(root: Flow, tagProvider: () -> NBTTagCompound?, syncManager: PanelSyncManager) {
        // no-op on dedicated server
    }

}