package github.kasuminova.prototypemachinery.api.recipe.process.component.type

import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.process.component.RecipeProcessComponent
import github.kasuminova.prototypemachinery.api.recipe.process.component.RecipeProcessComponentType
import github.kasuminova.prototypemachinery.api.recipe.process.component.system.RecipeProcessSystem
import github.kasuminova.prototypemachinery.impl.recipe.process.component.ZSDataProcessComponentImpl
import github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.data.ZenMachineData
import net.minecraft.util.ResourceLocation

/**
 * Component type for recipe process-level script data storage.
 * 配方进程级脚本数据存储组件类型。
 */
public object ZSDataProcessComponentType : RecipeProcessComponentType<ZSDataProcessComponent> {

    override val id: ResourceLocation = ResourceLocation("prototypemachinery", "zs_data")

    // No system needed - data is accessed directly, no tick logic
    // 不需要系统 - 数据直接访问，无 tick 逻辑
    override val system: RecipeProcessSystem<ZSDataProcessComponent>? = null

    override fun createComponent(process: RecipeProcess): ZSDataProcessComponent {
        return ZSDataProcessComponentImpl(process, this)
    }
}

/**
 * Recipe process component for storing ZenScript-accessible data.
 * 用于存储可从 ZenScript 访问的数据的配方进程组件。
 *
 * Synchronization granularity is at the component level.
 * 同步粒度在组件级别。
 */
public interface ZSDataProcessComponent : RecipeProcessComponent, RecipeProcessComponent.Synchronizable {

    /**
     * The mutable data container accessible from ZenScript.
     * 可从 ZenScript 访问的可变数据容器。
     */
    public val data: ZenMachineData

}
