package github.kasuminova.prototypemachinery.integration.jei.registry

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.integration.jei.api.JeiRecipeContext
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotKinds
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fluids.FluidStack
import java.util.concurrent.ConcurrentHashMap

/**
 * Provides fixed (node-less) JEI ingredient values for layouts.
 *
 * Fixed slots are identified by [PMJeiFixedSlotProvider.id] (a stable [ResourceLocation]).
 * A layout can place a fixed slot by referencing that provider id.
 */
public object JeiFixedSlotProviderRegistry {

    public interface PMJeiFixedSlotProvider {
        /** Stable id referenced by layouts/scripts. */
        public val id: ResourceLocation

        /** The slot kind id (must have a registered [github.kasuminova.prototypemachinery.integration.jei.api.ingredient.PMJeiIngredientKindHandler]). */
        public val kindId: ResourceLocation

        /** Values to display in the slot for the given recipe context. */
        public fun getDisplayed(ctx: JeiRecipeContext): List<Any>
    }

    private val providers: MutableMap<ResourceLocation, PMJeiFixedSlotProvider> = ConcurrentHashMap()

    public fun clear() {
        providers.clear()
    }

    /**
     * Remove a provider by id.
     *
     * @return true if a provider existed and was removed.
     */
    public fun remove(id: ResourceLocation): Boolean {
        return providers.remove(id) != null
    }

    public fun snapshot(): Map<ResourceLocation, PMJeiFixedSlotProvider> {
        return LinkedHashMap(providers)
    }

    public fun register(
        provider: PMJeiFixedSlotProvider,
        replace: Boolean = true,
    ) {
        val id = provider.id
        if (!replace && providers.containsKey(id)) {
            PrototypeMachinery.logger.warn(
                "JEI fixed slot provider already registered for id '$id'. Skipping because replace=false."
            )
            return
        }

        val prev = providers.put(id, provider)
        if (prev != null && prev !== provider) {
            PrototypeMachinery.logger.info(
                "JEI fixed slot provider replaced for id '$id': ${prev::class.java.name} -> ${provider::class.java.name}"
            )
        }
    }

    public fun get(id: ResourceLocation): PMJeiFixedSlotProvider? {
        return providers[id]
    }

    public fun has(id: ResourceLocation): Boolean {
        return providers.containsKey(id)
    }

    /** Convenience: register a constant item provider. */
    public fun registerItem(
        id: ResourceLocation,
        values: List<ItemStack>,
        replace: Boolean = true,
    ) {
        register(
            provider = object : PMJeiFixedSlotProvider {
                override val id: ResourceLocation = id
                override val kindId: ResourceLocation = JeiSlotKinds.ITEM.id
                override fun getDisplayed(ctx: JeiRecipeContext): List<Any> = values
            },
            replace = replace,
        )
    }

    /** Convenience: register a constant fluid provider. */
    public fun registerFluid(
        id: ResourceLocation,
        values: List<FluidStack>,
        replace: Boolean = true,
    ) {
        register(
            provider = object : PMJeiFixedSlotProvider {
                override val id: ResourceLocation = id
                override val kindId: ResourceLocation = JeiSlotKinds.FLUID.id
                override fun getDisplayed(ctx: JeiRecipeContext): List<Any> = values
            },
            replace = replace,
        )
    }
}
