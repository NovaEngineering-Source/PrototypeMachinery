package github.kasuminova.prototypemachinery.api.machine.component.type

import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent
import net.minecraft.item.ItemStack

public interface ItemContainerComponent : MachineComponent {

    public val slots: Int

    public fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack

    public fun extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack

    public fun getStackInSlot(slot: Int): ItemStack

    public fun setStackInSlot(slot: Int, stack: ItemStack)

}
