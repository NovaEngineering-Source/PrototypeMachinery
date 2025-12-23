package github.kasuminova.prototypemachinery.api.machine.attribute

import github.kasuminova.prototypemachinery.api.machine.attribute.StandardMachineAttributes.getById
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
 * ## TODO (Temporary registry) / TODO（临时注册表）
 *
 * This object currently doubles as a minimal “attribute registry” (via [getById]).
 * It is mainly for NBT deserialization, and should eventually be replaced by a real registry
 * that supports third-party attribute registration.
 *
 * 当前对象也充当了一个最小的“属性注册表”（通过 [getById]）。
 * 这主要用于 NBT 反序列化，后续应替换为真正的注册表，以支持第三方注册属性。
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

    private val byId: Map<ResourceLocation, MachineAttributeType> by lazy {
        listOf(
            MAX_CONCURRENT_PROCESSES,
            PROCESS_PARALLELISM,
            PROCESS_SPEED,
            ENERGY_EFFICIENCY,
        ).associateBy { it.id }
    }

    public fun getById(id: ResourceLocation): MachineAttributeType? = byId[id]

    private class MachineAttributeTypeImpl(
        override val id: ResourceLocation,
        private val translationKey: String = "attribute.${id.namespace}.${id.path}"
    ) : MachineAttributeType {
        override val name: String
            @SideOnly(Side.CLIENT)
            get() = I18n.format(translationKey)
    }
}
