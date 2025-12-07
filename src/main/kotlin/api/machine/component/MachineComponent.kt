package github.kasuminova.prototypemachinery.api.machine.component

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import net.minecraft.nbt.NBTTagCompound

public interface MachineComponent {

    public val type: MachineComponentType<*>

    public val owner: MachineInstance

    public val provider: Any?

    public fun onLoad() {}

    public fun onUnload() {}

    public interface Serializable : MachineComponent {

        public fun writeNBT(): NBTTagCompound

        public fun readNBT(nbt: NBTTagCompound)

    }

}