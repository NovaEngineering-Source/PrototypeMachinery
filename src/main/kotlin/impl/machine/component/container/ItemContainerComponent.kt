package github.kasuminova.prototypemachinery.impl.machine.component.container

import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent
import github.kasuminova.prototypemachinery.api.util.PortMode
import github.kasuminova.prototypemachinery.api.util.TransactionMode
import net.minecraft.item.ItemStack

public interface ItemContainerComponent : MachineComponent {

    public val slots: Int

    public val maxStackSize: Long

    public fun isAllowedPortMode(ioType: PortMode): Boolean

    public fun getItem(slot: Int): ItemStack

    public fun setItem(slot: Int, item: ItemStack)

    public fun insertItem(stack: ItemStack, action: TransactionMode): InsertResult

    public fun extractItem(amount: Long, action: TransactionMode, predicate: (ItemStack) -> Boolean): ExtractResult

    public sealed interface InsertResult {
        public data class Success(val remaining: ItemStack) : InsertResult
        public object Full : InsertResult
    }

    public sealed interface ExtractResult {
        public data class Success(val extracted: ItemStack) : ExtractResult
        public object Empty : ExtractResult
    }

}