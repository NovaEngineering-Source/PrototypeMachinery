package github.kasuminova.prototypemachinery.client.impl.render.bloom

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.client.impl.render.RenderManager
import java.lang.reflect.Proxy

/**
 * Optional integration with GregTech's bloom post-processing pipeline.
 *
 * MMCE renders bloom-capable geo models via BloomEffectUtil (UNREAL) instead of doing
 * a simple additive pass in the normal world render.
 *
 * PrototypeMachinery does NOT depend on GTCE at compile-time, so we integrate via reflection.
 * When GTCE is present, BLOOM/LUMENIZED passes are deferred to BloomEffectUtil callbacks.
 */
internal object GregTechBloomBridge {

    private var attempted: Boolean = false
    private var registered: Boolean = false
    private var postProcessing: Boolean = false

    val isEnabled: Boolean
        get() = registered

    fun initIfPresent() {
        if (attempted) return
        attempted = true

        try {
            val bloomEffectUtilClass = Class.forName("gregtech.client.utils.BloomEffectUtil")
            val bloomTypeClass = Class.forName("gregtech.client.shader.postprocessing.BloomType")
            val iRenderSetupClass = Class.forName("gregtech.client.renderer.IRenderSetup")
            val iBloomEffectClass = Class.forName("gregtech.client.utils.IBloomEffect")

            val registerMethod = bloomEffectUtilClass.methods
                .firstOrNull { it.name == "registerBloomRender" && it.parameterTypes.size == 4 }
                ?: return

            @Suppress("UNCHECKED_CAST")
            val unreal = java.lang.Enum.valueOf(bloomTypeClass as Class<out Enum<*>>, "UNREAL")

            // Predicate type varies by GT version; adapt at runtime.
            val predicateType = registerMethod.parameterTypes[3]
            val alwaysTruePredicate = Proxy.newProxyInstance(
                predicateType.classLoader,
                arrayOf(predicateType)
            ) { proxy, method, args ->
                when (method.name) {
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.getOrNull(0)
                    "toString" -> "PM-GTBloomGatePredicate"

                    // Common names: java.util.function.Predicate#test, guava Predicate#apply
                    "test", "apply" -> true

                    // java.util.function.Predicate default combinators (may be called)
                    "and", "or", "negate" -> proxy

                    else -> {
                        // Be permissive: if a boolean is expected, allow bloom.
                        when (method.returnType) {
                            java.lang.Boolean.TYPE, java.lang.Boolean::class.java -> true
                            predicateType -> proxy
                            else -> null
                        }
                    }
                }
            }

            val handler = java.lang.reflect.InvocationHandler { proxy, method, args ->
                when (method.name) {
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.getOrNull(0)
                    "toString" -> "PM-GTBloomEffectRenderer"

                    // IRenderSetup
                    "preDraw", "postDraw" -> Unit

                    // IBloomEffect
                    "shouldRenderBloomEffect" -> RenderManager.hasPendingBloomWork()
                    "renderBloomEffect" -> {
                        // BloomEffectUtil may invoke bloom render twice per frame.
                        // Mirror MMCE approach: don't clear on the first call.
                        RenderManager.drawBloomPasses(clearAfterDraw = postProcessing)
                        postProcessing = !postProcessing
                        Unit
                    }

                    else -> null
                }
            }

            val bloomRenderer = Proxy.newProxyInstance(
                iBloomEffectClass.classLoader,
                arrayOf(iRenderSetupClass, iBloomEffectClass),
                handler
            )

            // registerBloomRender(IRenderSetup setup, BloomType type, IBloomEffect effect, <Predicate> gate)
            registerMethod.invoke(null, bloomRenderer, unreal, bloomRenderer, alwaysTruePredicate)
            registered = true
            PrototypeMachinery.logger.info("[PM] GregTech bloom detected: enabling BloomEffectUtil integration")
        } catch (_: Throwable) {
            // Optional integration; ignore if GT is absent or signature mismatch.
        }
    }
}
