package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.recipe

import crafttweaker.annotations.ZenRegister
import crafttweaker.api.item.IItemStack
import crafttweaker.api.liquid.ILiquidStack
import crafttweaker.api.minecraft.CraftTweakerMC
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.FuzzyInputGroup
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.RandomOutputPool
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.RequirementPropertyKeys
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.WeightedKey
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.impl.key.fluid.PMFluidKeyType
import github.kasuminova.prototypemachinery.impl.key.item.PMItemKeyType
import github.kasuminova.prototypemachinery.impl.recipe.requirement.EnergyRequirementComponent
import github.kasuminova.prototypemachinery.impl.recipe.requirement.FluidRequirementComponent
import github.kasuminova.prototypemachinery.impl.recipe.requirement.ItemRequirementComponent
import github.kasuminova.prototypemachinery.impl.recipe.requirement.component.ParallelismRequirementComponent
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fluids.FluidStack
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

/**
 * Script-friendly wrapper around a [RecipeRequirementComponent].
 */
@ZenClass("mods.prototypemachinery.recipe.PMRecipeRequirement")
@ZenRegister
public class ZenRecipeRequirement internal constructor(
    internal val component: RecipeRequirementComponent,
) {
    public companion object {

        @ZenMethod
        @JvmStatic
        public fun itemInput(id: String, stack: IItemStack): ZenRecipeRequirement {
            val mc: ItemStack = CraftTweakerMC.getItemStack(stack)
            require(!mc.isEmpty) { "itemInput: stack is empty" }
            return ZenRecipeRequirement(
                ItemRequirementComponent(
                    id = id,
                    inputs = listOf(PMItemKeyType.create(mc)),
                )
            )
        }

        @ZenMethod
        @JvmStatic
        public fun itemInputChance(id: String, stack: IItemStack, chance: Double): ZenRecipeRequirement {
            return itemInputChanceWithAttribute(id, stack, chance, null)
        }

        @ZenMethod
        @JvmStatic
        public fun itemInputChanceWithAttribute(id: String, stack: IItemStack, chance: Double, chanceAttribute: String?): ZenRecipeRequirement {
            val mc: ItemStack = CraftTweakerMC.getItemStack(stack)
            require(!mc.isEmpty) { "itemInputChance: stack is empty" }

            val props = linkedMapOf<String, Any>(RequirementPropertyKeys.CHANCE to chance)
            if (!chanceAttribute.isNullOrBlank()) {
                props[RequirementPropertyKeys.CHANCE_ATTRIBUTE] = ResourceLocation(chanceAttribute)
            }

            return ZenRecipeRequirement(
                ItemRequirementComponent(
                    id = id,
                    inputs = listOf(PMItemKeyType.create(mc)),
                    properties = props,
                )
            )
        }

        @ZenMethod
        @JvmStatic
        public fun itemOutput(id: String, stack: IItemStack): ZenRecipeRequirement {
            val mc: ItemStack = CraftTweakerMC.getItemStack(stack)
            require(!mc.isEmpty) { "itemOutput: stack is empty" }
            return ZenRecipeRequirement(
                ItemRequirementComponent(
                    id = id,
                    outputs = listOf(PMItemKeyType.create(mc)),
                )
            )
        }

        @ZenMethod
        @JvmStatic
        public fun itemOutputChance(id: String, stack: IItemStack, chance: Double): ZenRecipeRequirement {
            return itemOutputChanceWithAttribute(id, stack, chance, null)
        }

        @ZenMethod
        @JvmStatic
        public fun itemOutputChanceWithAttribute(id: String, stack: IItemStack, chance: Double, chanceAttribute: String?): ZenRecipeRequirement {
            val mc: ItemStack = CraftTweakerMC.getItemStack(stack)
            require(!mc.isEmpty) { "itemOutputChance: stack is empty" }

            val props = linkedMapOf<String, Any>(RequirementPropertyKeys.CHANCE to chance)
            if (!chanceAttribute.isNullOrBlank()) {
                props[RequirementPropertyKeys.CHANCE_ATTRIBUTE] = ResourceLocation(chanceAttribute)
            }

            return ZenRecipeRequirement(
                ItemRequirementComponent(
                    id = id,
                    outputs = listOf(PMItemKeyType.create(mc)),
                    properties = props,
                )
            )
        }

        @ZenMethod
        @JvmStatic
        public fun itemFuzzyInput(id: String, count: Long, candidates: Array<IItemStack>): ZenRecipeRequirement {
            require(count > 0) { "itemFuzzyInput: count must be > 0" }
            require(candidates.isNotEmpty()) { "itemFuzzyInput: candidates is empty" }

            val keys = candidates.map {
                val mc: ItemStack = CraftTweakerMC.getItemStack(it)
                require(!mc.isEmpty) { "itemFuzzyInput: candidate is empty" }
                PMItemKeyType.create(mc)
            }

            val group = FuzzyInputGroup(keys, count)
            val props = mapOf<String, Any>(RequirementPropertyKeys.FUZZY_INPUTS to listOf(group))

            return ZenRecipeRequirement(ItemRequirementComponent(id = id, properties = props))
        }

        @ZenMethod
        @JvmStatic
        public fun itemRandomOutput(id: String, pickCount: Int, candidates: Array<IItemStack>, weights: IntArray): ZenRecipeRequirement {
            require(pickCount > 0) { "itemRandomOutput: pickCount must be > 0" }
            require(candidates.isNotEmpty()) { "itemRandomOutput: candidates is empty" }
            require(candidates.size == weights.size) { "itemRandomOutput: weights size must match candidates size" }

            val weighted = candidates.mapIndexed { idx, st ->
                val mc: ItemStack = CraftTweakerMC.getItemStack(st)
                require(!mc.isEmpty) { "itemRandomOutput: candidate is empty" }
                WeightedKey(PMItemKeyType.create(mc), weights[idx])
            }

            val pool = RandomOutputPool(weighted, pickCount)
            val props = mapOf<String, Any>(RequirementPropertyKeys.RANDOM_OUTPUTS to pool)
            return ZenRecipeRequirement(ItemRequirementComponent(id = id, properties = props))
        }

        @ZenMethod
        @JvmStatic
        public fun fluidInput(id: String, stack: ILiquidStack): ZenRecipeRequirement {
            val mc: FluidStack? = CraftTweakerMC.getLiquidStack(stack)
            require(mc != null && mc.amount > 0) { "fluidInput: stack is null/empty" }
            return ZenRecipeRequirement(
                FluidRequirementComponent(
                    id = id,
                    inputs = listOf(PMFluidKeyType.create(mc)),
                )
            )
        }

        @ZenMethod
        @JvmStatic
        public fun fluidInputChance(id: String, stack: ILiquidStack, chance: Double): ZenRecipeRequirement {
            return fluidInputChanceWithAttribute(id, stack, chance, null)
        }

        @ZenMethod
        @JvmStatic
        public fun fluidInputChanceWithAttribute(id: String, stack: ILiquidStack, chance: Double, chanceAttribute: String?): ZenRecipeRequirement {
            val mc: FluidStack? = CraftTweakerMC.getLiquidStack(stack)
            require(mc != null && mc.amount > 0) { "fluidInputChance: stack is null/empty" }

            val props = linkedMapOf<String, Any>(RequirementPropertyKeys.CHANCE to chance)
            if (!chanceAttribute.isNullOrBlank()) {
                props[RequirementPropertyKeys.CHANCE_ATTRIBUTE] = ResourceLocation(chanceAttribute)
            }

            return ZenRecipeRequirement(
                FluidRequirementComponent(
                    id = id,
                    inputs = listOf(PMFluidKeyType.create(mc)),
                    properties = props,
                )
            )
        }

        @ZenMethod
        @JvmStatic
        public fun fluidOutput(id: String, stack: ILiquidStack): ZenRecipeRequirement {
            val mc: FluidStack? = CraftTweakerMC.getLiquidStack(stack)
            require(mc != null && mc.amount > 0) { "fluidOutput: stack is null/empty" }
            return ZenRecipeRequirement(
                FluidRequirementComponent(
                    id = id,
                    outputs = listOf(PMFluidKeyType.create(mc)),
                )
            )
        }

        @ZenMethod
        @JvmStatic
        public fun fluidOutputChance(id: String, stack: ILiquidStack, chance: Double): ZenRecipeRequirement {
            return fluidOutputChanceWithAttribute(id, stack, chance, null)
        }

        @ZenMethod
        @JvmStatic
        public fun fluidOutputChanceWithAttribute(id: String, stack: ILiquidStack, chance: Double, chanceAttribute: String?): ZenRecipeRequirement {
            val mc: FluidStack? = CraftTweakerMC.getLiquidStack(stack)
            require(mc != null && mc.amount > 0) { "fluidOutputChance: stack is null/empty" }

            val props = linkedMapOf<String, Any>(RequirementPropertyKeys.CHANCE to chance)
            if (!chanceAttribute.isNullOrBlank()) {
                props[RequirementPropertyKeys.CHANCE_ATTRIBUTE] = ResourceLocation(chanceAttribute)
            }

            return ZenRecipeRequirement(
                FluidRequirementComponent(
                    id = id,
                    outputs = listOf(PMFluidKeyType.create(mc)),
                    properties = props,
                )
            )
        }

        @ZenMethod
        @JvmStatic
        public fun fluidFuzzyInput(id: String, amount: Long, candidates: Array<ILiquidStack>): ZenRecipeRequirement {
            require(amount > 0) { "fluidFuzzyInput: amount must be > 0" }
            require(candidates.isNotEmpty()) { "fluidFuzzyInput: candidates is empty" }

            val keys = candidates.map {
                val mc: FluidStack? = CraftTweakerMC.getLiquidStack(it)
                require(mc != null && mc.amount > 0) { "fluidFuzzyInput: candidate is null/empty" }
                val k = PMFluidKeyType.create(mc)
                k.count = amount
                k
            }

            val group = FuzzyInputGroup(keys, amount)
            val props = mapOf<String, Any>(RequirementPropertyKeys.FUZZY_INPUTS to listOf(group))

            return ZenRecipeRequirement(FluidRequirementComponent(id = id, properties = props))
        }

        @ZenMethod
        @JvmStatic
        public fun fluidRandomOutput(id: String, pickCount: Int, candidates: Array<ILiquidStack>, weights: IntArray): ZenRecipeRequirement {
            require(pickCount > 0) { "fluidRandomOutput: pickCount must be > 0" }
            require(candidates.isNotEmpty()) { "fluidRandomOutput: candidates is empty" }
            require(candidates.size == weights.size) { "fluidRandomOutput: weights size must match candidates size" }

            val weighted = candidates.mapIndexed { idx, st ->
                val mc: FluidStack? = CraftTweakerMC.getLiquidStack(st)
                require(mc != null && mc.amount > 0) { "fluidRandomOutput: candidate is null/empty" }
                WeightedKey(PMFluidKeyType.create(mc), weights[idx])
            }

            val pool = RandomOutputPool(weighted, pickCount)
            val props = mapOf<String, Any>(RequirementPropertyKeys.RANDOM_OUTPUTS to pool)
            return ZenRecipeRequirement(FluidRequirementComponent(id = id, properties = props))
        }

        @ZenMethod
        @JvmStatic
        public fun fluidInputPerTick(id: String, stack: ILiquidStack): ZenRecipeRequirement {
            val mc: FluidStack? = CraftTweakerMC.getLiquidStack(stack)
            require(mc != null && mc.amount > 0) { "fluidInputPerTick: stack is null/empty" }
            return ZenRecipeRequirement(
                FluidRequirementComponent(
                    id = id,
                    inputsPerTick = listOf(PMFluidKeyType.create(mc)),
                )
            )
        }

        @ZenMethod
        @JvmStatic
        public fun fluidOutputPerTick(id: String, stack: ILiquidStack): ZenRecipeRequirement {
            val mc: FluidStack? = CraftTweakerMC.getLiquidStack(stack)
            require(mc != null && mc.amount > 0) { "fluidOutputPerTick: stack is null/empty" }
            return ZenRecipeRequirement(
                FluidRequirementComponent(
                    id = id,
                    outputsPerTick = listOf(PMFluidKeyType.create(mc)),
                )
            )
        }

        @ZenMethod
        @JvmStatic
        public fun energy(id: String, input: Long, output: Long): ZenRecipeRequirement {
            return ZenRecipeRequirement(
                EnergyRequirementComponent(
                    id = id,
                    input = input,
                    output = output,
                )
            )
        }

        @ZenMethod
        @JvmStatic
        public fun energyPerTick(id: String, inputPerTick: Long, outputPerTick: Long): ZenRecipeRequirement {
            return ZenRecipeRequirement(
                EnergyRequirementComponent(
                    id = id,
                    inputPerTick = inputPerTick,
                    outputPerTick = outputPerTick,
                )
            )
        }

        @ZenMethod
        @JvmStatic
        public fun parallelism(id: String, parallelism: Long): ZenRecipeRequirement {
            require(parallelism >= 1) { "parallelism must be >= 1" }
            return ZenRecipeRequirement(ParallelismRequirementComponent(id = id, parallelism = parallelism))
        }
    }
}
