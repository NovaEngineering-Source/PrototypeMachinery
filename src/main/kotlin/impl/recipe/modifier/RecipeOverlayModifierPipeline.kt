package github.kasuminova.prototypemachinery.impl.recipe.modifier

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess

/**
 * Applies a list of overlay modifier ids to a process.
 *
 * Stable order: apply in the order of [modifierIds].
 */
public object RecipeOverlayModifierPipeline {

    public fun apply(machine: MachineInstance, process: RecipeProcess, modifierIds: List<String>) {
        if (modifierIds.isEmpty()) return

        val ctx = RecipeOverlayModifierContext(machine, process)
        modifierIds.forEachIndexed { _, id ->
            val modifier = RecipeOverlayModifierRegistry.get(id)
                ?: error("Unknown recipe overlay modifier id: $id")
            modifier.apply(ctx)
        }
    }
}
