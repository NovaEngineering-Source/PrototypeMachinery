package github.kasuminova.prototypemachinery.client.impl.render.assets

import github.kasuminova.prototypemachinery.PrototypeMachinery
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.renderer.texture.TextureUtil
import net.minecraft.util.ResourceLocation
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Binds textures with a fallback loader that reads from the local game directory.
 *
 * Why: our Gecko/asset pipeline can mount resources under `<gameDir>/resources/<namespace>/...`,
 * but vanilla TextureManager only looks inside resource packs (assets/...) and will warn+fail.
 *
 * This helper preloads/registers a [DynamicTexture] for the given [ResourceLocation] when a
 * matching PNG exists on disk.
 */
internal object ExternalDiskTextureBinder {

    /**
     * How often we allow checking disk state per texture.
     *
     * IMPORTANT: bind() is called on the render thread. We must avoid disk I/O here.
     */
    private const val CHECK_INTERVAL_MS: Long = 1_000L

    private data class Entry(
        @Volatile var lastModifiedMillis: Long = Long.MIN_VALUE,
        @Volatile var nextCheckAtMillis: Long = 0L,
        val inFlight: AtomicBoolean = AtomicBoolean(false),
    )

    private val entries = ConcurrentHashMap<ResourceLocation, Entry>()

    /**
     * Invalidate in-flight loads when caches are cleared.
     *
     * This avoids background decode jobs scheduling stale uploads back onto the main thread
     * after a world unload / resource reload.
     */
    private val generation = AtomicLong(0)

    private val ioExecutor = Executors.newSingleThreadExecutor(
        object : ThreadFactory {
            private val n = java.util.concurrent.atomic.AtomicInteger(0)
            override fun newThread(r: Runnable): Thread {
                return Thread(r, "PM-ExternalTexLoader-${n.incrementAndGet()}").apply {
                    isDaemon = true
                    priority = Thread.NORM_PRIORITY - 1
                }
            }
        }
    )

    private val dynamicTextures = Object2ObjectOpenHashMap<ResourceLocation, DynamicTexture>()

    internal fun entryCount(): Int = entries.size

    internal fun dynamicTextureCount(): Int = dynamicTextures.size

    /**
     * Clear all cached external textures.
     *
     * Must be safe to call during world unload / resource reload.
     */
    fun clearAll() {
        // Invalidate any in-flight work first.
        generation.incrementAndGet()
        entries.clear()

        val mc = Minecraft.getMinecraft()
        mc.addScheduledTask {
            val it = dynamicTextures.values.iterator()
            while (it.hasNext()) {
                runCatching { it.next().deleteGlTexture() }
            }
            dynamicTextures.clear()
        }
    }

    fun bind(texture: ResourceLocation) {
        // Non-blocking: schedule disk load on a background thread if needed.
        // GL texture upload (loadTexture) is scheduled back onto the main thread.
        prefetch(texture)
        Minecraft.getMinecraft().renderEngine.bindTexture(texture)
    }

    /**
     * Request (asynchronously) that this texture be loaded from disk if present.
     *
     * Safe to call from any thread.
     */
    fun prefetch(texture: ResourceLocation) {
        val now = System.currentTimeMillis()
        val entry = entries.computeIfAbsent(texture) { Entry() }

        val genAtSubmit = generation.get()

        // Fast path: throttle checks.
        if (now < entry.nextCheckAtMillis) return

        // Avoid queueing duplicate jobs.
        if (!entry.inFlight.compareAndSet(false, true)) return

        // Set next check *before* doing work to bound queue growth.
        entry.nextCheckAtMillis = now + CHECK_INTERVAL_MS

        val path = resolveDiskPath(texture)

        ioExecutor.execute {
            try {
                if (!Files.isRegularFile(path)) return@execute

                val mtime = runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrElse { return@execute }
                if (entry.lastModifiedMillis == mtime) return@execute

                val image: BufferedImage = try {
                    Files.newInputStream(path).use { input ->
                        TextureUtil.readBufferedImage(input)
                    }
                } catch (t: Throwable) {
                    PrototypeMachinery.logger.warn("Failed to decode external texture from disk: $texture ($path)", t)
                    return@execute
                }

                // Upload must happen on the MC main thread with a valid GL context.
                val mc = Minecraft.getMinecraft()
                mc.addScheduledTask {
                    // World unload / resource reload happened while we were decoding.
                    if (genAtSubmit != generation.get()) {
                        return@addScheduledTask
                    }
                    try {
                        val previous = dynamicTextures.put(texture, DynamicTexture(image))
                        previous?.deleteGlTexture()
                        mc.renderEngine.loadTexture(texture, dynamicTextures.getValue(texture))
                        entry.lastModifiedMillis = mtime
                    } catch (t: Throwable) {
                        PrototypeMachinery.logger.warn("Failed to upload external texture to GL: $texture ($path)", t)
                    }
                }
            } finally {
                entry.inFlight.set(false)
            }
        }
    }

    private fun resolveDiskPath(texture: ResourceLocation): Path {
        // Mirrors MountedDirectoryAssetResolver mapping: <gameDir>/resources/<namespace>/<path>
        val mcDir = Minecraft.getMinecraft().gameDir.toPath()
        return mcDir
            .resolve("resources")
            .resolve(texture.namespace)
            .resolve(texture.path)
    }
}
