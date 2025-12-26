package github.kasuminova.prototypemachinery.client.impl.render.demo

import github.kasuminova.prototypemachinery.client.impl.render.assets.MinecraftAssetResolver
import github.kasuminova.prototypemachinery.client.impl.render.assets.MountedDirectoryAssetResolver
import github.kasuminova.prototypemachinery.client.impl.render.assets.ResolverBackedResourceManager
import net.minecraft.client.Minecraft
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.server.MinecraftServer
import net.minecraft.util.ResourceLocation
import net.minecraft.util.text.TextComponentString
import software.bernie.geckolib3.file.AnimationFileLoader
import software.bernie.geckolib3.file.GeoModelLoader
import software.bernie.geckolib3.geo.render.built.GeoBone
import software.bernie.shadowed.eliotlash.molang.MolangParser
import java.nio.file.Path

/**
 * Dev-only client command to smoke-test GeckoLib asset loading from disk.
 *
 * Usage:
 * - /pm_gecko_smoke
 * - /pm_gecko_smoke <name>
 * - /pm_gecko_smoke <geoPath> <animationPath> [namespace]
 */
internal object GeckoSmokeClientCommand : CommandBase() {

    override fun getName(): String = "pm_gecko_smoke"

    override fun getUsage(sender: ICommandSender): String =
        "/pm_gecko_smoke [name|geoPath animationPath [namespace]]"

    override fun execute(server: MinecraftServer, sender: ICommandSender, args: Array<String>) {
        val namespace = args.getOrNull(2) ?: "modularmachinery"

        val (geoPath, animPath) = when (args.size) {
            0 -> defaultPair("dream_energy_core")
            1 -> defaultPair(args[0])
            else -> args[0] to args[1]
        }

        val mcDir = Minecraft.getMinecraft().gameDir.toPath()
        val resourcesRoot: Path = mcDir.resolve("resources")

        val resolver = MountedDirectoryAssetResolver(
            delegate = MinecraftAssetResolver,
            rootDir = resourcesRoot,
            namespaces = setOf(namespace),
        )
        val manager = ResolverBackedResourceManager(resolver, domains = setOf(namespace))

        val geoLoc = ResourceLocation(namespace, geoPath)
        val animLoc = ResourceLocation(namespace, animPath)

        val t0 = System.nanoTime()
        val model = GeoModelLoader().loadModel(manager, geoLoc)
        val t1 = System.nanoTime()
        val anims = AnimationFileLoader().loadAllAnimations(MolangParser(), animLoc, manager)
        val t2 = System.nanoTime()

        val totalBones = countBones(model.topLevelBones)
        val totalCubes = countCubes(model.topLevelBones)

        sender.sendMessage(TextComponentString("[PM] Gecko smoke ok"))
        sender.sendMessage(TextComponentString("  geo=$geoLoc"))
        sender.sendMessage(TextComponentString("  anim=$animLoc"))
        sender.sendMessage(TextComponentString("  topBones=${model.topLevelBones.size}, bones=$totalBones, cubes=$totalCubes"))
        sender.sendMessage(TextComponentString("  animations=${anims.getAllAnimations().size}"))
        sender.sendMessage(TextComponentString("  time: geo=${(t1 - t0) / 1_000_000}ms, anim=${(t2 - t1) / 1_000_000}ms"))
        sender.sendMessage(TextComponentString("  mountedRoot=$resourcesRoot"))
    }

    private fun defaultPair(nameOrPath: String): Pair<String, String> {
        val base = nameOrPath
            .removeSuffix(".geo.json")
            .removeSuffix(".animation.json")

        return "geo/${base}.geo.json" to "animations/${base}.animation.json"
    }

    private fun countBones(roots: List<GeoBone>): Int {
        var count = 0
        val stack = ArrayDeque<GeoBone>()
        roots.forEach { stack.addLast(it) }
        while (stack.isNotEmpty()) {
            val b = stack.removeLast()
            count++
            b.childBones?.forEach { stack.addLast(it) }
        }
        return count
    }

    private fun countCubes(roots: List<GeoBone>): Int {
        var count = 0
        val stack = ArrayDeque<GeoBone>()
        roots.forEach { stack.addLast(it) }
        while (stack.isNotEmpty()) {
            val b = stack.removeLast()
            count += (b.childCubes?.size ?: 0)
            b.childBones?.forEach { stack.addLast(it) }
        }
        return count
    }
}
