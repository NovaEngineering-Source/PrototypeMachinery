package github.kasuminova.prototypemachinery.client.preview

import github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI
import net.minecraft.client.Minecraft
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.util.math.BlockPos
import net.minecraft.util.text.TextComponentString
import net.minecraft.util.text.TextComponentTranslation

/**
 * Client-side helper command for structure preview debugging.
 *
 * 用于调试结构预览的客户端命令。
 *
 * Usage:
 * - /pm_preview <structureId> [sliceCount]
 * - /pm_preview off
 */
internal object StructurePreviewClientCommand : CommandBase() {

    override fun getName(): String = "pm_preview"

    override fun getUsage(sender: ICommandSender): String = "/pm_preview <structureId> [sliceCount] [distance] [mode] | /pm_preview off"

    override fun execute(server: net.minecraft.server.MinecraftServer, sender: ICommandSender, args: Array<String>) {
        val mc = Minecraft.getMinecraft()
        val player = mc.player ?: return

        if (args.isEmpty()) {
            sender.sendMessage(TextComponentString(getUsage(sender)))
            return
        }

        if (args[0].equals("off", ignoreCase = true) || args[0].equals("stop", ignoreCase = true)) {
            WorldProjectionManager.stop()
            sender.sendMessage(TextComponentTranslation("pm.preview.stopped"))
            return
        }

        val id = args[0]
        val structure = PrototypeMachineryAPI.structureRegistry.get(id)
        if (structure == null) {
            sender.sendMessage(TextComponentTranslation("pm.preview.unknown_structure", id))
            return
        }

        val ray = mc.objectMouseOver
        val anchor: BlockPos = if (ray != null && ray.typeOfHit == net.minecraft.util.math.RayTraceResult.Type.BLOCK) {
            ray.blockPos
        } else {
            player.position
        }

        val sliceCount = args.getOrNull(1)?.toIntOrNull()
        val distance = args.getOrNull(2)?.toDoubleOrNull()
        val mode = args.getOrNull(3)?.lowercase()

        val visualMode = when (mode) {
            null, "ghost" -> ProjectionVisualMode.GHOST
            "outline" -> ProjectionVisualMode.OUTLINE
            "both" -> ProjectionVisualMode.BOTH
            "block", "model", "block_model" -> ProjectionVisualMode.BLOCK_MODEL
            else -> ProjectionVisualMode.GHOST
        }

        WorldProjectionManager.start(
            StructureProjectionSession(
                structureId = id,
                anchor = anchor,
                sliceCountOverride = sliceCount,
                followPlayerFacing = true,
                maxRenderDistance = distance,
                renderMode = ProjectionRenderMode.ALL,
                visualMode = visualMode
            )
        )

        sender.sendMessage(TextComponentTranslation("pm.preview.started", id, anchor.x, anchor.y, anchor.z))
    }

    override fun getTabCompletions(
        server: net.minecraft.server.MinecraftServer,
        sender: ICommandSender,
        args: Array<String>,
        targetPos: BlockPos?
    ): MutableList<String> {
        if (args.isEmpty()) return mutableListOf()

        // arg0: structure id | off | stop
        if (args.size == 1) {
            val ids = try {
                PrototypeMachineryAPI.structureRegistry.getAll().map { it.id }
            } catch (_: Throwable) {
                emptyList()
            }
            val options = ArrayList<String>(2 + ids.size)
            options.add("off")
            options.add("stop")
            options.addAll(ids)
            return getListOfStringsMatchingLastWord(args, options)
        }

        // If turning off, no further completions.
        if (args[0].equals("off", ignoreCase = true) || args[0].equals("stop", ignoreCase = true)) {
            return mutableListOf()
        }

        // arg1: sliceCount
        if (args.size == 2) {
            val options = listOf("1", "2", "3", "4", "6", "8", "12", "16")
            return getListOfStringsMatchingLastWord(args, options)
        }

        // arg2: distance
        if (args.size == 3) {
            val options = listOf("16", "24", "32", "48", "64", "96", "128")
            return getListOfStringsMatchingLastWord(args, options)
        }

        // arg3: mode
        if (args.size == 4) {
            val options = listOf("ghost", "outline", "both", "block", "model", "block_model")
            return getListOfStringsMatchingLastWord(args, options)
        }

        return mutableListOf()
    }

    override fun getRequiredPermissionLevel(): Int = 0
}
