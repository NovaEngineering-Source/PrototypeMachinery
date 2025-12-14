package github.kasuminova.prototypemachinery

import github.kasuminova.prototypemachinery.common.CommonProxy
import github.kasuminova.prototypemachinery.common.handler.CraftTweakerReloadHandler
import github.kasuminova.prototypemachinery.common.network.NetworkHandler
import github.kasuminova.prototypemachinery.common.registry.MachineTypeRegisterer
import github.kasuminova.prototypemachinery.common.structure.loader.StructureLoader
import github.kasuminova.prototypemachinery.impl.scheduler.TaskSchedulerImpl
import github.kasuminova.prototypemachinery.integration.crafttweaker.CraftTweakerExamples
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.SidedProxy
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent
import org.apache.logging.log4j.Logger

@Mod(
    modid = PrototypeMachinery.MOD_ID,
    name = PrototypeMachinery.MOD_NAME,
    version = PrototypeMachinery.VERSION,
    acceptedMinecraftVersions = "[1.12.2]",
    dependencies = "required-after:forge@[14.23.5.2847,);" +
            "required-after:forgelin_continuous@[2.1.0.0,);" +
            "required-after:modularui@[3.0.0,);" +
            "after:crafttweaker@[4.1.20,);" +
            "after:jei@[4.16.0,)",
    modLanguageAdapter = "io.github.chaosunity.forgelin.KotlinAdapter"
)
public object PrototypeMachinery {

    public const val MOD_ID: String = Tags.MOD_ID
    public const val MOD_NAME: String = Tags.MOD_NAME
    public const val VERSION: String = Tags.VERSION

    internal lateinit var logger: Logger

    @JvmStatic
    @SidedProxy(
        clientSide = "github.kasuminova.prototypemachinery.client.ClientProxy",
        serverSide = "github.kasuminova.prototypemachinery.common.CommonProxy"
    )
    internal lateinit var proxy: CommonProxy

    @Mod.EventHandler
    internal fun preInit(event: FMLPreInitializationEvent) {
        logger = event.modLog
        event.modMetadata.version = Tags.VERSION

        NetworkHandler.init()

        // Register scheduler to event bus
        // 注册调度器到事件总线
        MinecraftForge.EVENT_BUS.register(TaskSchedulerImpl)

        // CraftTweaker script reload hook (ZenUtils)
        // CraftTweaker 脚本热重载钩子（ZenUtils）
        if (Loader.isModLoaded("crafttweaker")) {
            logger.info("CraftTweaker detected: enabling integration.")
            MinecraftForge.EVENT_BUS.register(CraftTweakerReloadHandler)
        }

        // Load structure JSON data (without resolving blocks)
        // 加载结构 JSON 数据（不解析方块）
        StructureLoader.loadStructureData(event)

        // Copy CraftTweaker example scripts if CraftTweaker is loaded
        // 如果 CraftTweaker 已加载，则复制示例脚本
        CraftTweakerExamples.initialize(event)

        // Register machine types from queue
        MachineTypeRegisterer.processQueue(event)

        proxy.preInit()
    }

    @Mod.EventHandler
    internal fun init(event: FMLInitializationEvent) {
        proxy.init()
    }

    @Mod.EventHandler
    internal fun postInit(event: FMLPostInitializationEvent) {
        // Process structures and resolve block references
        // 处理结构并解析方块引用
        StructureLoader.processStructures(event)

        proxy.postInit()
    }

    @Mod.EventHandler
    internal fun serverStopping(event: FMLServerStoppingEvent) {
        // Shutdown scheduler and cleanup resources
        // 关闭调度器并清理资源
        TaskSchedulerImpl.shutdown()
    }

}