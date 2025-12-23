package github.kasuminova.prototypemachinery.api.machine.structure.logic

import net.minecraft.util.ResourceLocation
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry for [StructureValidator] factories.
 *
 * This exists primarily for JSON-loaded structures:
 * `StructureData.validators: List<String>` is deserialized as validator ids, which are then mapped
 * to concrete [StructureValidator] instances via this registry.
 *
 * 当前主要用于 JSON 结构：`validators` 字段存的是 id 字符串，loader 会在 PostInit 阶段
 * 通过本注册表把它们解析为实际的 [StructureValidator]。
 *
 * Notes:
 * - Current JSON schema carries only ids (no parameters), so the registry stores factories.
 * - Unknown ids are ignored by the loader (with a warning).
 */
public object StructureValidatorRegistry {

    private val factories: MutableMap<ResourceLocation, () -> StructureValidator> = ConcurrentHashMap()

    /** Register a validator factory. By default, duplicate ids are not allowed. */
    @JvmStatic
    public fun register(id: ResourceLocation, factory: () -> StructureValidator, replace: Boolean = false) {
        if (!replace) {
            val prev = factories.putIfAbsent(id, factory)
            require(prev == null) { "StructureValidator id already registered: $id" }
        } else {
            factories[id] = factory
        }
    }

    /** Convenience overload for registering a singleton validator instance. */
    @JvmStatic
    public fun register(id: ResourceLocation, validator: StructureValidator, replace: Boolean = false) {
        register(id, { validator }, replace)
    }

    /** Create a validator by id, or null if not registered. */
    @JvmStatic
    public fun create(id: ResourceLocation): StructureValidator? = factories[id]?.invoke()

    /** Returns whether a validator id is registered. */
    @JvmStatic
    public fun contains(id: ResourceLocation): Boolean = factories.containsKey(id)
}
