package github.kasuminova.prototypemachinery.api.machine.component.ui

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType
import github.kasuminova.prototypemachinery.api.machine.component.system.MachineSystem
import net.minecraft.util.ResourceLocation

/**
 * # UIProviderComponentType - UI Provider Component Type
 * # UIProviderComponentType - UI 提供者组件类型
 *
 * Component type for UI providers. Each machine that needs a custom UI should
 * have exactly one UIProviderComponent.
 *
 * UI 提供者的组件类型。每个需要自定义 UI 的机器应该只有一个 UIProviderComponent。
 *
 * ## Factory Pattern / 工厂模式
 *
 * Use the companion object's factory methods to create component types:
 * 使用伴生对象的工厂方法创建组件类型：
 *
 * ```kotlin
 * // Create from a builder function
 * val uiType = UIProviderComponentType.create("mymod", "my_ui") { machine ->
 *     MyUIProvider(machine)
 * }
 * ```
 *
 * @see UIProviderComponent
 */
public interface UIProviderComponentType : MachineComponentType<UIProviderComponent> {

    public companion object {

        /**
         * Create a UIProviderComponentType with a custom factory function.
         * 使用自定义工厂函数创建 UIProviderComponentType。
         *
         * @param namespace The mod namespace
         * @param path The component path/name
         * @param factory Function to create the UIProviderComponent
         */
        public fun create(
            namespace: String,
            path: String,
            factory: (MachineInstance) -> UIProviderComponent
        ): UIProviderComponentType {
            return object : UIProviderComponentType {
                override val id = ResourceLocation(namespace, path)
                override fun createComponent(machine: MachineInstance) = factory(machine)
            }
        }

        /**
         * Create a UIProviderComponentType with a ResourceLocation.
         * 使用 ResourceLocation 创建 UIProviderComponentType。
         */
        public fun create(
            id: ResourceLocation,
            factory: (MachineInstance) -> UIProviderComponent
        ): UIProviderComponentType {
            return object : UIProviderComponentType {
                override val id = id
                override fun createComponent(machine: MachineInstance) = factory(machine)
            }
        }
    }

    /**
     * UI components don't need a processing system - they are passive.
     * UI 组件不需要处理系统 - 它们是被动的。
     */
    override val system: MachineSystem<UIProviderComponent>
        get() = UIProviderSystem

}

/**
 * No-op system for UI providers. UI components are passive and don't need ticking.
 * UI 提供者的空操作系统。UI 组件是被动的，不需要 tick。
 */
public object UIProviderSystem : MachineSystem<UIProviderComponent> {
    override fun onPreTick(machine: MachineInstance, component: UIProviderComponent) {
        // UI providers don't need pre-tick
    }

    override fun onTick(machine: MachineInstance, component: UIProviderComponent) {
        // UI providers don't need ticking
    }

    override fun onPostTick(machine: MachineInstance, component: UIProviderComponent) {
        // UI providers don't need post-tick
    }
}
