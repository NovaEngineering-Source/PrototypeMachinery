package github.kasuminova.prototypemachinery.example

import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType
import github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure
import github.kasuminova.prototypemachinery.common.registry.MachineTypeRegisterer
import net.minecraft.util.ResourceLocation

/**
 * Example of registering a machine type from code.
 * 
 * This demonstrates the direct API approach for mod developers.
 */
public object ExampleMachineRegistration {

    /**
     * Call this method during PreInit to register example machines.
     */
    public fun registerExampleMachines() {
        // Example 1: Simple machine type
        val simpleMachine = SimpleMachineType(
            id = ResourceLocation("prototypemachinery", "example_simple"),
            machineName = "Example Simple Machine"
        )
        MachineTypeRegisterer.queue(simpleMachine)

        // Example 2: Advanced machine with components
        val advancedMachine = AdvancedMachineType(
            id = ResourceLocation("prototypemachinery", "example_advanced"),
            machineName = "Example Advanced Machine"
        )
        MachineTypeRegisterer.queue(advancedMachine)
    }

    /**
     * Simple machine type implementation.
     */
    private class SimpleMachineType(
        override val id: ResourceLocation,
        private val machineName: String
    ) : MachineType {

        override val name: String
            get() = machineName

        override val structure: MachineStructure
            get() = TODO("Define your structure here")

        override val componentTypes: Set<MachineComponentType<*>>
            get() = emptySet() // No components for this simple example

    }

    /**
     * Advanced machine type with components.
     */
    private class AdvancedMachineType(
        override val id: ResourceLocation,
        private val machineName: String
    ) : MachineType {

        override val name: String
            get() = machineName

        override val structure: MachineStructure
            get() = TODO("Define your structure here")

        override val componentTypes: Set<MachineComponentType<*>>
            get() = setOf(
                // Add your component types here
                // Example:
                // StandardComponentTypes.RECIPE_PROCESSOR,
                // StandardComponentTypes.ITEM_CONTAINER,
                // StandardComponentTypes.ENERGY_CONSUMER
            )

    }

}

/**
 * Example of using the API to query registered machines.
 */
public object ExampleMachineQuery {

    /**
     * Example: Query a machine by ID
     */
    public fun findMachine(modId: String, machinePath: String): MachineType? {
        val id = ResourceLocation(modId, machinePath)
        return github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI.machineTypeRegistry[id]
    }

    /**
     * Example: List all registered machines
     */
    public fun listAllMachines() {
        val allMachines = github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI.machineTypeRegistry.all()
        allMachines.forEach { machine ->
            println("Machine: ${machine.id} - ${machine.name}")
            println("  Components: ${machine.componentTypes.size}")
        }
    }

    /**
     * Example: Check if a machine exists
     */
    public fun hasMachine(modId: String, machinePath: String): Boolean {
        val id = ResourceLocation(modId, machinePath)
        return github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI.machineTypeRegistry.contains(id)
    }

}
