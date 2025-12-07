package github.kasuminova.prototypemachinery

import github.kasuminova.prototypemachinery.common.CommonProxy
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.SidedProxy
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import org.apache.logging.log4j.Logger

@Mod(
    modid = PrototypeMachinery.MOD_ID,
    name = PrototypeMachinery.MOD_NAME,
    version = PrototypeMachinery.VERSION,
    acceptedMinecraftVersions = "[1.12.2]",
    dependencies = "required-after:forge@[14.23.5.2847,);" +
            "required-after:forgelin_continuous@[2.1.0.0,);" +
            "after:crafttweaker@[4.1.20,);" +
            "after:jei@[4.16.0,)",
    modLanguageAdapter = "io.github.chaosunity.forgelin.KotlinAdapter"
)
public object PrototypeMachinery {

    public const val MOD_ID: String = Tags.MOD_ID
    public const val MOD_NAME: String = Tags.MOD_NAME
    public const val VERSION: String = Tags.VERSION

    public lateinit var logger: Logger

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

        proxy.preInit()
    }

    @Mod.EventHandler
    internal fun init(event: FMLInitializationEvent) {
        proxy.init()
    }

    @Mod.EventHandler
    internal fun postInit(event: FMLPostInitializationEvent) {
        proxy.postInit()
    }

}