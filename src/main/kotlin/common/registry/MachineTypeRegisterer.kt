package github.kasuminova.prototypemachinery.common.registry

import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.impl.machine.MachineTypeRegistryImpl
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent

/**
 * Machine type registerer.
 * 
 * This class handles the registration of machine types to the internal registry.
 * It does NOT use Forge's registry system directly, as MachineType is a complex object
 * that should be registered during PreInit phase.
 */
internal object MachineTypeRegisterer {

    private val machineTypes = mutableListOf<MachineType>()

    /**
     * Queue a machine type for registration.
     * This should be called during mod construction or PreInit.
     */
    fun queue(machineType: MachineType) {
        machineTypes.add(machineType)
    }

    /**
     * Process all queued machine types.
     * This should be called during PreInit event.
     */
    fun processQueue(event: FMLPreInitializationEvent) {
        machineTypes.forEach { machineType ->
            try {
                MachineTypeRegistryImpl.register(machineType)
                event.modLog.info("Registered machine type: ${machineType.id}")
            } catch (e: IllegalArgumentException) {
                event.modLog.error("Failed to register machine type: ${machineType.id}", e)
            }
        }
        machineTypes.clear()
    }

}
