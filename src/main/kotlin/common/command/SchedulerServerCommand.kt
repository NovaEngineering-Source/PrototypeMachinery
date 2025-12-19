package github.kasuminova.prototypemachinery.common.command

import github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI
import github.kasuminova.prototypemachinery.common.config.PmSchedulerConfig
import github.kasuminova.prototypemachinery.impl.scheduler.SchedulerBackendType
import github.kasuminova.prototypemachinery.impl.scheduler.TaskSchedulerImpl
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.BlockPos
import net.minecraft.util.text.TextComponentString

internal object SchedulerServerCommand : CommandBase() {

    override fun getName(): String = "pm_scheduler"

    override fun getUsage(sender: ICommandSender): String =
        "/pm_scheduler status | report | reload | switch <JAVA|COROUTINES> | metrics <on|off>"

    override fun getRequiredPermissionLevel(): Int = 2

    override fun execute(server: MinecraftServer, sender: ICommandSender, args: Array<String>) {
        if (args.isEmpty()) {
            sender.sendMessage(TextComponentString(getUsage(sender)))
            return
        }

        when (args[0].lowercase()) {
            "status" -> {
                sender.sendMessage(
                    TextComponentString(
                        buildString {
                            append("[PM] scheduler backend=")
                            append(TaskSchedulerImpl.currentBackendName())
                            append(", registered=")
                            append(PrototypeMachineryAPI.taskScheduler.getRegisteredCount())
                            append(", workerThreads=")
                            append(PmSchedulerConfig.scheduler.workerThreads)
                            append(", lanes=")
                            append(PmSchedulerConfig.scheduler.laneCount)
                            append(", metrics=")
                            append(if (PmSchedulerConfig.scheduler.metricsEnabled) "on" else "off")
                            append(", coroutines=")
                            append(if (TaskSchedulerImpl.isCoroutinesBackendAvailable()) "available" else "missing")
                        }
                    )
                )
            }

            "report" -> {
                val report = TaskSchedulerImpl.snapshotReport()
                sender.sendMessage(TextComponentString("[PM] scheduler report: ${report.toLogLine()}"))
            }

            "reload" -> {
                TaskSchedulerImpl.requestReloadFromConfig()
                sender.sendMessage(TextComponentString("[PM] scheduler: config reload requested (applies next tick)"))
            }

            "switch" -> {
                val type = SchedulerBackendType.parse(args.getOrNull(1))
                val ok = TaskSchedulerImpl.requestSwitchBackend(type)
                if (!ok) {
                    sender.sendMessage(
                        TextComponentString(
                            "[PM] scheduler: COROUTINES backend is not available (missing kotlinx-coroutines from prerequisite mod)."
                        )
                    )
                    return
                }
                sender.sendMessage(
                    TextComponentString("[PM] scheduler: backend switch to ${type.name} requested (applies next tick)")
                )
            }

            "metrics" -> {
                val v = args.getOrNull(1)?.lowercase()
                val enabled = when (v) {
                    "on", "true", "1", "enable", "enabled" -> true
                    "off", "false", "0", "disable", "disabled" -> false
                    else -> {
                        sender.sendMessage(TextComponentString("[PM] metrics usage: /pm_scheduler metrics <on|off>"))
                        return
                    }
                }
                PmSchedulerConfig.scheduler.metricsEnabled = enabled
                TaskSchedulerImpl.requestReloadFromConfig()
                sender.sendMessage(TextComponentString("[PM] scheduler: metrics ${if (enabled) "enabled" else "disabled"} (applies next tick; also update config file to persist)"))
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
            return getListOfStringsMatchingLastWord(args, listOf("status", "report", "reload", "switch", "metrics"))
        }

        if (args.size == 2 && args[0].equals("switch", ignoreCase = true)) {
            return getListOfStringsMatchingLastWord(args, listOf("JAVA", "COROUTINES"))
        }

        if (args.size == 2 && args[0].equals("metrics", ignoreCase = true)) {
            return getListOfStringsMatchingLastWord(args, listOf("on", "off"))
        }

        return mutableListOf()
    }
}
