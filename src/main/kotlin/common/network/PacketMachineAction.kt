package github.kasuminova.prototypemachinery.common.network

import github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI
import github.kasuminova.prototypemachinery.common.block.entity.MachineBlockEntity
import io.netty.buffer.ByteBuf
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.network.ByteBufUtils
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

public class PacketMachineAction() : IMessage {
    public var pos: BlockPos = BlockPos.ORIGIN
    public var actionKey: String = ""
    public var payload: NBTTagCompound = NBTTagCompound()

    public constructor(pos: BlockPos, actionKey: String, payload: NBTTagCompound = NBTTagCompound()) : this() {
        this.pos = pos
        this.actionKey = actionKey
        this.payload = payload
    }

    override fun fromBytes(buf: ByteBuf) {
        pos = BlockPos.fromLong(buf.readLong())
        val length = buf.readInt()
        val bytes = ByteArray(length)
        buf.readBytes(bytes)
        actionKey = String(bytes, Charsets.UTF_8)

        payload = ByteBufUtils.readTag(buf) ?: NBTTagCompound()
    }

    override fun toBytes(buf: ByteBuf) {
        buf.writeLong(pos.toLong())
        val bytes = actionKey.toByteArray(Charsets.UTF_8)
        buf.writeInt(bytes.size)
        buf.writeBytes(bytes)

        ByteBufUtils.writeTag(buf, payload)
    }

    private companion object {
        // Built-in action prefix: toggle a writable bool binding.
        // Example: "prototypemachinery:toggle_bool:some_key"
        private const val TOGGLE_BOOL_PREFIX = "prototypemachinery:toggle_bool:"
    }

    public class Handler : IMessageHandler<PacketMachineAction, IMessage> {
        override fun onMessage(message: PacketMachineAction, ctx: MessageContext): IMessage? {
            val player = ctx.serverHandler.player
            player.serverWorld.addScheduledTask {
                if (player.world.isBlockLoaded(message.pos)) {
                    val tile = player.world.getTileEntity(message.pos)
                    if (tile is MachineBlockEntity) {
                        val handled = PrototypeMachineryAPI.uiActionRegistry.invoke(
                            player,
                            tile,
                            message.actionKey,
                            message.payload
                        )

                        if (!handled) {
                            // Built-in fallback actions (no explicit registration needed)
                            val key = message.actionKey.trim()
                            if (key.startsWith(TOGGLE_BOOL_PREFIX)) {
                                val bindKey = key.removePrefix(TOGGLE_BOOL_PREFIX).trim()
                                val resolved = PrototypeMachineryAPI.uiBindingRegistry.resolveBool(tile.machine, bindKey)
                                val setter = resolved?.setter
                                if (resolved != null && setter != null) {
                                    val current = resolved.getter.invoke(tile.machine)
                                    setter.invoke(tile.machine, !current)
                                }
                            }
                        }
                    }
                }
            }
            return null
        }
    }
}
