package github.kasuminova.prototypemachinery.api.recipe.scanning

import net.minecraft.util.ResourceLocation
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry for [RecipeParallelismConstraint].
 *
 * 配方并行可行性约束注册表。
 *
 * This is intentionally minimal:
 * - The core mod registers default constraints for built-in requirement types.
 * - Addons can register constraints for their custom requirement types to participate in scan-time parallelism.
 */
public object RecipeParallelismConstraintRegistry {

    private val constraints: MutableMap<ResourceLocation, RecipeParallelismConstraint> = ConcurrentHashMap()

    @JvmStatic
    public fun register(constraint: RecipeParallelismConstraint): RecipeParallelismConstraint {
        constraints[constraint.requirementTypeId] = constraint
        return constraint
    }

    @JvmStatic
    public fun get(requirementTypeId: ResourceLocation): RecipeParallelismConstraint? {
        return constraints[requirementTypeId]
    }

    @JvmStatic
    public fun all(): Collection<RecipeParallelismConstraint> {
        return constraints.values
    }
}
