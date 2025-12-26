package github.kasuminova.prototypemachinery.client

import github.kasuminova.prototypemachinery.client.atlas.PmGuiAtlas
import github.kasuminova.prototypemachinery.client.impl.render.WorldRenderFlushHandler
import github.kasuminova.prototypemachinery.client.impl.render.binding.MachineBlockEntitySpecialRenderer
import github.kasuminova.prototypemachinery.client.impl.render.bloom.GregTechBloomBridge
import github.kasuminova.prototypemachinery.client.impl.render.demo.GeckoBindMachineClientCommand
import github.kasuminova.prototypemachinery.client.impl.render.demo.GeckoSmokeClientCommand
import github.kasuminova.prototypemachinery.client.impl.render.demo.MachineIdClientCommand
import github.kasuminova.prototypemachinery.client.impl.world.BlockModelHideManager
import github.kasuminova.prototypemachinery.client.model.ControllerModelBakeHandler
import github.kasuminova.prototypemachinery.client.preview.ProjectionKeyBindings
import github.kasuminova.prototypemachinery.client.preview.StructurePreviewClientCommand
import github.kasuminova.prototypemachinery.client.preview.WorldProjectionManager
import github.kasuminova.prototypemachinery.client.preview.ui.StructurePreviewUiClientCommand
import github.kasuminova.prototypemachinery.client.registry.ModelRegisterer
import github.kasuminova.prototypemachinery.client.util.ClientNextTick
import github.kasuminova.prototypemachinery.common.CommonProxy
import github.kasuminova.prototypemachinery.common.block.entity.MachineBlockEntity
import net.minecraftforge.client.ClientCommandHandler
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.client.registry.ClientRegistry

internal class ClientProxy : CommonProxy() {

    init {
        MinecraftForge.EVENT_BUS.register(ModelRegisterer)
        MinecraftForge.EVENT_BUS.register(ControllerModelBakeHandler)
        MinecraftForge.EVENT_BUS.register(WorldProjectionManager)
        MinecraftForge.EVENT_BUS.register(ClientNextTick)
        MinecraftForge.EVENT_BUS.register(BlockModelHideManager)
        MinecraftForge.EVENT_BUS.register(WorldRenderFlushHandler)
    }

    override fun preInit() {
        super.preInit()
    }

    override fun init() {
        super.init()

        // Render bound machine models during the normal TileEntity render stage.
        // This ensures correct ordering relative to GT bloom post-processing.
        ClientRegistry.bindTileEntitySpecialRenderer(MachineBlockEntity::class.java, MachineBlockEntitySpecialRenderer())

        // Optional GTCE bloom post-processing integration (reflection-based).
        GregTechBloomBridge.initIfPresent()

        // GUI atlases (TextureMap-based, uses Stitcher internally)
        PmGuiAtlas.init()

        // Key bindings for projection preview controls (lock/rotate orientation).
        ProjectionKeyBindings.register()

        // Client-only debug command: /pm_preview <structureId> [sliceCount]
        // and /pm_preview off
        ClientCommandHandler.instance.registerCommand(StructurePreviewClientCommand)

        // Client-only UI command: /pm_preview_ui <structureId> [sliceCount]
        ClientCommandHandler.instance.registerCommand(StructurePreviewUiClientCommand)

        // Dev-only: load GeckoLib geo/animation from <mcDir>/resources/<namespace>/...
        ClientCommandHandler.instance.registerCommand(GeckoSmokeClientCommand)

        // Dev-only: bind Gecko model to a machine type id at runtime
        ClientCommandHandler.instance.registerCommand(GeckoBindMachineClientCommand)

        // Dev-only: print machine id of targeted controller
        ClientCommandHandler.instance.registerCommand(MachineIdClientCommand)
    }

    override fun postInit() {
        super.postInit()
    }

}