package github.kasuminova.prototypemachinery.common.command

import github.kasuminova.prototypemachinery.common.config.PrototypeMachineryCommonConfig
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.BlockPos
import net.minecraft.util.text.TextComponentString
import net.minecraftforge.common.config.ConfigCategory
import net.minecraftforge.common.config.Property

internal object PmConfigServerCommand : CommandBase() {

    override fun getName(): String = "pm_config"

    override fun getUsage(sender: ICommandSender): String =
        "/pm_config reload | list [category] | get <category> <name> | set <category> <name> <value>"

    override fun getRequiredPermissionLevel(): Int = 2

    override fun execute(server: MinecraftServer, sender: ICommandSender, args: Array<String>) {
        if (args.isEmpty()) {
            sender.sendMessage(TextComponentString(getUsage(sender)))
            return
        }

        val sub = args[0].lowercase()
        when (sub) {
            "reload" -> {
                val ok = PrototypeMachineryCommonConfig.reloadFromDisk()
                sender.sendMessage(
                    TextComponentString(
                        if (ok) "[PM] config reloaded & applied." else "[PM] config reload failed (config not initialized?)"
                    )
                )
            }

            "list" -> {
                val cfg = PrototypeMachineryCommonConfig.getLiveConfig()
                if (cfg == null) {
                    sender.sendMessage(TextComponentString("[PM] config not initialized."))
                    return
                }

                val category = args.getOrNull(1)
                if (category.isNullOrEmpty()) {
                    val cats = cfg.categoryNames.sorted()
                    sender.sendMessage(TextComponentString("[PM] categories (${cats.size}):"))
                    // Keep spam bounded.
                    val max = 50
                    for (i in 0 until minOf(cats.size, max)) {
                        sender.sendMessage(TextComponentString("  - ${cats[i]}"))
                    }
                    if (cats.size > max) {
                        sender.sendMessage(TextComponentString("  ... (${cats.size - max} more)"))
                    }
                    return
                }

                val cat: ConfigCategory? = cfg.getCategory(category)
                if (cat == null) {
                    sender.sendMessage(TextComponentString("[PM] unknown category: $category"))
                    return
                }

                val keys = getCategoryKeys(cat).sorted()
                sender.sendMessage(TextComponentString("[PM] $category keys (${keys.size}):"))
                val max = 80
                for (i in 0 until minOf(keys.size, max)) {
                    val k = keys[i]
                    val p = cat.get(k)
                    val t = p?.type?.name ?: "?"
                    sender.sendMessage(TextComponentString("  - $k ($t)"))
                }
                if (keys.size > max) {
                    sender.sendMessage(TextComponentString("  ... (${keys.size - max} more)"))
                }
            }

            "get" -> {
                val category = args.getOrNull(1)
                val name = args.getOrNull(2)
                if (category.isNullOrEmpty() || name.isNullOrEmpty()) {
                    sender.sendMessage(TextComponentString(getUsage(sender)))
                    return
                }

                val cfg = PrototypeMachineryCommonConfig.getLiveConfig()
                if (cfg == null) {
                    sender.sendMessage(TextComponentString("[PM] config not initialized."))
                    return
                }

                val prop = cfg.getCategory(category)?.get(name)
                if (prop == null) {
                    sender.sendMessage(TextComponentString("[PM] not found: $category.$name"))
                    return
                }

                sender.sendMessage(
                    TextComponentString(
                        "[PM] $category.$name = ${prop.string} (type=${prop.type.name}, default=${prop.default})"
                    )
                )
            }

            "set" -> {
                val category = args.getOrNull(1)
                val name = args.getOrNull(2)
                val value = args.getOrNull(3)
                if (category.isNullOrEmpty() || name.isNullOrEmpty() || value.isNullOrEmpty()) {
                    sender.sendMessage(TextComponentString(getUsage(sender)))
                    return
                }

                val cfg = PrototypeMachineryCommonConfig.getLiveConfig()
                if (cfg == null) {
                    sender.sendMessage(TextComponentString("[PM] config not initialized."))
                    return
                }

                val cat = cfg.getCategory(category)
                val prop: Property = (cat?.get(name) ?: run {
                    // If the key doesn't exist yet, create as string to avoid guessing.
                    cfg.get(category, name, value)
                })

                val old = prop.string

                val ok = try {
                    when (prop.type) {
                        Property.Type.BOOLEAN -> prop.set(parseBooleanValue(value))
                        Property.Type.INTEGER -> prop.set(value.toInt())
                        Property.Type.DOUBLE -> prop.set(value.toDouble())
                        Property.Type.STRING -> prop.set(value)
                        else -> prop.set(value)
                    }
                    true
                } catch (t: Throwable) {
                    sender.sendMessage(TextComponentString("[PM] set failed: ${t.javaClass.simpleName}: ${t.message}"))
                    false
                }

                if (!ok) return

                val applied = PrototypeMachineryCommonConfig.applyInMemory()
                sender.sendMessage(
                    TextComponentString(
                        if (applied) {
                            "[PM] set $category.$name: $old -> ${prop.string} (applied)"
                        } else {
                            "[PM] set $category.$name: $old -> ${prop.string} (saved but apply failed)"
                        }
                    )
                )
            }

            else -> sender.sendMessage(TextComponentString(getUsage(sender)))
        }
    }

    override fun getTabCompletions(
        server: MinecraftServer,
        sender: ICommandSender,
        args: Array<String>,
        targetPos: BlockPos?
    ): MutableList<String> {
        if (args.isEmpty()) return mutableListOf()

        if (args.size == 1) {
            return getListOfStringsMatchingLastWord(args, listOf("reload", "list", "get", "set"))
        }

        val cfg = PrototypeMachineryCommonConfig.getLiveConfig() ?: return mutableListOf()

        if (args.size == 2 && (args[0].equals("list", true) || args[0].equals("get", true) || args[0].equals("set", true))) {
            return getListOfStringsMatchingLastWord(args, cfg.categoryNames.toList())
        }

        if (args.size == 3 && (args[0].equals("get", true) || args[0].equals("set", true))) {
            val cat = cfg.getCategory(args[1]) ?: return mutableListOf()
            return getListOfStringsMatchingLastWord(args, getCategoryKeys(cat))
        }

        if (args.size == 4 && args[0].equals("set", true)) {
            // Convenience for booleans.
            return getListOfStringsMatchingLastWord(args, listOf("true", "false", "on", "off", "1", "0"))
        }

        return mutableListOf()
    }

    private fun parseBooleanValue(v: String): Boolean {
        return when (v.lowercase()) {
            "true", "1", "on", "yes", "enable", "enabled" -> true
            "false", "0", "off", "no", "disable", "disabled" -> false
            else -> v.toBooleanStrict()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getCategoryKeys(cat: ConfigCategory): List<String> {
        // Forge 1.12 ConfigCategory internals vary; use a stable public accessor when available.
        return try {
            val m = cat.javaClass.getMethod("getValues")
            val v = m.invoke(cat)
            val map = v as? Map<String, Property>
            map?.keys?.toList() ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
