package github.kasuminova.prototypemachinery.client

import github.kasuminova.prototypemachinery.client.preview.ProjectionKeyBindings
import github.kasuminova.prototypemachinery.client.preview.StructurePreviewClientCommand
import github.kasuminova.prototypemachinery.client.preview.WorldProjectionManager
import github.kasuminova.prototypemachinery.client.registry.ModelRegisterer
import github.kasuminova.prototypemachinery.common.CommonProxy
import net.minecraftforge.client.ClientCommandHandler
import net.minecraftforge.common.MinecraftForge

internal class ClientProxy : CommonProxy() {

    init {
        MinecraftForge.EVENT_BUS.register(ModelRegisterer)
        MinecraftForge.EVENT_BUS.register(WorldProjectionManager)
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
    }

    override fun postInit() {
        super.postInit()
    }

}