package github.kasuminova.prototypemachinery.client.impl.render.demo

import github.kasuminova.prototypemachinery.client.impl.render.assets.CachingAssetResolver
import github.kasuminova.prototypemachinery.client.impl.render.assets.MinecraftAssetResolver
import github.kasuminova.prototypemachinery.client.impl.render.assets.MountedDirectoryAssetResolver
import github.kasuminova.prototypemachinery.client.impl.render.assets.ResolverBackedResourceManager
import github.kasuminova.prototypemachinery.client.impl.render.gecko.GeckoAnimationDriver
import github.kasuminova.prototypemachinery.client.impl.render.gecko.GeckoModelBaker
import github.kasuminova.prototypemachinery.client.util.MmceMatrixStack
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
 * CPU-side animation + bake benchmark.
 *
 * This stresses GeckoLib animation evaluation (Molang + keyframes) plus our baking step.
 *
 * Usage:
 * - /pm_gecko_bench_anim <name|geoPath> <animationPath> [animationNamesCSV] [iters] [namespace] [reload]
 *
 * Examples:
 * - /pm_gecko_bench_anim dream_energy_core animations/dream_energy_core.animation.json idle 200 modularmachinery
 * - /pm_gecko_bench_anim geo/dream_energy_core.geo.json animations/dream_energy_core.animation.json idle,work 500 modularmachinery true
 */
internal object GeckoBakeAnimBenchClientCommand : CommandBase() {

    override fun getName(): String = "pm_gecko_bench_anim"

    override fun getUsage(sender: ICommandSender): String =
        "/pm_gecko_bench_anim <name|geoPath> <animationPath> [animationNamesCSV] [iters] [namespace] [reload]"

    override fun execute(server: MinecraftServer, sender: ICommandSender, args: Array<String>) {
        if (args.size < 2) {
            sender.sendMessage(TextComponentString(getUsage(sender)))
            return
        }

        val geoPathOrName = args[0]
        val animPath = args[1]
        val animationNames = args.getOrNull(2)?.let(::splitCsv)?.takeIf { it.isNotEmpty() } ?: emptyList()
        val iters = args.getOrNull(3)?.toIntOrNull()?.coerceIn(1, 100_000) ?: 200
        val namespace = args.getOrNull(4) ?: "modularmachinery"
        val reload = parseBool(args.getOrNull(5))

        val mc = Minecraft.getMinecraft()
        val mcDir = mc.gameDir.toPath()
        val resourcesRoot: Path = mcDir.resolve("resources")

        val geoPath = if (geoPathOrName.contains('/')) geoPathOrName else "geo/${normalizeBase(geoPathOrName)}.geo.json"
        val geoLoc = ResourceLocation(namespace, geoPath)
        val animLoc = ResourceLocation(namespace, animPath)

        val resolver = CachingAssetResolver(
            MountedDirectoryAssetResolver(
                delegate = MinecraftAssetResolver,
                rootDir = resourcesRoot,
                namespaces = setOf(namespace),
            )
        )
        val manager = ResolverBackedResourceManager(resolver, domains = setOf(namespace))

        sender.sendMessage(TextComponentString("[PM] Gecko anim+bake bench started (iters=$iters, reload=$reload)"))
        sender.sendMessage(TextComponentString("  geo=$geoLoc"))
        sender.sendMessage(TextComponentString("  anim=$animLoc"))
        sender.sendMessage(TextComponentString("  animations=${if (animationNames.isEmpty()) "<auto>" else animationNames.joinToString(",")}"))

        Thread {
            var totalVertices = 0
            val t0 = System.nanoTime()

            val model0 = if (!reload) GeoModelLoader().loadModel(manager, geoLoc) else null

            for (i in 0 until iters) {
                val model = model0 ?: GeoModelLoader().loadModel(manager, geoLoc)

                // Drive animations at a deterministic time.
                GeckoAnimationDriver.apply(
                    ownerKey = "bench",
                    geoModel = model,
                    animationLocation = animLoc,
                    animationNames = animationNames,
                    resourceManager = manager,
                    seekTimeTicks = (i % 200).toDouble(),
                )

                val builder = NativeBuffers.newBufferBuilder(32 * 1024, tag = "GeckoBakeAnimBench")
                builder.begin(GL11.GL_QUADS, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL)

                val ms = MmceMatrixStack()
                ms.push()
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
                sender.sendMessage(TextComponentString("[PM] Gecko anim+bake bench done"))
                sender.sendMessage(TextComponentString("  total=${"%.2f".format(totalMs)}ms, avg=${"%.3f".format(avgMs)}ms/iter"))
                sender.sendMessage(TextComponentString("  avgVertices=${"%.1f".format(avgVerts)}"))
            }
        }.start()
    }

    private fun splitCsv(raw: String): List<String> {
        return raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
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
