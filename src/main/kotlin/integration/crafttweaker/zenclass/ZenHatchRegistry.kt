package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass

import crafttweaker.annotations.ZenRegister
import github.kasuminova.prototypemachinery.common.block.hatch.HatchTier
import github.kasuminova.prototypemachinery.common.block.hatch.HatchType
import github.kasuminova.prototypemachinery.common.registry.HatchConfigRegistry
import github.kasuminova.prototypemachinery.common.registry.HatchRegisterer
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

/**
 * # ZenHatchRegistry - ZenScript Hatch Registry
 * # ZenHatchRegistry - ZenScript 仓位注册表
 *
 * ZenScript class for accessing and customizing hatch blocks.
 *
 * 用于访问和自定义仓位方块的 ZenScript 类。
 *
 * Usage 示例 / Example:
 * ```zenscript
 * import mods.prototypemachinery.HatchRegistry;
 *
 * // Get all tier names
 * val tiers = HatchRegistry.getTierNames();
 *
 * // Get hatch count
 * val count = HatchRegistry.getItemHatchCount();
 *
 * // Get tier capacity
 * val capacity = HatchRegistry.getEnergyCapacity(5); // IV tier
 * ```
 */
@ZenClass("mods.prototypemachinery.HatchRegistry")
@ZenRegister
public class ZenHatchRegistry private constructor() {

    public companion object {

        /**
         * Gets all tier names.
         * 获取所有等级名称。
         */
        @JvmStatic
        @ZenMethod
        public fun getTierNames(): Array<String> {
            return HatchTier.TIERS.map { it.name }.toTypedArray()
        }

        /**
         * Gets the total count of item hatches.
         * 获取物品仓总数。
         */
        @JvmStatic
        @ZenMethod
        public fun getItemHatchCount(): Int {
            return HatchRegisterer.getAllItemHatchBlocks().size +
                    HatchRegisterer.getAllItemIOHatchBlocks().size
        }

        /**
         * Gets the total count of fluid hatches.
         * 获取流体仓总数。
         */
        @JvmStatic
        @ZenMethod
        public fun getFluidHatchCount(): Int {
            return HatchRegisterer.getAllFluidHatchBlocks().size +
                    HatchRegisterer.getAllFluidIOHatchBlocks().size
        }

        /**
         * Gets the total count of energy hatches.
         * 获取能量仓总数。
         */
        @JvmStatic
        @ZenMethod
        public fun getEnergyHatchCount(): Int {
            return HatchRegisterer.getAllEnergyHatchBlocks().size
        }

        /**
         * Gets the slot count for an item hatch at a specific tier.
         * 获取特定等级物品仓的槽位数。
         *
         * @param tier Tier level (1-10)
         * @return Slot count for that tier
         */
        @JvmStatic
        @ZenMethod
        public fun getItemSlotCount(tier: Int): Int {
            return HatchConfigRegistry.getItemHatchConfig(tier, HatchType.INPUT).slotCount
        }

        /**
         * Gets the max stack size for an item hatch at a specific tier.
         * 获取特定等级物品仓的最大堆叠大小。
         *
         * @param tier Tier level (1-10)
         * @return Max stack size for that tier
         */
        @JvmStatic
        @ZenMethod
        public fun getItemMaxStackSize(tier: Int): Long {
            return HatchConfigRegistry.getItemHatchConfig(tier, HatchType.INPUT).maxStackSize
        }

        /**
         * Gets the tank count for a fluid hatch at a specific tier.
         * 获取特定等级流体仓的储罐数。
         *
         * @param tier Tier level (1-10)
         * @return Tank count for that tier
         */
        @JvmStatic
        @ZenMethod
        public fun getFluidTankCount(tier: Int): Int {
            return HatchConfigRegistry.getFluidHatchConfig(tier, HatchType.INPUT).tankCount
        }

        /**
         * Gets the tank capacity for a fluid hatch at a specific tier.
         * 获取特定等级流体仓的储罐容量。
         *
         * @param tier Tier level (1-10)
         * @return Tank capacity in mB for that tier
         */
        @JvmStatic
        @ZenMethod
        public fun getFluidTankCapacity(tier: Int): Long {
            return HatchConfigRegistry.getFluidHatchConfig(tier, HatchType.INPUT).tankCapacity
        }

        /**
         * Gets the energy capacity for an energy hatch at a specific tier.
         * 获取特定等级能量仓的能量容量。
         *
         * @param tier Tier level (1-10)
         * @return Energy capacity in FE for that tier
         */
        @JvmStatic
        @ZenMethod
        public fun getEnergyCapacity(tier: Int): Long {
            return HatchConfigRegistry.getEnergyHatchConfig(tier, HatchType.INPUT).capacity
        }

        /**
         * Gets the max transfer rate for an energy hatch at a specific tier.
         * 获取特定等级能量仓的最大传输速率。
         *
         * @param tier Tier level (1-10)
         * @return Max transfer rate in FE/t for that tier
         */
        @JvmStatic
        @ZenMethod
        public fun getEnergyMaxTransfer(tier: Int): Long {
            return HatchConfigRegistry.getEnergyHatchConfig(tier, HatchType.INPUT).maxTransfer
        }

        /**
         * Gets the tier name by tier level.
         * 通过等级获取等级名称。
         *
         * @param tier Tier level (1-10)
         * @return Tier name (e.g., "LV", "MV", "HV")
         */
        @JvmStatic
        @ZenMethod
        public fun getTierName(tier: Int): String {
            return HatchTier.fromTier(tier).name
        }

        /**
         * Gets the tier level by tier name.
         * 通过等级名称获取等级。
         *
         * @param name Tier name (e.g., "LV", "MV", "HV")
         * @return Tier level (1-10), or 1 if not found
         */
        @JvmStatic
        @ZenMethod
        public fun getTierLevel(name: String): Int {
            return HatchTier.TIERS.find {
                it.name.equals(name, ignoreCase = true)
            }?.tier ?: 1
        }

    }

}
