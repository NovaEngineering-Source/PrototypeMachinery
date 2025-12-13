package github.kasuminova.prototypemachinery.api.ui.registry

import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import net.minecraft.util.ResourceLocation

/**
 * Runtime registry for machine UI definitions.
 *
 * This registry is designed for script/mod overrides so UI can be provided
 * independently from machine type construction.
 */
public interface MachineUIRegistry {

    public data class Registration(
        public val machineId: ResourceLocation,
        public val owner: String,
        public val priority: Int,
        public val order: Long,
        public val definition: WidgetDefinition
    )

    /**
     * Register a UI definition for the given machine id.
     * Higher [priority] wins; if equal, later registrations win.
     */
    public fun register(
        machineId: ResourceLocation,
        definition: WidgetDefinition,
        priority: Int = 0,
        owner: String = "unknown"
    )

    /** Remove all registrations for [machineId]. */
    public fun clear(machineId: ResourceLocation)

    /** Remove all registrations for all machines. */
    public fun clearAll()

    /** Resolve the effective UI definition for [machineId]. */
    public fun resolve(machineId: ResourceLocation): WidgetDefinition?

    /** List all registrations for [machineId] (debug/inspection). */
    public fun list(machineId: ResourceLocation): List<Registration>
}
