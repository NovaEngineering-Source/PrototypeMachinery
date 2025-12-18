package github.kasuminova.prototypemachinery.integration.crafttweaker

import github.kasuminova.prototypemachinery.PrototypeMachinery
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import java.io.File

/**
 * Integration handler for CraftTweaker script examples.
 * CraftTweaker 脚本示例的集成处理器。
 */
public object CraftTweakerExamples {

    /**
     * Check if CraftTweaker is loaded and copy example scripts if needed.
     * 检查 CraftTweaker 是否加载，如果需要则复制示例脚本。
     */
    public fun initialize(event: FMLPreInitializationEvent) {
        if (!Loader.isModLoaded("crafttweaker")) {
            event.modLog.info("CraftTweaker not detected, skipping script examples")
            return
        }

        event.modLog.info("CraftTweaker detected, checking for example scripts...")

        val configDir = event.modConfigurationDirectory
        val scriptsDir = File(configDir.parentFile, "scripts/prototypemachinery")
        val examplesDir = File(scriptsDir, "examples")

        // Only copy if examples directory doesn't exist or is empty
        // 仅在示例目录不存在或为空时复制
        if (!examplesDir.exists() || examplesDir.listFiles()?.isEmpty() == true) {
            copyExampleScripts(examplesDir, event)
        } else {
            event.modLog.info("CraftTweaker example scripts already exist, skipping copy")
        }
    }

    /**
     * Copy example CraftTweaker scripts from resources to scripts directory.
     * 从资源文件复制示例 CraftTweaker 脚本到脚本目录。
     */
    private fun copyExampleScripts(targetDir: File, event: FMLPreInitializationEvent) {
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val exampleFiles = listOf(
            "machine_registration.zs",
            "recipe_processor_full_example.zs",
            "recipe_processor_with_hatches_example.zs",
            "recipe_processor_with_hatches_recipes.zs",
            "README.md"
        )

        var copiedCount = 0
        for (fileName in exampleFiles) {
            try {
                val resourcePath = "/assets/prototypemachinery/scripts/examples/$fileName"
                val inputStream = CraftTweakerExamples::class.java.getResourceAsStream(resourcePath)

                if (inputStream != null) {
                    val targetFile = File(targetDir, fileName)
                    inputStream.use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    copiedCount++
                    event.modLog.info("Copied example script: $fileName")
                } else {
                    event.modLog.warn("Example script not found in resources: $fileName")
                }
            } catch (e: Throwable) {
                event.modLog.error("Failed to copy example script: $fileName", e)
            }
        }

        if (copiedCount > 0) {
            PrototypeMachinery.logger.info("Copied $copiedCount CraftTweaker example script(s) to: ${targetDir.absolutePath}")
            PrototypeMachinery.logger.info("You can use these as templates for your own machine definitions")
            PrototypeMachinery.logger.info("Note: These are examples only and won't be loaded automatically")
        }
    }

}