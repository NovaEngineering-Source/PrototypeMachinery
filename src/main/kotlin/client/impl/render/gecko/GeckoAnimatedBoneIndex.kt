package github.kasuminova.prototypemachinery.client.impl.render.gecko

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.client.resources.IResourceManager
import net.minecraft.util.ResourceLocation
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Extracts animated bone names from a GeckoLib animation json.
 *
 * We intentionally parse json directly instead of relying on GeckoLib internal builder classes,
 * because this code runs in our off-thread bake pipeline and we only need bone name keys.
 */
internal object GeckoAnimatedBoneIndex {

    private data class CacheKey(
        val resourcesRoot: String,
        val animationLocation: ResourceLocation,
    )

    internal data class AnimationBoneIndex(
        /** animationName -> animated bone names */
        val bonesByAnimation: Map<String, Set<String>>,
    ) {
        /** Union of all bones used by any animation in the file. */
        val allAnimatedBones: Set<String> by lazy {
            val out = LinkedHashSet<String>()
            for (set in bonesByAnimation.values) out.addAll(set)
            Collections.unmodifiableSet(out)
        }

        /** All available animation names, sorted for deterministic fallback. */
        val animationNamesSorted: List<String> by lazy {
            bonesByAnimation.keys.sorted()
        }

        /**
         * Compute animated bones relevant to the *currently selected* animations.
         *
         * If [preferredAnimationNames] contains valid names, we union those.
         * Otherwise, we deterministically fall back to the first animation name in sorted order
         * (mirrors the deterministic fallback we use in GeckoAnimationDriver).
         */
        fun animatedBonesFor(preferredAnimationNames: List<String>): Set<String> {
            if (bonesByAnimation.isEmpty()) return emptySet()

            val selected = preferredAnimationNames
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .filter { bonesByAnimation.containsKey(it) }

            val effective = if (selected.isNotEmpty()) {
                selected
            } else {
                listOf(animationNamesSorted.first())
            }

            val out = LinkedHashSet<String>()
            for (name in effective) {
                bonesByAnimation[name]?.let(out::addAll)
            }
            return Collections.unmodifiableSet(out)
        }
    }

    private val cache: MutableMap<CacheKey, AnimationBoneIndex> = ConcurrentHashMap()

    internal fun cacheSize(): Int = cache.size

    internal fun getIndex(
        resourceManager: IResourceManager,
        resourcesRoot: String,
        animationLocation: ResourceLocation,
    ): AnimationBoneIndex {
        val key = CacheKey(resourcesRoot, animationLocation)
        cache[key]?.let { return it }

        val parsed = runCatching {
            resourceManager.getResource(animationLocation).use { res ->
                InputStreamReader(res.inputStream, StandardCharsets.UTF_8).use { reader ->
                    val root = JsonParser().parse(reader)
                    if (!root.isJsonObject) return@use AnimationBoneIndex(emptyMap())

                    val obj = root.asJsonObject
                    val animations = obj.getAsJsonObject("animations") ?: return@use AnimationBoneIndex(emptyMap())

                    val byAnim = LinkedHashMap<String, Set<String>>()
                    for ((animName, animEl) in animations.entrySet()) {
                        val animObj = animEl.asJsonObjectOrNull() ?: continue
                        val bones = animObj.getAsJsonObject("bones") ?: continue
                        if (bones.entrySet().isEmpty()) continue

                        val boneNames = LinkedHashSet<String>()
                        for ((boneName, _) in bones.entrySet()) {
                            boneNames.add(boneName)
                        }
                        byAnim[animName] = Collections.unmodifiableSet(boneNames)
                    }
                    AnimationBoneIndex(Collections.unmodifiableMap(byAnim))
                }
            }
        }.getOrElse { AnimationBoneIndex(emptyMap()) }

        cache[key] = parsed
        return parsed
    }

    /** Back-compat helper: union of all animated bones. */
    internal fun getAllAnimatedBones(
        resourceManager: IResourceManager,
        resourcesRoot: String,
        animationLocation: ResourceLocation,
    ): Set<String> {
        return getIndex(resourceManager, resourcesRoot, animationLocation).allAnimatedBones
    }

    internal fun invalidateAll() {
        cache.clear()
    }

    private fun com.google.gson.JsonElement.asJsonObjectOrNull(): JsonObject? {
        return if (this.isJsonObject) this.asJsonObject else null
    }
}
