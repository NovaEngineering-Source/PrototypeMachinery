package github.kasuminova.prototypemachinery.api.machine.attribute

public interface MachineAttributeInstance {

    public val attribute: MachineAttributeType

    public val modifiers: Map<String, MachineAttributeModifier>

    public var base: Double

    public val value: Double

    public fun addModifier(modifier: MachineAttributeModifier): Boolean

    public fun removeModifier(id: String): MachineAttributeModifier?

    public fun hasModifier(id: String): Boolean

    public fun getModifier(id: String): MachineAttributeModifier?

}