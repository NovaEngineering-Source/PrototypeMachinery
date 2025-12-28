package github.kasuminova.prototypemachinery.client

import com.cleanroommc.modularui.value.sync.PanelSyncManager
import com.cleanroommc.modularui.widgets.layout.Flow
import github.kasuminova.prototypemachinery.client.atlas.PmGuiAtlas
import github.kasuminova.prototypemachinery.client.buildinstrument.BuildInstrumentClientUi
import github.kasuminova.prototypemachinery.client.impl.render.ClientRenderCacheLifecycle
import github.kasuminova.prototypemachinery.client.impl.render.RenderDebugHud
import github.kasuminova.prototypemachinery.client.impl.render.RenderFrameClock
import github.kasuminova.prototypemachinery.client.impl.render.WorldRenderFlushHandler
import github.kasuminova.prototypemachinery.client.impl.render.binding.MachineBlockEntitySpecialRenderer
import github.kasuminova.prototypemachinery.client.impl.render.bloom.GregTechBloomBridge
import github.kasuminova.prototypemachinery.client.impl.render.demo.GeckoBakeAnimBenchClientCommand
import github.kasuminova.prototypemachinery.client.impl.render.demo.GeckoBakeBenchClientCommand
import github.kasuminova.prototypemachinery.client.impl.render.demo.GeckoBindMachineClientCommand
import github.kasuminova.prototypemachinery.client.impl.render.demo.GeckoSmokeClientCommand
import github.kasuminova.prototypemachinery.client.impl.render.demo.MachineIdClientCommand
import github.kasuminova.prototypemachinery.client.impl.render.demo.RenderHudClientCommand
import github.kasuminova.prototypemachinery.client.impl.render.demo.RenderStressClientCommand
import github.kasuminova.prototypemachinery.client.impl.render.gecko.GeckoAnimationDriver
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
import net.minecraft.nbt.NBTTagCompound
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
        MinecraftForge.EVENT_BUS.register(RenderFrameClock)
        MinecraftForge.EVENT_BUS.register(RenderDebugHud)
        MinecraftForge.EVENT_BUS.register(ClientRenderCacheLifecycle)
    }

    override fun preInit() {
        super.preInit()
    }

    override fun init() {
        super.init()

        // GeckoLib animation integration: register ModelFetcher once.
        GeckoAnimationDriver.init()

        // Render bound machine models during the normal TileEntity render stage.
        // This ensures correct ordering relative to GT bloom post-processing.
        ClientRegistry.bindTileEntitySpecialRenderer(MachineBlockEntity::class.java, MachineBlockEntitySpecialRenderer())

        // Optional GTCE bloom post-processing integration (reflection-based).
        GregTechBloomBridge.initIfPresent()

        // GUI atlases (TextureMap-based, uses Stitcher internally)
        PmGuiAtlas.init()

        // Clear render caches on resource reload / world unload to avoid leaks.
        ClientRenderCacheLifecycle.init()

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

        // Perf/stress tools
        ClientCommandHandler.instance.registerCommand(RenderStressClientCommand)
        ClientCommandHandler.instance.registerCommand(RenderHudClientCommand)
        ClientCommandHandler.instance.registerCommand(GeckoBakeBenchClientCommand)
        ClientCommandHandler.instance.registerCommand(GeckoBakeAnimBenchClientCommand)
    }

    override fun postInit() {
        super.postInit()
    }

    override fun addBuildInstrumentClientWidgets(root: Flow, tagProvider: () -> NBTTagCompound?, syncManager: PanelSyncManager) {
        BuildInstrumentClientUi.addWidgets(root, tagProvider, syncManager)
    }

}