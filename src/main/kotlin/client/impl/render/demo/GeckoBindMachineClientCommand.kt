package github.kasuminova.prototypemachinery.client.impl.render.demo

import github.kasuminova.prototypemachinery.client.api.render.RenderPass
import github.kasuminova.prototypemachinery.client.api.render.binding.ClientRenderBindingApi
import github.kasuminova.prototypemachinery.client.api.render.binding.GeckoModelBinding
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.server.MinecraftServer
import net.minecraft.util.ResourceLocation
import net.minecraft.util.text.TextComponentString

/**
 * Runtime helper to bind a Gecko model to a machine type id.
 *
 * Usage:
 * - /pm_gecko_bind_machine <machineTypeId> [nameOrGeoPath] [texturePath] [namespace] [pass] [forceGlobalRenderer]
 *
 * Examples:
 * - /pm_gecko_bind_machine prototypemachinery:interactive_demo_controller dream_energy_core
 * - /pm_gecko_bind_machine prototypemachinery:interactive_demo_controller geo/dream_energy_core.geo.json textures/dream_energy_core.png modularmachinery
 * - /pm_gecko_bind_machine prototypemachinery:interactive_demo_controller dream_energy_core textures/dream_energy_core.png modularmachinery BLOOM true
 */
internal object GeckoBindMachineClientCommand : CommandBase() {

    override fun getName(): String = "pm_gecko_bind_machine"

    override fun getUsage(sender: ICommandSender): String =
        "/pm_gecko_bind_machine <machineTypeId> [nameOrGeoPath] [texturePath] [namespace] [pass] [forceGlobalRenderer]"

    override fun execute(server: MinecraftServer, sender: ICommandSender, args: Array<String>) {
        if (args.isEmpty()) {
            sender.sendMessage(TextComponentString(getUsage(sender)))
            return
        }

        val machineId = ResourceLocation(args[0])

        val namespace = args.getOrNull(3) ?: "modularmachinery"

        val pass = args.getOrNull(4)?.let(::parsePassOrNull) ?: RenderPass.DEFAULT

        val forceGlobalRenderer = args.getOrNull(5)?.let { raw ->
            raw == "1" || raw.equals("true", ignoreCase = true) || raw.equals("yes", ignoreCase = true)
        } ?: false

        val geoPathOrName = args.getOrNull(1) ?: "dream_energy_core"
        val texturePath = args.getOrNull(2) ?: "textures/${normalizeBase(geoPathOrName)}.png"

        val geoPath = if (geoPathOrName.contains('/')) geoPathOrName else "geo/${normalizeBase(geoPathOrName)}.geo.json"

        val binding = GeckoModelBinding(
            geo = ResourceLocation(namespace, geoPath),
            texture = ResourceLocation(namespace, texturePath),
            pass = pass,
            forceGlobalRenderer = forceGlobalRenderer,
        )

        ClientRenderBindingApi.bindGeckoToMachineType(machineId, binding)

        sender.sendMessage(TextComponentString("[PM] Bound gecko model to $machineId"))
        sender.sendMessage(TextComponentString("  geo=${binding.geo}"))
        sender.sendMessage(TextComponentString("  tex=${binding.texture}"))
        sender.sendMessage(TextComponentString("  pass=${binding.pass}"))
        sender.sendMessage(TextComponentString("  forceGlobalRenderer=${binding.forceGlobalRenderer}"))
        sender.sendMessage(TextComponentString("  Tip: place/target that machine in-world; rendering is submitted every frame."))
    }

    private fun normalizeBase(nameOrPath: String): String {
        return nameOrPath
            .removeSuffix(".geo.json")
            .removeSuffix(".png")
            .removeSuffix(".animation.json")
    }

    private fun parsePassOrNull(name: String): RenderPass? {
        val normalized = name.trim().uppercase()
        return RenderPass.values().firstOrNull { it.name == normalized }
    }
}
