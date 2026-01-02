package github.kasuminova.prototypemachinery.api.recipe.requirement.overlay

import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import net.minecraft.util.ResourceLocation
import java.util.concurrent.ConcurrentHashMap

/**
 * # RecipeRequirementOverlayApplierRegistry
 * # 需求 Overlay 应用器注册表
 *
 * A small in-memory registry for [RecipeRequirementOverlayApplier].
 *
 * 这是一个简单的内存注册表，用于让 RequirementOverlay 可扩展，避免硬编码。
 */
public object RecipeRequirementOverlayApplierRegistry {

    private val appliers: MutableMap<ResourceLocation, RecipeRequirementOverlayApplier<*>> = ConcurrentHashMap()

    @JvmStatic
    public fun <C : RecipeRequirementComponent> register(applier: RecipeRequirementOverlayApplier<C>) {
        appliers[applier.type.id] = applier
    }

    @JvmStatic
    public fun <C : RecipeRequirementComponent> register(type: RecipeRequirementType<C>, applier: RecipeRequirementOverlayApplier<C>) {
        // Keep a single source of truth for the key.
        if (applier.type.id != type.id) {
            // Programmer error; fail fast to avoid confusing overlay behavior.
            error("Overlay applier type mismatch: expected=$type, actual=${applier.type}")
        }
        appliers[type.id] = applier
    }

    @JvmStatic
    public fun get(typeId: ResourceLocation): RecipeRequirementOverlayApplier<*>? {
        return appliers[typeId]
    }
}
