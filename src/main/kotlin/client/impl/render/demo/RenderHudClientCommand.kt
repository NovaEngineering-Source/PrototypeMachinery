package github.kasuminova.prototypemachinery.client.impl.render.demo

import github.kasuminova.prototypemachinery.client.impl.render.RenderDebugHud
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.server.MinecraftServer
import net.minecraft.util.text.TextComponentString

/**
 * Toggle PM render debug HUD.
 *
 * Usage:
 * - /pm_render_hud
 * - /pm_render_hud on|off|toggle
 */
internal object RenderHudClientCommand : CommandBase() {

    override fun getName(): String = "pm_render_hud"

    override fun getUsage(sender: ICommandSender): String = "/pm_render_hud [on|off|toggle]"

    override fun execute(server: MinecraftServer, sender: ICommandSender, args: Array<String>) {
        val mode = args.getOrNull(0)?.lowercase()

        val next = when (mode) {
            null, "toggle" -> !RenderDebugHud.enabled
            "on", "1", "true", "yes" -> true
            "off", "0", "false", "no" -> false
            else -> {
                sender.sendMessage(TextComponentString("[PM] Unknown arg: '$mode'"))
                sender.sendMessage(TextComponentString(getUsage(sender)))
                return
            }
        }

        RenderDebugHud.enabled = next
        sender.sendMessage(TextComponentString("[PM] Render HUD ${if (next) "enabled" else "disabled"}"))
    }
}
