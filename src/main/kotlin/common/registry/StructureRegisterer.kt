package github.kasuminova.prototypemachinery.common.registry

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure
import github.kasuminova.prototypemachinery.impl.machine.structure.StructureRegistryImpl
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent

/**
 * Structure registerer for queuing and processing structure registrations.
 * 用于排队和处理结构注册的注册器。
 *
 * Structures are queued during mod construction/PreInit and processed
 * at the appropriate time to ensure proper initialization order.
 *
 * 结构在模组构造/PreInit 期间排队，并在适当的时间处理以确保正确的初始化顺序。
 */
internal object StructureRegisterer {

    private val structures = mutableListOf<MachineStructure>()

    /**
     * Queue a structure for registration.
     * 将结构排队等待注册。
     *
     * This should be called during mod construction or PreInit.
     * 应在模组构造或 PreInit 期间调用。
     */
    fun queue(structure: MachineStructure) {
        structures.add(structure)
    }

    /**
     * Process all queued structures.
     * 处理所有排队的结构。
     *
     * This should be called during PostInit event after all structures are loaded.
     * 应在 PostInit 事件期间，所有结构加载完成后调用。
     */
    fun processQueue(event: FMLPostInitializationEvent) {
        structures.forEach { structure ->
            try {
                StructureRegistryImpl.register(structure)
                PrototypeMachinery.logger.info("Registered structure: ${structure.id}")
            } catch (e: IllegalArgumentException) {
                PrototypeMachinery.logger.error("Failed to register structure: ${structure.id}", e)
            }
        }
        structures.clear()
    }

}
