package github.kasuminova.prototypemachinery.api.ui.action

import github.kasuminova.prototypemachinery.common.block.entity.MachineBlockEntity
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.ResourceLocation

/**
 * Registry for UI actions (client -> server).
 */
public interface UIActionRegistry {

    public fun interface Handler {
        public fun handle(player: EntityPlayerMP, machine: MachineBlockEntity, payload: NBTTagCompound)
    }

    public fun register(
        machineId: ResourceLocation?,
        actionKey: String,
        handler: Handler,
        owner: String = "unknown"
    )

    public fun clear(machineId: ResourceLocation)

    public fun clearAll()

    /**
     * Invoke action if registered. Returns true if handled.
     */
    public fun invoke(player: EntityPlayerMP, machine: MachineBlockEntity, actionKey: String, payload: NBTTagCompound): Boolean
}
