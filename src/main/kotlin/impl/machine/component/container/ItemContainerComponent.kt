package github.kasuminova.prototypemachinery.impl.machine.component.container

import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent
import github.kasuminova.prototypemachinery.common.util.Action
import github.kasuminova.prototypemachinery.common.util.IOType
import net.minecraft.item.ItemStack

public interface ItemContainerComponent : MachineComponent {

    public val slots: Int

    public val maxStackSize: Long

    public fun isAllowedIOType(ioType: IOType): Boolean

    public fun getItem(slot: Int): ItemStack

    public fun setItem(slot: Int, item: ItemStack)

    public fun insertItem(stack: ItemStack, action: Action): InsertResult

    public fun extractItem(amount: Long, action: Action, predicate: (ItemStack) -> Boolean): ExtractResult

    public sealed interface InsertResult {
        public data class Success(val remaining: ItemStack) : InsertResult
        public object Full : InsertResult
    }

    public sealed interface ExtractResult {
        public data class Success(val extracted: ItemStack) : ExtractResult
        public object Empty : ExtractResult
    }

}