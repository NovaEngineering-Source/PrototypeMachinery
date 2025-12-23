package github.kasuminova.prototypemachinery.impl.machine.component.container

import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent
import github.kasuminova.prototypemachinery.api.util.PortMode
import github.kasuminova.prototypemachinery.api.util.TransactionMode
import net.minecraftforge.fluids.Fluid
import net.minecraftforge.fluids.FluidStack

public interface FluidContainerComponent : MachineComponent {

    public val tanks: Int

    public val maxFluidAmount: Long

    public fun isAllowedPortMode(mode: PortMode): Boolean

    public fun getFluidAmount(tank: Int): Long

    public fun setFluidAmount(tank: Int, amount: Long)

    public fun insertFluid(fluid: FluidStack, amount: Long, mode: TransactionMode): InsertResult

    public fun extractFluid(fluid: Fluid, amount: Long, mode: TransactionMode): ExtractResult

    public sealed interface InsertResult {
        public data class Success(val remaining: Long) : InsertResult
        public object Full : InsertResult
    }

    public sealed interface ExtractResult {
        public data class Success(val extracted: FluidStack, val amount: Long) : ExtractResult
        public object Empty : ExtractResult
    }

}