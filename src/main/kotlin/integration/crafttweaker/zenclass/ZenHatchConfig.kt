package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass

import crafttweaker.annotations.ZenRegister
import github.kasuminova.prototypemachinery.common.block.hatch.HatchType
import github.kasuminova.prototypemachinery.common.registry.HatchConfigRegistry
import github.kasuminova.prototypemachinery.common.registry.HatchConfigUpdateBridge
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

/**
 * ZenScript API for modifying hatch configurations at runtime
 */
@ZenRegister
@ZenClass("mods.prototypemachinery.HatchConfig")
public object ZenHatchConfig {

    /**
     * Modify item hatch configuration
     * @param tierLevel The tier level (1-10)
     * @param slotCount Number of slots
     */
    @ZenMethod
    @JvmStatic
    public fun modifyItemHatch(tierLevel: Int, slotCount: Int) {
        HatchConfigRegistry.updateItemHatch(tierLevel, HatchType.INPUT, slotCount = slotCount)
        HatchConfigRegistry.updateItemHatch(tierLevel, HatchType.OUTPUT, slotCount = slotCount)

        // IO hatch uses a different config type; for convenience we set both input & output slots.
        val io = HatchConfigRegistry.getItemIOHatchConfig(tierLevel)
        HatchConfigRegistry.setItemIOHatchConfig(
            io.copy(
                inputSlotCount = slotCount,
                outputSlotCount = slotCount
            )
        )

        HatchConfigUpdateBridge.applyItemTier(tierLevel)
    }

    /**
     * Modify fluid hatch configuration.
     *
     * Note: current hatch implementation models fluids as a type-based storage (types == tanks).
     * There is no per-tick transfer rate in the config.
     *
     * @param tierLevel The tier level (1-10)
     * @param tankCount Number of tanks (types)
     * @param tankCapacity Capacity per tank in mB
     */
    @ZenMethod
    @JvmStatic
    public fun modifyFluidHatch(tierLevel: Int, tankCount: Int, tankCapacity: Long) {
        HatchConfigRegistry.updateFluidHatch(tierLevel, HatchType.INPUT, tankCount = tankCount, tankCapacity = tankCapacity)
        HatchConfigRegistry.updateFluidHatch(tierLevel, HatchType.OUTPUT, tankCount = tankCount, tankCapacity = tankCapacity)
        HatchConfigRegistry.updateFluidHatch(tierLevel, HatchType.IO, tankCount = tankCount, tankCapacity = tankCapacity)

        HatchConfigUpdateBridge.applyFluidTier(tierLevel)
    }

    /**
     * Modify energy hatch configuration
     * @param tierLevel The tier level (1-10)
     * @param capacity Energy capacity in FE
     * @param maxTransfer Max transfer rate per tick in FE/t
     */
    @ZenMethod
    @JvmStatic
    public fun modifyEnergyHatch(tierLevel: Int, capacity: Long, maxTransfer: Long) {
        HatchConfigRegistry.updateEnergyHatch(tierLevel, HatchType.INPUT, capacity = capacity, maxTransfer = maxTransfer)
        HatchConfigRegistry.updateEnergyHatch(tierLevel, HatchType.OUTPUT, capacity = capacity, maxTransfer = maxTransfer)
        HatchConfigRegistry.updateEnergyHatch(tierLevel, HatchType.IO, capacity = capacity, maxTransfer = maxTransfer)

        HatchConfigUpdateBridge.applyEnergyTier(tierLevel)
    }

    /**
     * Get current item hatch slot count
     */
    @ZenMethod
    @JvmStatic
    public fun getItemHatchSlotCount(tierLevel: Int): Int {
        return HatchConfigRegistry.getItemHatchConfig(tierLevel, HatchType.INPUT).slotCount
    }

    /**
     * Get current fluid hatch tank capacity (per tank).
     */
    @ZenMethod
    @JvmStatic
    public fun getFluidHatchCapacity(tierLevel: Int): Long {
        return HatchConfigRegistry.getFluidHatchConfig(tierLevel, HatchType.INPUT).tankCapacity
    }

    /**
     * Get current energy hatch capacity
     */
    @ZenMethod
    @JvmStatic
    public fun getEnergyHatchCapacity(tierLevel: Int): Long {
        return HatchConfigRegistry.getEnergyHatchConfig(tierLevel, HatchType.INPUT).capacity
    }
}