package github.kasuminova.prototypemachinery.client

import github.kasuminova.prototypemachinery.client.preview.ProjectionKeyBindings
import github.kasuminova.prototypemachinery.client.preview.StructurePreviewClientCommand
import github.kasuminova.prototypemachinery.client.preview.WorldProjectionManager
import github.kasuminova.prototypemachinery.client.preview.ui.StructurePreviewUiClientCommand
import github.kasuminova.prototypemachinery.client.registry.ModelRegisterer
import github.kasuminova.prototypemachinery.client.util.ClientNextTick
import github.kasuminova.prototypemachinery.common.CommonProxy
import net.minecraftforge.client.ClientCommandHandler
import net.minecraftforge.common.MinecraftForge

internal class ClientProxy : CommonProxy() {

    init {
        MinecraftForge.EVENT_BUS.register(ModelRegisterer)
        MinecraftForge.EVENT_BUS.register(WorldProjectionManager)
        MinecraftForge.EVENT_BUS.register(ClientNextTick)
    }

    override fun preInit() {
        super.preInit()
    }

    override fun init() {
        super.init()

        // Key bindings for projection preview controls (lock/rotate orientation).
        ProjectionKeyBindings.register()

        // Client-only debug command: /pm_preview <structureId> [sliceCount]
        // and /pm_preview off
        ClientCommandHandler.instance.registerCommand(StructurePreviewClientCommand)

        // Client-only UI command: /pm_preview_ui <structureId> [sliceCount]
        ClientCommandHandler.instance.registerCommand(StructurePreviewUiClientCommand)
    }

    override fun postInit() {
        super.postInit()
    }

}