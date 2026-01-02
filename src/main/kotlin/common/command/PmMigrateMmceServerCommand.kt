package github.kasuminova.prototypemachinery.common.command

import github.kasuminova.prototypemachinery.common.structure.tools.MmceStructureMigrationUtil
import github.kasuminova.prototypemachinery.common.structure.tools.StructureExportUtil
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.BlockPos
import net.minecraft.util.text.TextComponentString
import net.minecraftforge.fml.common.Loader
import java.io.File

internal object PmMigrateMmceServerCommand : CommandBase() {

    override fun getName(): String = "pm_migrate_mmce"

    override fun getUsage(sender: ICommandSender): String =
        "/pm_migrate_mmce <machine.json|name> [outId] [--nbt] [--script] [--no-dynamic | --dynamic=range|min|max|N]"

    override fun getRequiredPermissionLevel(): Int = 2

    override fun execute(server: MinecraftServer, sender: ICommandSender, args: Array<String>) {
        if (args.isEmpty()) {
            sender.sendMessage(TextComponentString(getUsage(sender)))
            return
        }

        val flags = args.filter { it.startsWith("--") }.toSet()
        val positional = args.filterNot { it.startsWith("--") }

        val ref = positional.getOrNull(0)
        if (ref.isNullOrBlank()) {
            sender.sendMessage(TextComponentString(getUsage(sender)))
            return
        }

        val outId = positional.getOrNull(1)
        val withNbt = flags.contains("--nbt")
        val writeScript = flags.contains("--script")

        val dynamicMode = run {
            if (flags.contains("--no-dynamic")) return@run MmceStructureMigrationUtil.DynamicPatternMode.IGNORE
            val dyn = args.firstOrNull { it.startsWith("--dynamic=") }?.substringAfter('=')?.trim()
            when (dyn?.lowercase()) {
                null, "", "range" -> MmceStructureMigrationUtil.DynamicPatternMode.RANGE
                "min" -> MmceStructureMigrationUtil.DynamicPatternMode.MIN
                "max" -> MmceStructureMigrationUtil.DynamicPatternMode.MAX
                else -> MmceStructureMigrationUtil.DynamicPatternMode.FIXED
            }
        }

        val dynamicFixedSize = run {
            if (dynamicMode != MmceStructureMigrationUtil.DynamicPatternMode.FIXED) return@run null
            val dyn = args.firstOrNull { it.startsWith("--dynamic=") }?.substringAfter('=')?.trim()
            dyn?.toIntOrNull()
        }

        val configDir = Loader.instance().configDir
        val mmceMachineryDir = File(configDir, "modularmachinery/machinery")
        if (!mmceMachineryDir.exists()) {
            sender.sendMessage(TextComponentString("[PM] 未找到 MMCE machinery 目录: ${mmceMachineryDir.absolutePath}"))
            return
        }

        val machineFile = resolveMachineFile(mmceMachineryDir, ref)
        if (machineFile == null || !machineFile.exists()) {
            sender.sendMessage(TextComponentString("[PM] 找不到 machine JSON: $ref (base=${mmceMachineryDir.absolutePath})"))
            return
        }

        sender.sendMessage(TextComponentString("[PM] 读取 MMCE machine: ${machineFile.absolutePath}"))

        val variableCtx = MmceStructureMigrationUtil.loadVariableContext(mmceMachineryDir)
        sender.sendMessage(TextComponentString("[PM] 读取变量定义: ${variableCtx.size} 项"))

        val result = runCatching {
            MmceStructureMigrationUtil.migrateMachineJson(
                machineJsonFile = machineFile,
                variableContext = variableCtx,
                outStructureId = outId,
                includeNbtConstraints = withNbt,
                dynamicPatternMode = dynamicMode,
                dynamicPatternFixedSize = dynamicFixedSize,
            )
        }.getOrElse { t ->
            sender.sendMessage(TextComponentString("[PM] 迁移失败: ${t.javaClass.simpleName}: ${t.message}"))
            return
        }

        // Write additional child structures first (so the main structure references already exist on disk).
        for (s in result.additionalStructures) {
            StructureExportUtil.writeStructureJson(
                data = s,
                subDir = "migrated/mmce",
                preferredFileName = s.id
            )
        }

        val outFile = StructureExportUtil.writeStructureJson(
            data = result.structure,
            subDir = "migrated/mmce",
            preferredFileName = result.structure.id
        )

        sender.sendMessage(TextComponentString("[PM] 已生成 PM 结构: id=${result.structure.id}"))
        sender.sendMessage(TextComponentString("[PM] 文件: ${outFile.absolutePath}"))
        if (result.additionalStructures.isNotEmpty()) {
            sender.sendMessage(TextComponentString("[PM] 已生成子结构: ${result.additionalStructures.size} 个"))
        }

        if (result.warnings.isNotEmpty()) {
            sender.sendMessage(TextComponentString("[PM] 警告 (${result.warnings.size}):"))
            result.warnings.take(20).forEach { w ->
                sender.sendMessage(TextComponentString("  - $w"))
            }
            if (result.warnings.size > 20) {
                sender.sendMessage(TextComponentString("  ... (${result.warnings.size - 20} more)"))
            }
        }

        if (writeScript) {
            val scriptFile = writeScriptTemplate(result.structure.id, result.structure.name)
            sender.sendMessage(TextComponentString("[PM] 已生成脚本模板: ${scriptFile.absolutePath}"))
        }

        sender.sendMessage(TextComponentString("[PM] 提示: 结构 JSON 需要重启/重载后才会被加载（当前暂无在线重载）。"))
    }

