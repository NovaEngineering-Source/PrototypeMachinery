package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.recipe

import crafttweaker.annotations.ZenRegister
import crafttweaker.api.item.IItemStack
import crafttweaker.api.liquid.ILiquidStack
import crafttweaker.api.minecraft.CraftTweakerMC
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.impl.key.fluid.PMFluidKeyType
import github.kasuminova.prototypemachinery.impl.key.item.PMItemKeyType
import github.kasuminova.prototypemachinery.impl.recipe.requirement.EnergyRequirementComponent
import github.kasuminova.prototypemachinery.impl.recipe.requirement.FluidRequirementComponent
import github.kasuminova.prototypemachinery.impl.recipe.requirement.ItemRequirementComponent
import github.kasuminova.prototypemachinery.impl.recipe.requirement.component.ParallelismRequirementComponent
import net.minecraft.item.ItemStack
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
