package github.kasuminova.prototypemachinery.common.structure.tools

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * 本地集成烟雾测试：用于拿真实整合包的 MMCE machinery 配置跑一遍迁移器，尽早发现崩溃/格式差异。
 *
 * 说明：
 * - CI / 其他开发者环境一般没有该目录，所以会自动 skip。
 * - 运行方式：
 *   -Dpm.mmceDir=/path/to/.minecraft/config/modularmachinery/machinery
 */
class MmceStructureMigrationIntegrationTest {

    @Test
    fun `migrator can process a batch of real mmce machines without crashing`() {
        val defaultDir = File(
            "/home/hikari_nova/.local/share/PrismLauncher/instances/NovaEng-CRL/.minecraft/config/modularmachinery/machinery"
        )
        val dir = System.getProperty("pm.mmceDir")?.takeIf { it.isNotBlank() }?.let { File(it) } ?: defaultDir

        assumeTrue(dir.isDirectory) { "MMCE machinery dir not found: ${dir.absolutePath}" }

        val variableCtx = MmceStructureMigrationUtil.loadVariableContext(dir)

        val prioritized = listOf(
            "Nova-extendable_calculator_subsystem_l9.json",   // dynamic-patterns
            "Nova-extendable_fabricator_subsystem_l9.json",   // dynamic-patterns
            "Blue_Sky_It-Large_sieving_machine.json",         // modifiers
            "U_interactive_generators.json"                   // nbt/preview-nbt
        )

        val prioritizedFiles = prioritized
            .mapNotNull { name ->
                dir.walkTopDown().firstOrNull { it.isFile && it.name.equals(name, ignoreCase = true) }
            }

        val randomFiles = dir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".json", ignoreCase = true) && !it.name.endsWith(".var.json", ignoreCase = true) }
            .filterNot { f -> prioritizedFiles.any { it.absolutePath == f.absolutePath } }
            .take(25)
            .toList()

        val machineFiles = (prioritizedFiles + randomFiles).distinctBy { it.absolutePath }

        assumeTrue(machineFiles.isNotEmpty()) { "No machine json found under: ${dir.absolutePath}" }

        for (f in machineFiles) {
            MmceStructureMigrationUtil.migrateMachineJson(
                machineJsonFile = f,
                variableContext = variableCtx,
                outStructureId = null,
                includeNbtConstraints = false,
                dynamicPatternMode = MmceStructureMigrationUtil.DynamicPatternMode.RANGE,
                dynamicPatternFixedSize = null,
            )
        }
    }
}
