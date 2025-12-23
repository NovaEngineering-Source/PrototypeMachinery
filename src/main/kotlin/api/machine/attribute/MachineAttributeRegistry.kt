package github.kasuminova.prototypemachinery.api.machine.attribute

import net.minecraft.util.ResourceLocation
import java.util.concurrent.ConcurrentHashMap

/**
 * Global registry for [MachineAttributeType].
 *
 * This is the authoritative registry used for NBT (de)serialization and any dynamic attribute lookups.
 *
 * 不考虑向后兼容：反序列化遇到未知 attribute id 时，调用方应选择直接报错/失败。
 */
public object MachineAttributeRegistry {

    private val byId: MutableMap<ResourceLocation, MachineAttributeType> = ConcurrentHashMap()

    /**
     * Ensure the built-in attribute set is registered.
     *
     * Kotlin `object` initializers are lazy; if callers only touch this registry and never
     * reference [StandardMachineAttributes], the built-in attributes would otherwise remain
     * unregistered.
     */
    @Suppress("unused")
    private val ensureBuiltinsRegistered: Any = StandardMachineAttributes

    /** Register an attribute type. */
    @JvmStatic
    public fun register(type: MachineAttributeType, replace: Boolean = false) {
        val id = type.id
        if (!replace) {
            val prev = byId.putIfAbsent(id, type)
            require(prev == null) { "MachineAttributeType already registered: $id" }
        } else {
            byId[id] = type
        }
    }

    /** Lookup an attribute type by id, or null if not registered. */
    @JvmStatic
    public fun get(id: ResourceLocation): MachineAttributeType? = byId[id]

    /** Lookup an attribute type by id; throws if not registered. */
    @JvmStatic
    public fun require(id: ResourceLocation): MachineAttributeType {
        return get(id) ?: error("Unknown MachineAttributeType id: $id")
    }

    /** Snapshot all registered attribute types. */
    @JvmStatic
    public fun all(): List<MachineAttributeType> = byId.values.toList()

    /** Clears registry. Intended for tests only. */
    @JvmStatic
    @JvmSynthetic
    internal fun clearForTests() {
        byId.clear()
    }
}
