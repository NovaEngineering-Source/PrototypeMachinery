package github.kasuminova.prototypemachinery.common.registry

import github.kasuminova.prototypemachinery.common.block.hatch.HatchType
import github.kasuminova.prototypemachinery.common.block.hatch.energy.EnergyHatchConfig
import github.kasuminova.prototypemachinery.common.block.hatch.fluid.FluidHatchConfig
import github.kasuminova.prototypemachinery.common.block.hatch.fluid.FluidIOHatchConfig
import github.kasuminova.prototypemachinery.common.block.hatch.item.ItemHatchConfig
import github.kasuminova.prototypemachinery.common.block.hatch.item.ItemIOHatchConfig

/**
 * Centralized mutable registry for hatch configs.
 *
 * This exists mainly for CraftTweaker / ZenScript integration: scripts can override configs
 * before blocks are registered.
 */
public object HatchConfigRegistry {

    private val itemHatches: MutableMap<Pair<HatchType, Int>, ItemHatchConfig> = mutableMapOf()
    private val itemIOHatches: MutableMap<Int, ItemIOHatchConfig> = mutableMapOf()

    private val fluidHatches: MutableMap<Pair<HatchType, Int>, FluidHatchConfig> = mutableMapOf()
    private val fluidIOHatches: MutableMap<Int, FluidIOHatchConfig> = mutableMapOf()

    private val energyHatches: MutableMap<Pair<HatchType, Int>, EnergyHatchConfig> = mutableMapOf()

    init {
        // Pre-populate defaults for all tiers so scripts can safely query/override.
        for (tier in 1..10) {
            itemHatches[HatchType.INPUT to tier] = ItemHatchConfig.createDefault(tier, HatchType.INPUT)
            itemHatches[HatchType.OUTPUT to tier] = ItemHatchConfig.createDefault(tier, HatchType.OUTPUT)
            itemIOHatches[tier] = ItemIOHatchConfig.createDefault(tier)

            fluidHatches[HatchType.INPUT to tier] = FluidHatchConfig.createDefault(tier, HatchType.INPUT)
            fluidHatches[HatchType.OUTPUT to tier] = FluidHatchConfig.createDefault(tier, HatchType.OUTPUT)
            fluidHatches[HatchType.IO to tier] = FluidHatchConfig.createDefault(tier, HatchType.IO)
            fluidIOHatches[tier] = FluidIOHatchConfig.createDefault(tier)

            energyHatches[HatchType.INPUT to tier] = EnergyHatchConfig.createDefault(tier, HatchType.INPUT)
            energyHatches[HatchType.OUTPUT to tier] = EnergyHatchConfig.createDefault(tier, HatchType.OUTPUT)
            energyHatches[HatchType.IO to tier] = EnergyHatchConfig.createDefault(tier, HatchType.IO)
        }
    }

    // region Item

    public fun getItemHatchConfig(tier: Int, hatchType: HatchType): ItemHatchConfig {
        return itemHatches[hatchType to tier]
            ?: ItemHatchConfig.createDefault(tier, hatchType).also { itemHatches[hatchType to tier] = it }
    }

    public fun setItemHatchConfig(config: ItemHatchConfig) {
        itemHatches[config.hatchType to config.tier.tier] = config
    }

    public fun updateItemHatch(tier: Int, hatchType: HatchType, slotCount: Int? = null, maxStackSize: Long? = null) {
        val current = getItemHatchConfig(tier, hatchType)
        val updated = current.copy(
            slotCount = slotCount ?: current.slotCount,
            maxStackSize = maxStackSize ?: current.maxStackSize
        )
        setItemHatchConfig(updated)
    }

    public fun getItemIOHatchConfig(tier: Int): ItemIOHatchConfig {
        return itemIOHatches[tier]
            ?: ItemIOHatchConfig.createDefault(tier).also { itemIOHatches[tier] = it }
    }

    public fun setItemIOHatchConfig(config: ItemIOHatchConfig) {
        itemIOHatches[config.tier.tier] = config
    }

    // endregion

    // region Fluid

    public fun getFluidHatchConfig(tier: Int, hatchType: HatchType): FluidHatchConfig {
        return fluidHatches[hatchType to tier]
            ?: FluidHatchConfig.createDefault(tier, hatchType).also { fluidHatches[hatchType to tier] = it }
    }

    public fun setFluidHatchConfig(config: FluidHatchConfig) {
        fluidHatches[config.hatchType to config.tier.tier] = config
    }

    public fun updateFluidHatch(tier: Int, hatchType: HatchType, tankCount: Int? = null, tankCapacity: Long? = null) {
        val current = getFluidHatchConfig(tier, hatchType)
        val updated = current.copy(
            tankCount = tankCount ?: current.tankCount,
            tankCapacity = tankCapacity ?: current.tankCapacity
        )
        setFluidHatchConfig(updated)
    }

    public fun getFluidIOHatchConfig(tier: Int): FluidIOHatchConfig {
        return fluidIOHatches[tier]
            ?: FluidIOHatchConfig.createDefault(tier).also { fluidIOHatches[tier] = it }
    }

    public fun setFluidIOHatchConfig(config: FluidIOHatchConfig) {
        fluidIOHatches[config.tier.tier] = config
    }

    // endregion

    // region Energy

    public fun getEnergyHatchConfig(tier: Int, hatchType: HatchType): EnergyHatchConfig {
        return energyHatches[hatchType to tier]
            ?: EnergyHatchConfig.createDefault(tier, hatchType).also { energyHatches[hatchType to tier] = it }
    }

    public fun setEnergyHatchConfig(config: EnergyHatchConfig) {
        energyHatches[config.hatchType to config.tier.tier] = config
    }

    public fun updateEnergyHatch(tier: Int, hatchType: HatchType, capacity: Long? = null, maxTransfer: Long? = null) {
        val current = getEnergyHatchConfig(tier, hatchType)
        val updated = current.copy(
            capacity = capacity ?: current.capacity,
            maxTransfer = maxTransfer ?: current.maxTransfer
        )
        setEnergyHatchConfig(updated)
    }

    // endregion
}
