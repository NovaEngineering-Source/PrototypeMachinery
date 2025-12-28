package github.kasuminova.prototypemachinery.client.impl.render.demo

import github.kasuminova.prototypemachinery.client.impl.render.RenderStress
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.server.MinecraftServer
import net.minecraft.util.text.TextComponentString

/**
 * In-game render stress toggles.
 *
 * Usage:
 * - /pm_render_stress                 -> show current settings
 * - /pm_render_stress <drawMultiplier> -> repeat each draw call N times
 */
internal object RenderStressClientCommand : CommandBase() {

    override fun getName(): String = "pm_render_stress"

    override fun getUsage(sender: ICommandSender): String = "/pm_render_stress [drawMultiplier]"

    override fun execute(server: MinecraftServer, sender: ICommandSender, args: Array<String>) {
        if (args.isEmpty()) {
            sender.sendMessage(TextComponentString("[PM] Render stress settings:"))
            sender.sendMessage(TextComponentString("  drawMultiplier=${RenderStress.drawMultiplier} (repeat each draw call N times)"))
            sender.sendMessage(TextComponentString("  Tip: set back to 1 to disable."))
            return
        }

        val raw = args[0]
        val n = raw.toIntOrNull()
        if (n == null) {
            sender.sendMessage(TextComponentString("[PM] Invalid number: '$raw'"))
            sender.sendMessage(TextComponentString(getUsage(sender)))
            return
        }

        RenderStress.setDrawMultiplier(n)
        sender.sendMessage(TextComponentString("[PM] drawMultiplier set to ${RenderStress.drawMultiplier}"))
    }
}
