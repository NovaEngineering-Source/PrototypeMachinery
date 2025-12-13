package github.kasuminova.prototypemachinery.common.network

import github.kasuminova.prototypemachinery.PrototypeMachinery
import net.minecraftforge.fml.common.network.NetworkRegistry
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper
import net.minecraftforge.fml.relauncher.Side

internal object NetworkHandler {

    val INSTANCE: SimpleNetworkWrapper = NetworkRegistry.INSTANCE.newSimpleChannel(PrototypeMachinery.MOD_ID)
    private var packetId = 0

    fun init() {
        registerPacket(PacketSyncMachine::class.java, PacketSyncMachine.Handler::class.java, Side.CLIENT)
        registerPacket(PacketMachineAction::class.java, PacketMachineAction.Handler::class.java, Side.SERVER)
    }

    private fun <REQ : IMessage, REPLY : IMessage> registerPacket(
        requestMessageType: Class<REQ>,
        messageHandler: Class<out IMessageHandler<REQ, REPLY>>,
        side: Side
    ) {
        INSTANCE.registerMessage(messageHandler, requestMessageType, packetId++, side)
    }

}
