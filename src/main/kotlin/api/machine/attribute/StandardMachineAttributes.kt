package github.kasuminova.prototypemachinery.api.machine.attribute

import net.minecraft.client.resources.I18n
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

public object StandardMachineAttributes {

    public val MAX_CONCURRENT_PROCESSES: MachineAttributeType = MachineAttributeTypeImpl(
        ResourceLocation("prototypemachinery", "max_concurrent_processes")
    )

    public val PROCESS_PARALLELISM: MachineAttributeType = MachineAttributeTypeImpl(
        ResourceLocation("prototypemachinery", "process_parallelism")
    )

    public val PROCESS_SPEED: MachineAttributeType = MachineAttributeTypeImpl(
        ResourceLocation("prototypemachinery", "process_speed")
    )

    public val ENERGY_EFFICIENCY: MachineAttributeType = MachineAttributeTypeImpl(
        ResourceLocation("prototypemachinery", "energy_efficiency")
    )

    private class MachineAttributeTypeImpl(
        override val id: ResourceLocation,
        private val translationKey: String = "attribute.${id.namespace}.${id.path}"
    ) : MachineAttributeType {
        override val name: String
            @SideOnly(Side.CLIENT)
            get() = I18n.format(translationKey)
    }
}
