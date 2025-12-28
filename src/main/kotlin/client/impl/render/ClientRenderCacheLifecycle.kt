package github.kasuminova.prototypemachinery.client.impl.render

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.client.impl.render.assets.ExternalDiskTextureBinder
import github.kasuminova.prototypemachinery.client.impl.render.gecko.GeckoAnimatedBoneIndex
import github.kasuminova.prototypemachinery.client.impl.render.gecko.GeckoAnimationDriver
import github.kasuminova.prototypemachinery.client.impl.render.gecko.GeckoGeoModelInstanceCache
import github.kasuminova.prototypemachinery.client.impl.render.task.RenderTaskCache
import github.kasuminova.prototypemachinery.client.util.BufferBuilderVboCache
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.IReloadableResourceManager
import net.minecraft.client.resources.IResourceManager
import net.minecraft.client.resources.IResourceManagerReloadListener
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

/**
 * Centralized lifecycle hooks for render-related caches.
 *
 * Motivation:
 * - Several render caches intentionally keep large CPU buffers and parsed model data.
 * - If those caches are not cleared on world unload / resource reload, the client can keep
 *   strong references to TileEntities, large ByteBuffers, and GL objects, eventually causing OOM
 *   or driver memory pressure.
 */
internal object ClientRenderCacheLifecycle : IResourceManagerReloadListener {

    fun init() {
        val mc = Minecraft.getMinecraft()
        val rm = mc.resourceManager
        if (rm is IReloadableResourceManager) {
            rm.registerReloadListener(this)
        }
    }

    @SubscribeEvent
    fun onWorldUnload(event: WorldEvent.Unload) {
        // Only act for client worlds.
        if (!event.world.isRemote) return
        clearAll("world_unload")
    }

    override fun onResourceManagerReload(resourceManager: IResourceManager) {
        clearAll("resource_reload")
    }

    private fun clearAll(reason: String) {
        val mc = Minecraft.getMinecraft()

        // Always schedule onto the main thread. Some operations delete GL resources.
        mc.addScheduledTask {
            val taskSizes = RenderTaskCache.sizeSnapshot()
            PrototypeMachinery.logger.info(
                "[RenderCaches] clearAll(reason={}): tasks={}, nextTasks={}, geoModelCache={}, geckoRuntimes={}, animBoneIndex={}, externalTex(entries={}, dynTex={})",
                reason,
                taskSizes.tasks,
                taskSizes.nextTasks,
                GeckoGeoModelInstanceCache.size(),
                GeckoAnimationDriver.runtimeCount(),
                GeckoAnimatedBoneIndex.cacheSize(),
                ExternalDiskTextureBinder.entryCount(),
                ExternalDiskTextureBinder.dynamicTextureCount(),
            )

            // CPU-side build task caches (can retain large direct buffers and TE references)
            RenderTaskCache.clearAll()

            // Gecko caches
            GeckoGeoModelInstanceCache.clearAll()
            GeckoAnimationDriver.clearAll()
            GeckoAnimatedBoneIndex.invalidateAll()

            // Render queues + reusable VBOs
            MachineRenderDispatcher.clearAll()
            RenderManager.clearAll()

            // Per-buffer VBO cache
            BufferBuilderVboCache.clearAll(reason)

            // External disk texture cache (DynamicTexture -> GL texture)
            ExternalDiskTextureBinder.clearAll()
        }
    }
}
