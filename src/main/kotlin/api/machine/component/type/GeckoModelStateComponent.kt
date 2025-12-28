package github.kasuminova.prototypemachinery.api.machine.component.type

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType
import github.kasuminova.prototypemachinery.api.machine.component.system.MachineSystem
import github.kasuminova.prototypemachinery.impl.machine.component.GeckoModelStateComponentImpl
import net.minecraft.util.ResourceLocation

/**
 * Component type for storing client-relevant GeckoLib model/animation state.
 *
 * 用于存储 GeckoLib 模型/动画状态（客户端渲染需要）的机器组件类型。
 *
 * Why this exists:
 * - ZSDataComponent is a generic key/value bag; great for scripts, but not ideal for render state.
 * - Render state tends to be latency-sensitive and needs a stable, typed schema.
 * - Having a dedicated component also makes it easy for server systems to update animation state
 *   without stringly-typed keys.
 */
public object GeckoModelStateComponentType : MachineComponentType<GeckoModelStateComponent> {

    override val id: ResourceLocation = ResourceLocation("prototypemachinery", "gecko_model_state")

    // No system needed by default; server-side systems/components may update it directly.
    override val system: MachineSystem<GeckoModelStateComponent>? = null

    override fun createComponent(machine: MachineInstance): GeckoModelStateComponent {
        return GeckoModelStateComponentImpl(machine, this)
    }
}

/**
 * Machine component for storing GeckoLib animation state.
 *
 * Minimal MVP schema:
 * - [animationLayers]: the layered animation names, evaluated in order.
 * - [animationName]: convenience single-layer accessor.
 * - [stateVersion]: monotonically increasing version used for cache busting.
 */
public interface GeckoModelStateComponent : MachineComponent, MachineComponent.Serializable, MachineComponent.Synchronizable {

    /**
     * Layered animation names.
     * Empty means "no override".
     */
    public val animationLayers: List<String>

    /**
     * Convenience: single animation name.
     * When set, it replaces [animationLayers] with a single entry.
     */
    public val animationName: String?

    /**
     * Cache-busting version.
     *
     * Renderer should include this in RenderKey.variant so even non-name state changes can
     * invalidate cached static buffers.
     */
    public val stateVersion: Int

    /** Set layered animations (server-authoritative). */
    public fun setAnimationLayers(layers: List<String>)

    /** Set a single animation (server-authoritative). */
    public fun setAnimation(name: String?)

    /** Clear any overrides. */
    public fun clear()
}
