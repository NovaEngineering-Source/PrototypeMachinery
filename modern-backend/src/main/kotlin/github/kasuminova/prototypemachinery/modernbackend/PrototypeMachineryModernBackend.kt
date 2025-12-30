package github.kasuminova.prototypemachinery.modernbackend

import github.kasuminova.prototypemachinery.modernbackend.accel.PositionTransformBackends
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.Mod.EventHandler
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import org.apache.logging.log4j.LogManager

/**
 * PrototypeMachinery - Modern Backend
 *
 * Optional addon mod intended for Cleanroom Loader environments running on modern JVMs.
 * It can provide newer Java features and/or alternative backend implementations without
 * impacting the legacy Java 8/RFG main build.
 */
@Mod(
    modid = PrototypeMachineryModernBackend.MODID,
    name = PrototypeMachineryModernBackend.NAME,
    version = PrototypeMachineryModernBackend.VERSION,
    acceptedMinecraftVersions = "[1.12.2]",
    dependencies = "required-after:forge@[14.23.5.2847,);required-after:prototypemachinery;required-after:forgelin_continuous@[2.1.0.0,);"
)
class PrototypeMachineryModernBackend {
    companion object {
        const val MODID: String = "pm_modern_backend"
        const val NAME: String = "PrototypeMachinery - Modern Backend"
        const val VERSION: String = "0.0.0"
    }

    private val logger = LogManager.getLogger(MODID)

    @EventHandler
    fun preInit(event: FMLPreInitializationEvent) {
        // Force backend probing during init so later hot paths do not pay classloading/reflection cost.
        val backend = PositionTransformBackends.get()
        if (java.lang.Boolean.getBoolean("pm.debugTransformBackend")) {
            logger.info("[PM-MB] PositionTransformBackend selected: {} (vectorized={})", backend.name(), backend.isVectorized)
        }
    }
}