    override fun getTabCompletions(
        server: MinecraftServer,
        sender: ICommandSender,
        args: Array<String>,
        targetPos: BlockPos?
    ): MutableList<String> {
        if (args.isEmpty()) return mutableListOf()
        if (args.size == 1) {
            return getListOfStringsMatchingLastWord(args, listOf("--nbt", "--script", "--no-dynamic", "--dynamic=range", "--dynamic=min", "--dynamic=max"))
        }
        return mutableListOf()
    }

    private fun resolveMachineFile(base: File, ref: String): File? {
        val direct = File(ref)
        if (direct.isAbsolute && direct.exists()) return direct

        // Relative to machinery dir.
        run {
            val f1 = File(base, ref)
            if (f1.exists() && f1.isFile) return f1
            val f2 = if (ref.endsWith(".json", true)) null else File(base, "$ref.json")
            if (f2 != null && f2.exists() && f2.isFile) return f2
        }

        // Search by file name recursively.
        val targetNames = buildList {
            add(ref)
            if (!ref.endsWith(".json", true)) add("$ref.json")
        }.toSet()

        return base.walkTopDown()
            .firstOrNull { it.isFile && it.name in targetNames }
    }

    private fun writeScriptTemplate(structureId: String, displayName: String?): File {
        val dir = File("scripts/prototypemachinery/migrated")
        if (!dir.exists()) dir.mkdirs()

        val safe = StructureExportUtil.sanitizeId(structureId)
        var f = File(dir, "$safe.zs")
        if (f.exists()) {
            var i = 2
            while (true) {
                val c = File(dir, "${safe}_$i.zs")
                if (!c.exists()) {
                    f = c
                    break
                }
                i++
            }
        }

        val name = (displayName?.takeIf { it.isNotBlank() } ?: safe).replace("\"", "\\\"")

        val content = buildString {
            appendLine("#loader preinit")
            appendLine()
            appendLine("// Auto-generated by /pm_migrate_mmce")
            appendLine("// 注意：这只是最小模板，你仍需要补齐 componentTypes / recipes / UI 等。")
            appendLine()
            appendLine("import mods.prototypemachinery.MachineRegistry;")
            appendLine()
            appendLine("val m = MachineRegistry.create(\"prototypemachinery\", \"$safe\");")
            appendLine("m.name(\"$name\");")
            appendLine("m.structure(\"$structureId\");")
            appendLine("MachineRegistry.register(m);")
        }

        f.writeText(content)
        return f
    }
}
