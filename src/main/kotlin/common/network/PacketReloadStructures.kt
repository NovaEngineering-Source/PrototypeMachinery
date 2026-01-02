package github.kasuminova.prototypemachinery.common.network

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.client.preview.WorldProjectionManager
import github.kasuminova.prototypemachinery.common.structure.loader.StructureLoader
import io.netty.buffer.ByteBuf
import net.minecraft.client.Minecraft
import net.minecraft.util.text.TextComponentString
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

/**
 * Server -> Client notification: structure JSON has been reloaded on the server.
 *
 * We intentionally do NOT sync JSON payload (packet size & modpack workflow concerns).
 * The client will re-read its local config/prototypemachinery/structures and rebuild registry.
 */
internal class PacketReloadStructures : IMessage {

    // A small nonce for log/debug; not strictly required.
    private var nonce: Long = 0L

    constructor()

    constructor(nonce: Long) {
        this.nonce = nonce
    }

    override fun fromBytes(buf: ByteBuf) {
        nonce = buf.readLong()
    }

    override fun toBytes(buf: ByteBuf) {
        buf.writeLong(nonce)
    }

    internal class Handler : IMessageHandler<PacketReloadStructures, IMessage> {
        override fun onMessage(message: PacketReloadStructures, ctx: MessageContext): IMessage? {
            Minecraft.getMinecraft().addScheduledTask {
                runCatching {
                    val report = StructureLoader.reloadFromDisk(replaceRegistry = true)
                    WorldProjectionManager.onStructuresReloaded()

                    val player = Minecraft.getMinecraft().player
                    if (player != null) {
                        player.sendMessage(
                            TextComponentString(
                                "[PM] structures reloaded (client): ok=${report.ok}, loaded=${report.structuresLoaded}, converted=${report.structuresConverted}, errors=${report.errors}"
                            )
                        )
                    }
                }.onFailure {
                    PrototypeMachinery.logger.warn("[PM] client structure reload failed (nonce=${message.nonce})", it)
                    val player = Minecraft.getMinecraft().player
                    player?.sendMessage(TextComponentString("[PM] structures reload failed on client: ${it.message}"))
                }
            }
            return null
        }
    }
}
