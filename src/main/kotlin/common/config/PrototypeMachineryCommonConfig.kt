package github.kasuminova.prototypemachinery.common.config

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.tuning.OrientationToolTuning
import github.kasuminova.prototypemachinery.api.tuning.RenderTuning
import github.kasuminova.prototypemachinery.api.util.probability.ProbabilityTuning
import net.minecraftforge.common.config.ConfigCategory
import net.minecraftforge.common.config.Configuration
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import java.io.File

/**
 * Simple Forge 1.12 config loader.
 *
 * We keep the tuning flags in API ([ProbabilityTuning]) and only update them from here,
 * so core utility code doesn't depend on Forge config classes.
 */
public object PrototypeMachineryCommonConfig {

    @Volatile
    private var liveConfigFile: File? = null

    @Volatile
    private var liveConfig: Configuration? = null

    private const val CATEGORY_PROBABILITY = "probability"
    private const val CATEGORY_TOOLS = "tools"

    private const val CATEGORY_RENDER_ANIM = "render_animation"
    private const val CATEGORY_RENDER_TASKS = "render_tasks"

    // Keep only a small set of high-impact render switches exposed to config.
    // Everything else stays as code defaults (or may be tuned via dev builds).
    private const val CATEGORY_RENDER_MERGE = "render_merge"
    private const val CATEGORY_RENDER_ASYNC_PACK = "render_async_pack"
    private const val CATEGORY_RENDER_VBO_CACHE = "render_vbo_cache"
    private const val CATEGORY_RENDER_GECKO = "render_gecko"

    private val RENDER_CATEGORIES: Array<String> = arrayOf(
        CATEGORY_RENDER_ANIM,
        CATEGORY_RENDER_TASKS,
        CATEGORY_RENDER_MERGE,
        CATEGORY_RENDER_ASYNC_PACK,
        CATEGORY_RENDER_VBO_CACHE,
        CATEGORY_RENDER_GECKO,
    )

    public fun load(event: FMLPreInitializationEvent) {
        liveConfigFile = event.suggestedConfigurationFile
        val cfg = Configuration(event.suggestedConfigurationFile)
        liveConfig = cfg
        reloadInternal(cfg, loadFromDisk = true)
    }

    internal fun getLiveConfig(): Configuration? = liveConfig

    /** Reloads config from disk and applies to runtime tuning objects. */
    internal fun reloadFromDisk(): Boolean {
        val file = liveConfigFile ?: return false
        val cfg = Configuration(file)
        liveConfig = cfg
        reloadInternal(cfg, loadFromDisk = true)
        return true
    }

    /** Applies the currently loaded in-memory config (after /pm_config set) and saves if changed. */
    internal fun applyInMemory(): Boolean {
        val cfg = liveConfig ?: return false
        reloadInternal(cfg, loadFromDisk = false)
        return true
    }

