package github.kasuminova.prototypemachinery.client.impl.render.demo

import github.kasuminova.prototypemachinery.common.block.entity.MachineBlockEntity
import net.minecraft.client.Minecraft
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.text.TextComponentString

/**
 * Prints the machine type id of the MachineBlockEntity the player is looking at.
 */
internal object MachineIdClientCommand : CommandBase() {

    override fun getName(): String = "pm_machine_id"

    override fun getUsage(sender: ICommandSender): String = "/pm_machine_id"

    override fun execute(server: MinecraftServer, sender: ICommandSender, args: Array<String>) {
        val mc = Minecraft.getMinecraft()
        val world = mc.world ?: return
        val hit = mc.objectMouseOver

        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK || hit.blockPos == null) {
            sender.sendMessage(TextComponentString("[PM] No block targeted."))
            return
        }

        val te = world.getTileEntity(hit.blockPos)
        val machineTe = te as? MachineBlockEntity
        if (machineTe == null) {
            sender.sendMessage(TextComponentString("[PM] Targeted tile is not a MachineBlockEntity: ${te?.javaClass?.name}"))
            return
        }

        sender.sendMessage(TextComponentString("[PM] MachineType: ${machineTe.machine.type.id}"))
    }
}
