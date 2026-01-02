package github.kasuminova.prototypemachinery.common.command

import github.kasuminova.prototypemachinery.common.network.NetworkHandler
import github.kasuminova.prototypemachinery.common.network.PacketReloadStructures
import github.kasuminova.prototypemachinery.common.structure.loader.StructureLoader
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.server.MinecraftServer
import net.minecraft.util.text.TextComponentString

/**
 * Admin command for structure hot reload.
 *
 * Usage:
 * - /pm_structures reload
 */
internal object PmStructuresServerCommand : CommandBase() {

    override fun getName(): String = "pm_structures"

    override fun getUsage(sender: ICommandSender): String = "/pm_structures reload"

    override fun getRequiredPermissionLevel(): Int = 2

    override fun execute(server: MinecraftServer, sender: ICommandSender, args: Array<String>) {
        val sub = args.getOrNull(0)?.lowercase()
        if (sub != "reload") {
            sender.sendMessage(TextComponentString(getUsage(sender)))
            return
        }

        val report = StructureLoader.reloadFromDisk(replaceRegistry = true)
        sender.sendMessage(
            TextComponentString(
                "[PM] structures reloaded (server): ok=${report.ok}, files=${report.filesScanned}, loaded=${report.structuresLoaded}, converted=${report.structuresConverted}, errors=${report.errors}"
            )
        )

        // Notify clients to reload from their local disk & drop preview caches.
        val nonce = System.currentTimeMillis()
        val pkt = PacketReloadStructures(nonce)
        for (p in server.playerList.players) {
            if (p is EntityPlayerMP) {
                NetworkHandler.INSTANCE.sendTo(pkt, p)
            }
        }
    }
}
