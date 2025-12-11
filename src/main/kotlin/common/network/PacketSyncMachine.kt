package github.kasuminova.prototypemachinery.common.network

import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent
import github.kasuminova.prototypemachinery.common.block.entity.MachineBlockEntity
import io.netty.buffer.ByteBuf
import net.minecraft.client.Minecraft
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.network.ByteBufUtils
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

internal class PacketSyncMachine : IMessage {

    var pos: BlockPos = BlockPos.ORIGIN
    var componentId: String = ""
    var data: NBTTagCompound = NBTTagCompound()
    var isFullSync: Boolean = false

    constructor()

    constructor(pos: BlockPos, componentId: String, data: NBTTagCompound, isFullSync: Boolean) {
        this.pos = pos
        this.componentId = componentId
        this.data = data
        this.isFullSync = isFullSync
    }

    override fun fromBytes(buf: ByteBuf) {
        pos = BlockPos.fromLong(buf.readLong())
        componentId = ByteBufUtils.readUTF8String(buf)
        data = ByteBufUtils.readTag(buf) ?: NBTTagCompound()
        isFullSync = buf.readBoolean()
    }

    override fun toBytes(buf: ByteBuf) {
        buf.writeLong(pos.toLong())
        ByteBufUtils.writeUTF8String(buf, componentId)
        ByteBufUtils.writeTag(buf, data)
        buf.writeBoolean(isFullSync)
    }

    class Handler : IMessageHandler<PacketSyncMachine, IMessage> {
        override fun onMessage(message: PacketSyncMachine, ctx: MessageContext): IMessage? {
            Minecraft.getMinecraft().addScheduledTask {
                val world = Minecraft.getMinecraft().world ?: return@addScheduledTask
                val tile = world.getTileEntity(message.pos)
                if (tile is MachineBlockEntity) {
                    if (message.componentId.isEmpty()) {
                        // Machine level sync (if needed)
                    } else {
                        val componentType = tile.machine.type.componentTypes.find { it.id.toString() == message.componentId }
                        if (componentType != null) {
                            val component = tile.machine.componentMap.get(componentType)
                            if (component is MachineComponent.Synchronizable) {
                                component.readClientNBT(
                                    message.data,
                                    if (message.isFullSync) MachineComponent.Synchronizable.SyncType.FULL else MachineComponent.Synchronizable.SyncType.INCREMENTAL
                                )
                            }
                        }
                    }
                }
            }
            return null
        }
    }

}
