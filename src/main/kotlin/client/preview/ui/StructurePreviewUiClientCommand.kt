package github.kasuminova.prototypemachinery.client.preview.ui

import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.util.math.BlockPos
import net.minecraft.util.text.TextComponentString

/**
 * Client-only command that opens a read-only ModularUI screen for structure preview.
 *
 * Usage:
 * - /pm_preview_ui <structureId> [sliceCount]
 */
internal object StructurePreviewUiClientCommand : CommandBase() {

    override fun getName(): String = "pm_preview_ui"

    override fun getUsage(sender: ICommandSender): String = "/pm_preview_ui <structureId> [sliceCount]"

    override fun execute(server: net.minecraft.server.MinecraftServer, sender: ICommandSender, args: Array<String>) {
        if (args.isEmpty()) {
            sender.sendMessage(TextComponentString(getUsage(sender)))
            return
        }

        val id = args[0]
        val sliceCount = args.getOrNull(1)?.toIntOrNull()

        // Small feedback so users can distinguish "client command executed" from "sent to server".
        sender.sendMessage(TextComponentString("[PM] Opening preview UI: $id"))
        StructurePreviewUiScreen.open(structureId = id, sliceCountOverride = sliceCount)
    }

    override fun getTabCompletions(
        server: net.minecraft.server.MinecraftServer,
        sender: ICommandSender,
        args: Array<String>,
        targetPos: BlockPos?
    ): MutableList<String> {
        // Reuse /pm_preview completion semantics would be ideal; keep minimal for now.
        return mutableListOf()
    }

    override fun getRequiredPermissionLevel(): Int = 0
}
