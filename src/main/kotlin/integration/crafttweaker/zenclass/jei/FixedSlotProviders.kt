package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.jei

import crafttweaker.annotations.ZenRegister
import crafttweaker.api.item.IItemStack
import crafttweaker.api.liquid.ILiquidStack
import crafttweaker.api.minecraft.CraftTweakerMC
import github.kasuminova.prototypemachinery.integration.jei.registry.JeiFixedSlotProviderRegistry
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fluids.FluidStack
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

/**
 * Fixed (node-less) JEI ingredient providers.
 *
 * 固定值（不绑定配方 requirement node）的 JEI 槽位 provider 注册表。
 *
 * Providers are referenced by id in layouts via LayoutBuilder.placeFixedSlot(providerId, ...).
 * 固定槽位在布局里通过 providerId 引用（LayoutBuilder.placeFixedSlot(providerId, ...)）。
 */
@ZenClass("mods.prototypemachinery.jei.FixedSlotProviders")
@ZenRegister
public class FixedSlotProviders {

    public companion object {

        /**
         * Register a fixed item provider.
         *
         * 注册一个固定物品 provider。
         */
        @ZenMethod
        @JvmStatic
        public fun registerItem(providerId: String, value: IItemStack, replace: Boolean = true): Unit {
            val id = ResourceLocation(providerId)
            val mc: ItemStack = CraftTweakerMC.getItemStack(value)
            require(!mc.isEmpty) { "registerItem: stack is empty" }

            JeiFixedSlotProviderRegistry.registerItem(id = id, values = listOf(mc), replace = replace)
        }

        /**
         * Register a fixed item provider with multiple displayed values.
         *
         * 注册一个固定物品 provider，并提供多个显示值（JEI 会轮播/聚焦这些值）。
         */
        @ZenMethod
        @JvmStatic
        public fun registerItems(providerId: String, values: Array<IItemStack>, replace: Boolean = true): Unit {
            val id = ResourceLocation(providerId)
            val out = ArrayList<ItemStack>(values.size)
            for (v in values) {
                val mc: ItemStack = CraftTweakerMC.getItemStack(v)
                if (mc.isEmpty) continue
                out += mc
            }
            require(out.isNotEmpty()) { "registerItems: all stacks are empty" }

            JeiFixedSlotProviderRegistry.registerItem(id = id, values = out, replace = replace)
        }

        /**
         * Register a fixed fluid provider.
         *
         * 注册一个固定流体 provider。
         */
        @ZenMethod
        @JvmStatic
        public fun registerFluid(providerId: String, value: ILiquidStack, replace: Boolean = true): Unit {
            val id = ResourceLocation(providerId)
            val mc: FluidStack? = CraftTweakerMC.getLiquidStack(value)
            require(mc != null && mc.amount > 0) { "registerFluid: stack is null/empty" }

            JeiFixedSlotProviderRegistry.registerFluid(id = id, values = listOf(mc), replace = replace)
        }

        /**
         * Register a fixed fluid provider with multiple displayed values.
         *
         * 注册一个固定流体 provider，并提供多个显示值（JEI 会轮播/聚焦这些值）。
         */
        @ZenMethod
        @JvmStatic
        public fun registerFluids(providerId: String, values: Array<ILiquidStack>, replace: Boolean = true): Unit {
            val id = ResourceLocation(providerId)
            val out = ArrayList<FluidStack>(values.size)
            for (v in values) {
                val mc: FluidStack? = CraftTweakerMC.getLiquidStack(v)
                if (mc == null || mc.amount <= 0) continue
                out += mc
            }
            require(out.isNotEmpty()) { "registerFluids: all stacks are null/empty" }

            JeiFixedSlotProviderRegistry.registerFluid(id = id, values = out, replace = replace)
        }

        /**
         * Remove a provider by id.
         *
         * 按 id 移除一个 provider。
         */
        @ZenMethod
        @JvmStatic
        public fun clear(providerId: String): Boolean {
            return JeiFixedSlotProviderRegistry.remove(ResourceLocation(providerId))
        }

        /**
         * Clear all fixed slot providers.
         *
         * 清空所有固定槽位 provider。
         */
        @ZenMethod
        @JvmStatic
        public fun clearAll(): Unit {
            JeiFixedSlotProviderRegistry.clear()
        }

        /**
         * Check if a provider exists.
         *
         * 检查指定 providerId 是否已注册。
         */
        @ZenMethod
        @JvmStatic
        public fun has(providerId: String): Boolean {
            return JeiFixedSlotProviderRegistry.has(ResourceLocation(providerId))
        }
    }
}
