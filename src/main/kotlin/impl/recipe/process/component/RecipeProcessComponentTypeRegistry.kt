package github.kasuminova.prototypemachinery.impl.recipe.process.component

import github.kasuminova.prototypemachinery.api.recipe.process.component.RecipeProcessComponentType
import github.kasuminova.prototypemachinery.api.recipe.process.component.type.ZSDataProcessComponentType
import net.minecraft.util.ResourceLocation
import java.util.concurrent.ConcurrentHashMap

/**
 * Global registry for [RecipeProcessComponentType]s.
 * RecipeProcess 组件类型注册表（用于从 NBT 反序列化时按 id 创建组件实例）。
 */
public object RecipeProcessComponentTypeRegistry {

    private val types: MutableMap<ResourceLocation, RecipeProcessComponentType<*>> = ConcurrentHashMap()

    init {
        // Built-ins
        register(ZSDataProcessComponentType)
        register(RecipeOverlayProcessComponentType)
        register(SelectiveStateProcessComponentType)
        register(RecipeLifecycleStateProcessComponentType)
    }

    public fun register(type: RecipeProcessComponentType<*>) {
        types[type.id] = type
    }

    public fun get(id: ResourceLocation): RecipeProcessComponentType<*>? = types[id]

    public fun all(): Collection<RecipeProcessComponentType<*>> = types.values
}
