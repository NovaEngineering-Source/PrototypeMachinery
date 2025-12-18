package github.kasuminova.prototypemachinery.integration.jei.registry

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiMachineLayoutDefinition
import github.kasuminova.prototypemachinery.integration.jei.registry.JeiMachineLayoutRegistry.defaultLayout
import net.minecraft.util.ResourceLocation
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-machine JEI layout registry.
 */
public object JeiMachineLayoutRegistry {

    private val layouts: MutableMap<ResourceLocation, PMJeiMachineLayoutDefinition> = ConcurrentHashMap()

    /**
     * Default/fallback layout. Must be set by the JEI integration during client init.
     */
    @Volatile
    private var defaultLayout: PMJeiMachineLayoutDefinition? = null

    public fun clear() {
        layouts.clear()
    }

    public fun snapshot(): Map<ResourceLocation, PMJeiMachineLayoutDefinition> {
        return LinkedHashMap(layouts)
    }

    public fun setDefault(layout: PMJeiMachineLayoutDefinition) {
        defaultLayout = layout
    }

    public fun getDefault(): PMJeiMachineLayoutDefinition? = defaultLayout

    public fun register(
        machineTypeId: ResourceLocation,
        layout: PMJeiMachineLayoutDefinition,
        replace: Boolean = true,
    ) {
        if (!replace && layouts.containsKey(machineTypeId)) {
            PrototypeMachinery.logger.warn(
                "JEI layout already registered for machineType '$machineTypeId'. Skipping because replace=false."
            )
            return
        }

        val prev = layouts.put(machineTypeId, layout)
        if (prev != null && prev !== layout) {
            PrototypeMachinery.logger.info(
                "JEI layout replaced for machineType '$machineTypeId': ${prev::class.java.name} -> ${layout::class.java.name}"
            )
        }
    }

    public fun get(machineTypeId: ResourceLocation): PMJeiMachineLayoutDefinition? {
        return layouts[machineTypeId]
    }

    /**
     * Resolve a layout for the given machine type, falling back to [defaultLayout].
     *
     * NOTE: Callers should treat the returned layout as immutable.
     */
    public fun resolve(machineTypeId: ResourceLocation): PMJeiMachineLayoutDefinition? {
        return layouts[machineTypeId] ?: defaultLayout
    }
}
