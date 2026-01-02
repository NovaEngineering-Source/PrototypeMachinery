package github.kasuminova.prototypemachinery.client.impl.render.demo

import github.kasuminova.prototypemachinery.client.impl.render.assets.CachingAssetResolver
import github.kasuminova.prototypemachinery.client.impl.render.assets.MinecraftAssetResolver
import github.kasuminova.prototypemachinery.client.impl.render.assets.MountedDirectoryAssetResolver
import github.kasuminova.prototypemachinery.client.impl.render.assets.ResolverBackedResourceManager
import github.kasuminova.prototypemachinery.client.impl.render.gecko.GeckoModelBaker
import github.kasuminova.prototypemachinery.client.util.MatrixStack
import github.kasuminova.prototypemachinery.client.util.NativeBuffers
import net.minecraft.client.Minecraft
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.server.MinecraftServer
import net.minecraft.util.ResourceLocation
import net.minecraft.util.text.TextComponentString
import org.lwjgl.opengl.GL11
import software.bernie.geckolib3.file.GeoModelLoader
import java.nio.file.Path

/**
 * CPU-side bake benchmark.
 *
 * Measures the time spent converting a GeckoLib [GeoModel] into a [BufferBuilder] (our custom baker).
 *
 * Usage:
 * - /pm_gecko_bench_bake
 * - /pm_gecko_bench_bake <name|geoPath> [iters] [namespace] [reload]
 *
 * Examples:
 * - /pm_gecko_bench_bake dream_energy_core 200 modularmachinery
 * - /pm_gecko_bench_bake geo/dream_energy_core.geo.json 200 modularmachinery true
 */
internal object GeckoBakeBenchClientCommand : CommandBase() {

    override fun getName(): String = "pm_gecko_bench_bake"

    override fun getUsage(sender: ICommandSender): String =
        "/pm_gecko_bench_bake [name|geoPath] [iters] [namespace] [reload]"

    override fun execute(server: MinecraftServer, sender: ICommandSender, args: Array<String>) {
        val geoPathOrName = args.getOrNull(0) ?: "dream_energy_core"
        val iters = args.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 100_000) ?: 200
        val namespace = args.getOrNull(2) ?: "modularmachinery"
        val reload = parseBool(args.getOrNull(3))

        val mc = Minecraft.getMinecraft()
        val mcDir = mc.gameDir.toPath()
        val resourcesRoot: Path = mcDir.resolve("resources")

        val geoPath = if (geoPathOrName.contains('/')) geoPathOrName else "geo/${normalizeBase(geoPathOrName)}.geo.json"
        val geoLoc = ResourceLocation(namespace, geoPath)

        val resolver = CachingAssetResolver(
            MountedDirectoryAssetResolver(
                delegate = MinecraftAssetResolver,
                rootDir = resourcesRoot,
                namespaces = setOf(namespace),
            )
        )
        val manager = ResolverBackedResourceManager(resolver, domains = setOf(namespace))

        sender.sendMessage(TextComponentString("[PM] Gecko bake bench started (iters=$iters, reload=$reload)"))
        sender.sendMessage(TextComponentString("  geo=$geoLoc"))

        Thread {
            var totalVertices = 0
            val t0 = System.nanoTime()

            val model0 = if (!reload) GeoModelLoader().loadModel(manager, geoLoc) else null

            for (i in 0 until iters) {
                val model = model0 ?: GeoModelLoader().loadModel(manager, geoLoc)

                val builder = NativeBuffers.newBufferBuilder(32 * 1024, tag = "GeckoBakeBench")
                builder.begin(GL11.GL_QUADS, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL)

                val ms = MatrixStack()
                ms.push()
                // Keep the same baseline as our real build task (no world translation here).
                ms.translate(0.5f, 0.0f, 0.5f)

                GeckoModelBaker.bake(
                    model = model,
                    builder = builder,
                    matrixStack = ms,
                    red = 1.0f,
                    green = 1.0f,
                    blue = 1.0f,
                    alpha = 1.0f,
                )

                ms.pop()
                builder.finishDrawing()

                totalVertices += builder.vertexCount
            }

            val t1 = System.nanoTime()
            val totalMs = (t1 - t0) / 1_000_000.0
            val avgMs = totalMs / iters
            val avgVerts = totalVertices.toDouble() / iters

            mc.addScheduledTask {
                sender.sendMessage(TextComponentString("[PM] Gecko bake bench done"))
                sender.sendMessage(TextComponentString("  total=${"%.2f".format(totalMs)}ms, avg=${"%.3f".format(avgMs)}ms/iter"))
                sender.sendMessage(TextComponentString("  avgVertices=${"%.1f".format(avgVerts)}"))
            }
        }.start()
    }

    private fun normalizeBase(nameOrPath: String): String {
        return nameOrPath
            .removeSuffix(".geo.json")
            .removeSuffix(".png")
            .removeSuffix(".animation.json")
    }

    private fun parseBool(raw: String?): Boolean {
        if (raw == null) return false
        return raw == "1" || raw.equals("true", ignoreCase = true) || raw.equals("yes", ignoreCase = true)
    }
}
