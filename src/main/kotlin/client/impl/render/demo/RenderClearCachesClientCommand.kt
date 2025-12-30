package github.kasuminova.prototypemachinery.client.impl.render.demo

import github.kasuminova.prototypemachinery.client.impl.render.ClientRenderCacheLifecycle
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.server.MinecraftServer
import net.minecraft.util.text.TextComponentString

/**
 * Force-clear PM render caches at runtime.
 *
 * Useful for profiling Gecko baking paths: ensures the next render requires rebuilding tasks.
 *
 * Usage:
 * - /pm_render_clear_caches
 * - /pm_render_clear_caches <reason>
 */
internal object RenderClearCachesClientCommand : CommandBase() {

    override fun getName(): String = "pm_render_clear_caches"

    override fun getUsage(sender: ICommandSender): String = "/pm_render_clear_caches [reason]"

    override fun execute(server: MinecraftServer, sender: ICommandSender, args: Array<String>) {
        val reason = args.getOrNull(0) ?: "manual"
        ClientRenderCacheLifecycle.clearAllForDebug(reason)
        sender.sendMessage(TextComponentString("[PM] Requested render cache clear (reason=$reason)."))
    }
}