    private fun reloadInternal(cfg: Configuration, loadFromDisk: Boolean) {
        try {
            if (loadFromDisk) {
                cfg.load()
            }

            ProbabilityTuning.enableExactSmallBinomial = cfg.getBoolean(
                /* name = */ "enableExactSmallBinomial",
                /* category = */ CATEGORY_PROBABILITY,
                /* defaultValue = */ false,
                /* comment = */ "If true, BinomialSampler uses exact Bernoulli trials when n <= ${ProbabilityTuning.exactSmallBinomialThreshold}. " +
                    "This improves distribution quality for small parallelism but costs O(n) RNG calls. " +
                    "Default false (scheme B: always use table-based approximation in hot paths).",
                /* languageKey = */ "${PrototypeMachinery.MOD_ID}.config.$CATEGORY_PROBABILITY.enableExactSmallBinomial"
            )

            // Controller orientation tool (wrench)
            OrientationToolTuning.maxDurability = cfg.getInt(
                /* name = */ "orientationToolMaxDurability",
                /* category = */ CATEGORY_TOOLS,
                /* defaultValue = */ OrientationToolTuning.maxDurability,
                /* minValue = */ 1,
                /* maxValue = */ 100000,
                /* comment = */ "Max durability of the controller orientation tool (wrench)."
            )

            OrientationToolTuning.enchantability = cfg.getInt(
                /* name = */ "orientationToolEnchantability",
                /* category = */ CATEGORY_TOOLS,
                /* defaultValue = */ OrientationToolTuning.enchantability,
                /* minValue = */ 0,
                /* maxValue = */ 100,
                /* comment = */ "Enchantability of the controller orientation tool (wrench)."
            )

            OrientationToolTuning.attackDamageEnabled = cfg.getFloat(
                /* name = */ "orientationToolAttackDamageEnabled",
                /* category = */ CATEGORY_TOOLS,
                /* defaultValue = */ OrientationToolTuning.attackDamageEnabled.toFloat(),
                /* minValue = */ 0.0f,
                /* maxValue = */ 2048.0f,
                /* comment = */ "Attack damage when wrench is enabled (controller rotation mode)."
            ).toDouble()

            OrientationToolTuning.attackDamageDisabled = cfg.getFloat(
                /* name = */ "orientationToolAttackDamageDisabled",
                /* category = */ CATEGORY_TOOLS,
                /* defaultValue = */ OrientationToolTuning.attackDamageDisabled.toFloat(),
                /* minValue = */ 0.0f,
                /* maxValue = */ 2048.0f,
                /* comment = */ "Attack damage when wrench is disabled (iron multi-tool mode)."
            ).toDouble()

            OrientationToolTuning.attackSpeed = cfg.getFloat(
                /* name = */ "orientationToolAttackSpeed",
                /* category = */ CATEGORY_TOOLS,
                /* defaultValue = */ OrientationToolTuning.attackSpeed.toFloat(),
                /* minValue = */ -8.0f,
                /* maxValue = */ 8.0f,
                /* comment = */ "Attack speed modifier (vanilla style) for the wrench."
            ).toDouble()

            OrientationToolTuning.durabilityCostRotateEnabled = cfg.getInt(
                /* name = */ "orientationToolDurabilityCostRotateEnabled",
                /* category = */ CATEGORY_TOOLS,
                /* defaultValue = */ OrientationToolTuning.durabilityCostRotateEnabled,
                /* minValue = */ 0,
                /* maxValue = */ 1000,
                /* comment = */ "Durability cost per successful controller rotation when enabled."
            )

            OrientationToolTuning.durabilityCostAttackEnabled = cfg.getInt(
                /* name = */ "orientationToolDurabilityCostAttackEnabled",
                /* category = */ CATEGORY_TOOLS,
                /* defaultValue = */ OrientationToolTuning.durabilityCostAttackEnabled,
                /* minValue = */ 0,
                /* maxValue = */ 1000,
                /* comment = */ "Durability cost per attack hit when enabled."
            )

            OrientationToolTuning.durabilityCostAttackDisabled = cfg.getInt(
                /* name = */ "orientationToolDurabilityCostAttackDisabled",
                /* category = */ CATEGORY_TOOLS,
                /* defaultValue = */ OrientationToolTuning.durabilityCostAttackDisabled,
                /* minValue = */ 0,
                /* maxValue = */ 1000,
                /* comment = */ "Durability cost per attack hit when disabled."
            )

            OrientationToolTuning.durabilityCostBreakEnabled = cfg.getInt(
                /* name = */ "orientationToolDurabilityCostBreakEnabled",
                /* category = */ CATEGORY_TOOLS,
                /* defaultValue = */ OrientationToolTuning.durabilityCostBreakEnabled,
                /* minValue = */ 0,
                /* maxValue = */ 1000,
                /* comment = */ "Durability cost per block broken when enabled."
            )

            OrientationToolTuning.durabilityCostBreakDisabled = cfg.getInt(
                /* name = */ "orientationToolDurabilityCostBreakDisabled",
                /* category = */ CATEGORY_TOOLS,
                /* defaultValue = */ OrientationToolTuning.durabilityCostBreakDisabled,
                /* minValue = */ 0,
                /* maxValue = */ 1000,
                /* comment = */ "Durability cost per block broken when disabled."
            )

            // ---------------------
            // Client render tuning
            // ---------------------
            // These are safe to load on the server too (they just won't be used).

            // We intentionally keep config surface minimal: only "big lever" toggles.
            // Snapshot current values so we can prune the config and re-write only the kept keys.
            val keepAnimSmooth = cfg.getBoolean(
                /* name = */ "animSmooth",
                /* category = */ CATEGORY_RENDER_ANIM,
                /* defaultValue = */ RenderTuning.animSmooth,
                /* comment = */ "If true, drive Gecko animations with a render-frame-synced time key for smoother motion. " +
                    "This can increase background rebuild frequency."
            )

            val keepAnimStepTicks = cfg.getFloat(
                /* name = */ "animStepTicks",
                /* category = */ CATEGORY_RENDER_ANIM,
                /* defaultValue = */ RenderTuning.animStepTicks.toFloat(),
                /* minValue = */ 0.01f,
                /* maxValue = */ 20.0f,
                /* comment = */ "Quantization step in ticks for animation time keys. " +
                    "1.0 => 20Hz (tick-rate), 0.5 => 40Hz, 0.25 => 80Hz. Smaller is smoother but can cost more CPU."
            ).toDouble()

            val keepAnimAutoThrottle = cfg.getBoolean(
                /* name = */ "animAutoThrottle",
                /* category = */ CATEGORY_RENDER_ANIM,
                /* defaultValue = */ RenderTuning.animAutoThrottle,
                /* comment = */ "If true, automatically suppress smooth animation keys when build queue/stress is high (prevents runaway rebuild churn)."
            )

            val keepAnimMaxQueued = cfg.getInt(
                /* name = */ "animMaxQueued",
                /* category = */ CATEGORY_RENDER_ANIM,
                /* defaultValue = */ RenderTuning.animMaxQueued.toInt(),
                /* minValue = */ 0,
                /* maxValue = */ Int.MAX_VALUE,
                /* comment = */ "If render-build backlog exceeds this, smooth animation keys are throttled for that frame. 0 = disable queue-based throttling."
            ).toLong()

            val keepAnimMaxStressMultiplier = cfg.getInt(
                /* name = */ "animMaxStressMultiplier",
                /* category = */ CATEGORY_RENDER_ANIM,
                /* defaultValue = */ RenderTuning.animMaxStressMultiplier,
                /* minValue = */ 1,
                /* maxValue = */ 4096,
                /* comment = */ "If RenderStress.drawMultiplier exceeds this, smooth animation is suppressed (keeps stress tests meaningful)."
            )

            val keepRenderBuildUseCoroutines = cfg.getBoolean(
                /* name = */ "renderBuildUseCoroutines",
                /* category = */ CATEGORY_RENDER_TASKS,
                /* defaultValue = */ RenderTuning.renderBuildUseCoroutines,
                /* comment = */ "Experimental: run render-build tasks using Kotlin coroutines on a dedicated fixed thread pool instead of ForkJoinPool. " +
                    "This can be used as a fallback when certain executor implementations (e.g., virtual threads) exhibit severe scheduling issues."
            )

            val keepMergeParallelCopyEnabled = cfg.getBoolean(
                /* name = */ "mergeParallelCopyEnabled",
                /* category = */ CATEGORY_RENDER_MERGE,
                /* defaultValue = */ RenderTuning.mergeParallelCopyEnabled,
                /* comment = */ "If true, memcpy during merge may use the render-build ForkJoinPool to copy non-overlapping slices in parallel."
            )

            val keepMergeDirectVboSliceUploadEnabled = cfg.getBoolean(
                /* name = */ "mergeDirectVboSliceUploadEnabled",
                /* category = */ CATEGORY_RENDER_MERGE,
                /* defaultValue = */ RenderTuning.mergeDirectVboSliceUploadEnabled,
                /* comment = */ "If true, merged draws may upload each source builder directly into scratch VBO slices via glBufferSubData(offset,data), avoiding an intermediate CPU merge buffer."
            )

            // Async uncached bucket packing: keep only the master enable toggle.
            val keepAsyncUncachedBucketPackEnabled = cfg.getBoolean(
                /* name = */ "asyncUncachedBucketPackEnabled",
                /* category = */ CATEGORY_RENDER_ASYNC_PACK,
                /* defaultValue = */ RenderTuning.asyncUncachedBucketPackEnabled,
                /* comment = */ "If true, uncached (dynamic) opaque buckets may be packed on a background thread and rendered from the last completed packed buffer for a few frames (or skipped until ready)."
            )

            // VBO cache: keep only the master enable toggle.
            val keepVboCacheEnabled = cfg.getBoolean(
                /* name = */ "vboCacheEnabled",
                /* category = */ CATEGORY_RENDER_VBO_CACHE,
                /* defaultValue = */ RenderTuning.vboCacheEnabled,
                /* comment = */ "If true, cache a GPU VBO per built BufferBuilder. This can dramatically reduce per-frame uploads on cache hits, " +
                    "at the cost of extra GPU memory (roughly mirrors CPU buffer sizes)."
            )

            // Gecko: keep only the high-impact experimental switch.
            val keepGeckoDirectMappedVboEnabled = cfg.getBoolean(
                /* name = */ "geckoDirectMappedVboEnabled",
                /* category = */ CATEGORY_RENDER_GECKO,
                /* defaultValue = */ RenderTuning.geckoDirectMappedVboEnabled,
                /* comment = */ "EXPERIMENTAL: For Gecko ANIMATED_ONLY builds, try to write packed vertices directly into a mapped VBO (A/B per owner+pass). " +
                    "This aims to reduce CPU memcpy by removing the intermediate packed direct buffer + upload step."
            )

            // Apply kept values.
            RenderTuning.animSmooth = keepAnimSmooth
            RenderTuning.animStepTicks = keepAnimStepTicks
            RenderTuning.animAutoThrottle = keepAnimAutoThrottle
            RenderTuning.animMaxQueued = keepAnimMaxQueued
            RenderTuning.animMaxStressMultiplier = keepAnimMaxStressMultiplier
            RenderTuning.renderBuildUseCoroutines = keepRenderBuildUseCoroutines
            RenderTuning.mergeParallelCopyEnabled = keepMergeParallelCopyEnabled
            RenderTuning.mergeDirectVboSliceUploadEnabled = keepMergeDirectVboSliceUploadEnabled
            RenderTuning.asyncUncachedBucketPackEnabled = keepAsyncUncachedBucketPackEnabled
            RenderTuning.vboCacheEnabled = keepVboCacheEnabled
            RenderTuning.geckoDirectMappedVboEnabled = keepGeckoDirectMappedVboEnabled

            RenderTuning.sanitize()

            // Prune config file: drop most render tuning keys and re-create only the kept ones.
            pruneRenderConfig(cfg)

            // Re-register kept keys so the saved cfg is concise and contains current values.
            cfg.getBoolean(
                /* name = */ "animSmooth",
                /* category = */ CATEGORY_RENDER_ANIM,
                /* defaultValue = */ keepAnimSmooth,
                /* comment = */ "If true, drive Gecko animations with a render-frame-synced time key for smoother motion. " +
                    "This can increase background rebuild frequency."
            )

            cfg.getFloat(
                /* name = */ "animStepTicks",
                /* category = */ CATEGORY_RENDER_ANIM,
                /* defaultValue = */ keepAnimStepTicks.toFloat(),
                /* minValue = */ 0.01f,
                /* maxValue = */ 20.0f,
                /* comment = */ "Quantization step in ticks for animation time keys. " +
                    "1.0 => 20Hz (tick-rate), 0.5 => 40Hz, 0.25 => 80Hz. Smaller is smoother but can cost more CPU."
            )

            cfg.getBoolean(
                /* name = */ "animAutoThrottle",
                /* category = */ CATEGORY_RENDER_ANIM,
                /* defaultValue = */ keepAnimAutoThrottle,
                /* comment = */ "If true, automatically suppress smooth animation keys when build queue/stress is high (prevents runaway rebuild churn)."
            )

            cfg.getInt(
                /* name = */ "animMaxQueued",
                /* category = */ CATEGORY_RENDER_ANIM,
                /* defaultValue = */ keepAnimMaxQueued.toInt().coerceAtLeast(0),
                /* minValue = */ 0,
                /* maxValue = */ Int.MAX_VALUE,
                /* comment = */ "If render-build backlog exceeds this, smooth animation keys are throttled for that frame. 0 = disable queue-based throttling."
            )

            cfg.getInt(
                /* name = */ "animMaxStressMultiplier",
                /* category = */ CATEGORY_RENDER_ANIM,
                /* defaultValue = */ keepAnimMaxStressMultiplier,
                /* minValue = */ 1,
                /* maxValue = */ 4096,
                /* comment = */ "If RenderStress.drawMultiplier exceeds this, smooth animation is suppressed (keeps stress tests meaningful)."
            )

            cfg.getBoolean(
                /* name = */ "renderBuildUseCoroutines",
                /* category = */ CATEGORY_RENDER_TASKS,
                /* defaultValue = */ keepRenderBuildUseCoroutines,
                /* comment = */ "Experimental: run render-build tasks using Kotlin coroutines on a dedicated fixed thread pool instead of ForkJoinPool. " +
                    "This can be used as a fallback when certain executor implementations (e.g., virtual threads) exhibit severe scheduling issues."
            )

            cfg.getBoolean(
                /* name = */ "mergeParallelCopyEnabled",
                /* category = */ CATEGORY_RENDER_MERGE,
                /* defaultValue = */ keepMergeParallelCopyEnabled,
                /* comment = */ "If true, memcpy during merge may use the render-build pool to copy non-overlapping slices in parallel."
            )

            cfg.getBoolean(
                /* name = */ "mergeDirectVboSliceUploadEnabled",
                /* category = */ CATEGORY_RENDER_MERGE,
                /* defaultValue = */ keepMergeDirectVboSliceUploadEnabled,
                /* comment = */ "If true, merged draws may upload each source builder directly into scratch VBO slices (avoids intermediate CPU merge buffer)."
            )

            cfg.getBoolean(
                /* name = */ "asyncUncachedBucketPackEnabled",
                /* category = */ CATEGORY_RENDER_ASYNC_PACK,
                /* defaultValue = */ keepAsyncUncachedBucketPackEnabled,
                /* comment = */ "If true, uncached (dynamic) opaque buckets may be packed on a background thread and rendered from the last completed packed buffer for a few frames (or skipped until ready)."
            )

            cfg.getBoolean(
                /* name = */ "vboCacheEnabled",
                /* category = */ CATEGORY_RENDER_VBO_CACHE,
                /* defaultValue = */ keepVboCacheEnabled,
                /* comment = */ "If true, cache a GPU VBO per built BufferBuilder to reduce per-frame uploads on cache hits (uses extra GPU memory)."
            )

            cfg.getBoolean(
                /* name = */ "geckoDirectMappedVboEnabled",
                /* category = */ CATEGORY_RENDER_GECKO,
                /* defaultValue = */ keepGeckoDirectMappedVboEnabled,
                /* comment = */ "EXPERIMENTAL: For Gecko ANIMATED_ONLY builds, try to write packed vertices directly into a mapped VBO to reduce CPU memcpy."
            )
        } catch (t: Throwable) {
            // Don't fail startup on config issues.
            PrototypeMachinery.logger.warn("Failed to load config, using defaults.", t)
        } finally {
            if (cfg.hasChanged()) {
                cfg.save()
            }
        }
    }

