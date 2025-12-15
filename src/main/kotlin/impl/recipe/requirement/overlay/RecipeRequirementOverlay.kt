package github.kasuminova.prototypemachinery.impl.recipe.requirement.overlay

import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.process.component.RecipeProcessComponent
import github.kasuminova.prototypemachinery.impl.recipe.process.component.RecipeOverlayProcessComponent
import github.kasuminova.prototypemachinery.impl.recipe.process.component.RecipeOverlayProcessComponentType

/**
 * Utilities to resolve effective requirement components with per-process overlay applied.
 * 使用 per-process overlay 解析“生效的”需求组件。
 */
public object RecipeRequirementOverlay {

    public fun get(process: RecipeProcess): RecipeOverlayProcessComponent? = process[RecipeOverlayProcessComponentType]

    public fun getOrCreate(process: RecipeProcess): RecipeOverlayProcessComponent {
        val existing = process[RecipeOverlayProcessComponentType]
        if (existing != null) return existing

        val created = RecipeOverlayProcessComponentType.createComponent(process)
        process.components.addTail(RecipeOverlayProcessComponentType, created as RecipeProcessComponent)
        return created
    }

    /**
     * Resolve effective component.
     * Unknown component types are returned as-is.
     */
    @Suppress("UNCHECKED_CAST")
    public fun <T : Any> resolve(process: RecipeProcess, component: T): T {
        val overlay = get(process) ?: return component
        return overlay.applyTo(component) as T
    }
}
