package github.kasuminova.prototypemachinery.common.registry

import github.kasuminova.prototypemachinery.common.block.hatch.HatchType
import github.kasuminova.prototypemachinery.common.block.hatch.energy.EnergyHatchBlockEntity
import github.kasuminova.prototypemachinery.common.block.hatch.fluid.FluidHatchBlockEntity
import github.kasuminova.prototypemachinery.common.block.hatch.fluid.FluidIOHatchBlockEntity
import github.kasuminova.prototypemachinery.common.block.hatch.item.ItemHatchBlockEntity
import github.kasuminova.prototypemachinery.common.block.hatch.item.ItemIOHatchBlockEntity
import net.minecraft.tileentity.TileEntity
import net.minecraftforge.common.DimensionManager

/**
 * Bridge that applies HatchConfigRegistry changes to already-loaded TileEntities.
 *
 * Notes:
 * - This runs server-side and only touches loaded worlds.
 * - If a player currently has a hatch UI open, they may need to reopen it
 *   because existing sync handlers can hold references to old storage instances.
 */
public object HatchConfigUpdateBridge {

    private inline fun forEachLoadedTileEntity(consumer: (TileEntity) -> Unit) {
        val worlds = runCatching { DimensionManager.getWorlds() }.getOrNull() ?: return
        for (world in worlds) {
            // Copy to avoid CME if tiles change while iterating.
            val tiles = world.loadedTileEntityList.toList()
            tiles.forEach(consumer)
        }
    }

    // region Item

    public fun applyItemTier(tier: Int) {
        applyItemHatch(tier, HatchType.INPUT)
        applyItemHatch(tier, HatchType.OUTPUT)
        applyItemIOHatch(tier)
    }

    public fun applyItemHatch(tier: Int, hatchType: HatchType) {
        val newConfig = HatchConfigRegistry.getItemHatchConfig(tier, hatchType)
        forEachLoadedTileEntity { te ->
            if (te is ItemHatchBlockEntity) {
                val old = te.config
                if (old.tier.tier == tier && old.hatchType == hatchType) {
                    te.applyConfig(newConfig)
                }
            }
        }
    }

    public fun applyItemIOHatch(tier: Int) {
        val newConfig = HatchConfigRegistry.getItemIOHatchConfig(tier)
        forEachLoadedTileEntity { te ->
            if (te is ItemIOHatchBlockEntity) {
                val old = te.config
                if (old.tier.tier == tier) {
                    te.applyConfig(newConfig)
                }
            }
        }
    }

    // endregion

    // region Fluid

    public fun applyFluidTier(tier: Int) {
        applyFluidHatch(tier, HatchType.INPUT)
        applyFluidHatch(tier, HatchType.OUTPUT)
        applyFluidIOHatch(tier)
    }

    public fun applyFluidHatch(tier: Int, hatchType: HatchType) {
        val newConfig = HatchConfigRegistry.getFluidHatchConfig(tier, hatchType)
        forEachLoadedTileEntity { te ->
            if (te is FluidHatchBlockEntity) {
                val old = te.config
                if (old.tier.tier == tier && old.hatchType == hatchType) {
                    te.applyConfig(newConfig)
                }
            }
        }
    }

    public fun applyFluidIOHatch(tier: Int) {
        val newConfig = HatchConfigRegistry.getFluidIOHatchConfig(tier)
        forEachLoadedTileEntity { te ->
            if (te is FluidIOHatchBlockEntity) {
                val old = te.config
                if (old.tier.tier == tier) {
                    te.applyConfig(newConfig)
                }
            }
        }
    }

    // endregion

    // region Energy

    public fun applyEnergyTier(tier: Int) {
        applyEnergyHatch(tier, HatchType.INPUT)
        applyEnergyHatch(tier, HatchType.OUTPUT)
        applyEnergyHatch(tier, HatchType.IO)
    }

    public fun applyEnergyHatch(tier: Int, hatchType: HatchType) {
        val newConfig = HatchConfigRegistry.getEnergyHatchConfig(tier, hatchType)
        forEachLoadedTileEntity { te ->
            if (te is EnergyHatchBlockEntity) {
                val old = te.config
                if (old.tier.tier == tier && old.hatchType == hatchType) {
                    te.applyConfig(newConfig)
                }
            }
        }
    }

    // endregion
}
