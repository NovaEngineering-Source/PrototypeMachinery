package github.kasuminova.prototypemachinery.api.machine.attribute
import net.minecraft.client.resources.I18n
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

/**
 * # StandardMachineAttributes - Built-in machine attributes
 * # StandardMachineAttributes - 内置机器属性
 *
 * Defines the framework-provided [MachineAttributeType] set.
 * These attributes are commonly used by built-in systems (parallelism, speed, etc.).
 *
 * 定义框架提供的一组 [MachineAttributeType]。
 * 这些属性通常被内置系统使用（并行度、速度等）。
 *
 */
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

    init {
        // Register built-in attribute types into the global registry.
        MachineAttributeRegistry.register(MAX_CONCURRENT_PROCESSES)
        MachineAttributeRegistry.register(PROCESS_PARALLELISM)
        MachineAttributeRegistry.register(PROCESS_SPEED)
        MachineAttributeRegistry.register(ENERGY_EFFICIENCY)
    }

    private class MachineAttributeTypeImpl(
        override val id: ResourceLocation,
        private val translationKey: String = "attribute.${id.namespace}.${id.path}"
    ) : MachineAttributeType {
        override val name: String
            @SideOnly(Side.CLIENT)
            get() = I18n.format(translationKey)
    }
}
