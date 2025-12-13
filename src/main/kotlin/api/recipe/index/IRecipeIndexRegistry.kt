package github.kasuminova.prototypemachinery.api.recipe.index

import github.kasuminova.prototypemachinery.api.machine.MachineType

/**
 * # Recipe Index Registry
 * # 配方索引注册表
 *
 * API for managing recipe indices.
 * Allows registering custom [RequirementIndexFactory] to support indexing for custom requirement types.
 *
 * 用于管理配方索引的 API。
 * 允许注册自定义的 [RequirementIndexFactory] 以支持自定义需求类型的索引。
 */
public interface IRecipeIndexRegistry {

    /**
     * Registers a new factory for creating requirement indices.
     * This should be called during the PreInit or Init phase.
     *
     * 注册一个新的用于创建需求索引的工厂。
     * 应在 PreInit 或 Init 阶段调用此方法。
     *
     * @param factory The factory to register.
     */
    public fun registerFactory(factory: RequirementIndexFactory)

    /**
     * Retrieves the compiled recipe index for a specific machine type.
     *
     * 获取特定机器类型的已编译配方索引。
     *
     * @param machineType The machine type to look up.
     * @return The index, or null if no index was built for this machine type.
     */
    public fun getIndex(machineType: MachineType): RecipeIndex?

    public companion object {
        /**
         * The singleton instance of the recipe index registry.
         * This will be populated by the implementation.
         *
         * 配方索引注册表的单例实例。
         * 将由实现类填充。
         */
        public lateinit var INSTANCE: IRecipeIndexRegistry
    }

}