    private fun pruneRenderConfig(cfg: Configuration) {
        // Forge 1.12 config implementations vary across forks; best-effort removal with multiple fallbacks.
        for (c in RENDER_CATEGORIES) {
            // Prefer API-style removal if present.
            val cat: ConfigCategory? = try {
                cfg.getCategory(c)
            } catch (_: Throwable) {
                null
            }

            if (cat != null) {
                try {
                    val m = cfg.javaClass.getMethod("removeCategory", ConfigCategory::class.java)
                    m.invoke(cfg, cat)
                    continue
                } catch (_: Throwable) {
                    // fall through
                }
            }

            // Some forks expose removeCategory(String).
            try {
                val m = cfg.javaClass.getMethod("removeCategory", String::class.java)
                m.invoke(cfg, c)
                continue
            } catch (_: Throwable) {
                // fall through
            }

            // Last resort: remove from internal categories map.
            try {
                val f = cfg.javaClass.getDeclaredField("categories")
                f.isAccessible = true
                val map = f.get(cfg) as? MutableMap<*, *>
                map?.let {
                    @Suppress("UNCHECKED_CAST")
                    (it as MutableMap<String, Any>).remove(c)
                }
            } catch (_: Throwable) {
                // Best-effort: if removal isn't supported, we simply stop registering extra keys.
            }
        }
    }
}
