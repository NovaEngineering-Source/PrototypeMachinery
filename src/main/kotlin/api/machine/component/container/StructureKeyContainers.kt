package github.kasuminova.prototypemachinery.api.machine.component.container

import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.api.machine.component.StructureComponent
import github.kasuminova.prototypemachinery.api.util.PortMode
import github.kasuminova.prototypemachinery.api.util.TransactionMode
import net.minecraft.item.ItemStack
import net.minecraftforge.fluids.FluidStack

/**
 * Key-level container view for items.
 *
 * This is the preferred API for recipe IO and scanning.
 *
 * - No ItemStack predicate matching
 * - No per-slot snapshot/restore requirements
 * - All amounts are Long
 */
public interface StructureItemKeyContainer : StructureComponent {

    public fun isAllowedPortMode(ioType: PortMode): Boolean

    /** Inserts up to [amount] of [key]. Returns the amount actually inserted. */
    public fun insert(key: PMKey<ItemStack>, amount: Long, action: TransactionMode): Long

    /** Extracts up to [amount] of [key]. Returns the amount actually extracted. */
    public fun extract(key: PMKey<ItemStack>, amount: Long, action: TransactionMode): Long

    /** Unchecked variant that ignores PortMode restrictions (for rollback). */
    public fun insertUnchecked(key: PMKey<ItemStack>, amount: Long, action: TransactionMode): Long

    /** Unchecked variant that ignores PortMode restrictions (for rollback). */
    public fun extractUnchecked(key: PMKey<ItemStack>, amount: Long, action: TransactionMode): Long
}

/**
 * Key-level container view for fluids.
 *
 * NOTE: fluid key equality decides whether NBT/tag participates.
 */
public interface StructureFluidKeyContainer : StructureComponent {

    public fun isAllowedPortMode(ioType: PortMode): Boolean

    /** Inserts up to [amount] of [key]. Returns the amount actually inserted. */
    public fun insert(key: PMKey<FluidStack>, amount: Long, action: TransactionMode): Long

    /** Extracts up to [amount] of [key]. Returns the amount actually extracted. */
    public fun extract(key: PMKey<FluidStack>, amount: Long, action: TransactionMode): Long

    /** Unchecked variant that ignores PortMode restrictions (for rollback). */
    public fun insertUnchecked(key: PMKey<FluidStack>, amount: Long, action: TransactionMode): Long

    /** Unchecked variant that ignores PortMode restrictions (for rollback). */
    public fun extractUnchecked(key: PMKey<FluidStack>, amount: Long, action: TransactionMode): Long
}
