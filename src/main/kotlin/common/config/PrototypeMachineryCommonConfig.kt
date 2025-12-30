package github.kasuminova.prototypemachinery.common.config

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.tuning.OrientationToolTuning
import github.kasuminova.prototypemachinery.api.tuning.RenderTuning
import github.kasuminova.prototypemachinery.api.util.probability.ProbabilityTuning
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
    private const val CATEGORY_RENDER_MERGE = "render_merge"
    private const val CATEGORY_RENDER_VBO_CACHE = "render_vbo_cache"
    private const val CATEGORY_RENDER_OPAQUE_CHUNK_VBO_CACHE = "render_opaque_chunk_vbo_cache"
    private const val CATEGORY_RENDER_GECKO = "render_gecko"

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

            RenderTuning.animSmooth = cfg.getBoolean(
                /* name = */ "animSmooth",
                /* category = */ CATEGORY_RENDER_ANIM,
                /* defaultValue = */ RenderTuning.animSmooth,
                /* comment = */ "If true, drive Gecko animations with a render-frame-synced time key for smoother motion. " +
                    "This can increase background rebuild frequency."
            )

            RenderTuning.animStepTicks = cfg.getFloat(
                /* name = */ "animStepTicks",
                /* category = */ CATEGORY_RENDER_ANIM,
                /* defaultValue = */ RenderTuning.animStepTicks.toFloat(),
                /* minValue = */ 0.01f,
                /* maxValue = */ 20.0f,
                /* comment = */ "Quantization step in ticks for animation time keys. " +
                    "1.0 => 20Hz (tick-rate), 0.5 => 40Hz, 0.25 => 80Hz. Smaller is smoother but can cost more CPU."
            ).toDouble()

            RenderTuning.animAutoThrottle = cfg.getBoolean(
                /* name = */ "animAutoThrottle",
                /* category = */ CATEGORY_RENDER_ANIM,
                /* defaultValue = */ RenderTuning.animAutoThrottle,
                /* comment = */ "If true, automatically suppress smooth animation keys when build queue/stress is high (prevents runaway rebuild churn)."
            )

            RenderTuning.animMaxQueued = cfg.getInt(
                /* name = */ "animMaxQueued",
                /* category = */ CATEGORY_RENDER_ANIM,
                /* defaultValue = */ RenderTuning.animMaxQueued.toInt(),
                /* minValue = */ 0,
                /* maxValue = */ Int.MAX_VALUE,
                /* comment = */ "If RenderTaskExecutor queued tasks exceed this, smooth animation is suppressed for that frame. 0 = disable when any backlog exists."
            ).toLong()

            RenderTuning.animMaxStressMultiplier = cfg.getInt(
                /* name = */ "animMaxStressMultiplier",
                /* category = */ CATEGORY_RENDER_ANIM,
                /* defaultValue = */ RenderTuning.animMaxStressMultiplier,
                /* minValue = */ 1,
                /* maxValue = */ 4096,
                /* comment = */ "If RenderStress.drawMultiplier exceeds this, smooth animation is suppressed (keeps stress tests meaningful)."
            )

            RenderTuning.mergeMinBuffers = cfg.getInt(
                /* name = */ "mergeMinBuffers",
                /* category = */ CATEGORY_RENDER_MERGE,
                /* defaultValue = */ RenderTuning.mergeMinBuffers,
                /* minValue = */ 1,
                /* maxValue = */ 1024,
                /* comment = */ "Minimum buffers in a merge bucket before memcpy+merge is attempted. " +
                    "Increase to reduce merge overhead; decrease to reduce draw calls."
            )

            RenderTuning.mergeMinBytes = cfg.getInt(
                /* name = */ "mergeMinBytes",
                /* category = */ CATEGORY_RENDER_MERGE,
                /* defaultValue = */ RenderTuning.mergeMinBytes,
                /* minValue = */ 0,
                /* maxValue = */ 256 * 1024 * 1024,
                /* comment = */ "Minimum total bytes in a merge bucket before memcpy+merge is attempted."
            )

            RenderTuning.mergeForceClientArrays = cfg.getBoolean(
                /* name = */ "mergeForceClientArrays",
                /* category = */ CATEGORY_RENDER_MERGE,
                /* defaultValue = */ RenderTuning.mergeForceClientArrays,
                /* comment = */ "If true, force client-side arrays for merged draws even when VBO is available. Useful for debugging driver stalls."
            )

            RenderTuning.vboCacheEnabled = cfg.getBoolean(
                /* name = */ "vboCacheEnabled",
                /* category = */ CATEGORY_RENDER_VBO_CACHE,
                /* defaultValue = */ RenderTuning.vboCacheEnabled,
                /* comment = */ "If true, cache a GPU VBO per built BufferBuilder. This can dramatically reduce per-frame uploads on cache hits, " +
                    "at the cost of extra GPU memory (roughly mirrors CPU buffer sizes)."
            )

            RenderTuning.vboCachePreferIndividualAboveBytes = cfg.getInt(
                /* name = */ "vboCachePreferIndividualAboveBytes",
                /* category = */ CATEGORY_RENDER_VBO_CACHE,
                /* defaultValue = */ RenderTuning.vboCachePreferIndividualAboveBytes,
                /* minValue = */ 0,
                /* maxValue = */ 256 * 1024 * 1024,
                /* comment = */ "When a merge bucket's total bytes are >= this value, prefer drawing buffers individually using cached VBOs instead of merge+upload. 0 = always prefer individual when VBO cache is enabled."
            )

            RenderTuning.vboCachePoolEnabled = cfg.getBoolean(
                /* name = */ "vboCachePoolEnabled",
                /* category = */ CATEGORY_RENDER_VBO_CACHE,
                /* defaultValue = */ RenderTuning.vboCachePoolEnabled,
                /* comment = */ "If true, reuse GL buffer objects (VBO names) when BufferBuilders are recycled. " +
                    "This reduces main-thread glGenBuffers/VertexBuffer.<init> overhead at the cost of keeping a small pool of idle VBOs."
            )

            RenderTuning.vboCachePoolMaxEntries = cfg.getInt(
                /* name = */ "vboCachePoolMaxEntries",
                /* category = */ CATEGORY_RENDER_VBO_CACHE,
                /* defaultValue = */ RenderTuning.vboCachePoolMaxEntries,
                /* minValue = */ 0,
                /* maxValue = */ 16384,
                /* comment = */ "Max number of idle pooled VBO ids kept for reuse. 0 disables the count limit."
            )

            RenderTuning.vboCachePoolMaxBytes = cfg.getInt(
                /* name = */ "vboCachePoolMaxBytes",
                /* category = */ CATEGORY_RENDER_VBO_CACHE,
                /* defaultValue = */ RenderTuning.vboCachePoolMaxBytes.toInt().coerceAtLeast(0),
                /* minValue = */ 0,
                /* maxValue = */ Int.MAX_VALUE,
                /* comment = */ "Max bytes estimate retained by idle pooled VBO ids. 0 disables the bytes limit. " +
                    "Note: this is a heuristic based on last uploaded sizes; actual driver memory usage can differ."
            ).toLong()

            RenderTuning.opaqueChunkVboCacheEnabled = cfg.getBoolean(
                /* name = */ "opaqueChunkVboCacheEnabled",
                /* category = */ CATEGORY_RENDER_OPAQUE_CHUNK_VBO_CACHE,
                /* defaultValue = */ RenderTuning.opaqueChunkVboCacheEnabled,
                /* comment = */ "Experimental: cache merged opaque (RenderPass.DEFAULT) geometry per chunk-group VBO to reduce repeated VertexBuffer.bufferData uploads. " +
                    "Only affects opaque pass; transparent/bloom are excluded."
            )

            RenderTuning.opaqueChunkVboCacheChunkSize = cfg.getInt(
                /* name = */ "opaqueChunkVboCacheChunkSize",
                /* category = */ CATEGORY_RENDER_OPAQUE_CHUNK_VBO_CACHE,
                /* defaultValue = */ RenderTuning.opaqueChunkVboCacheChunkSize,
                /* minValue = */ 1,
                /* maxValue = */ 4,
                /* comment = */ "Chunk-group size in chunks: 1 => 1x1, 2 => 2x2, 4 => 4x4. Values outside {1,2,4} are sanitized to 1."
            )

            RenderTuning.opaqueChunkVboCacheMaxEntries = cfg.getInt(
                /* name = */ "opaqueChunkVboCacheMaxEntries",
                /* category = */ CATEGORY_RENDER_OPAQUE_CHUNK_VBO_CACHE,
                /* defaultValue = */ RenderTuning.opaqueChunkVboCacheMaxEntries,
                /* minValue = */ 0,
                /* maxValue = */ 1_000_000,
                /* comment = */ "Max cached chunk entries. 0 disables the entry-count limit."
            )

            RenderTuning.opaqueChunkVboCacheMaxBytes = cfg.getInt(
                /* name = */ "opaqueChunkVboCacheMaxBytes",
                /* category = */ CATEGORY_RENDER_OPAQUE_CHUNK_VBO_CACHE,
                /* defaultValue = */ RenderTuning.opaqueChunkVboCacheMaxBytes.toInt().coerceAtLeast(0),
                /* minValue = */ 0,
                /* maxValue = */ Int.MAX_VALUE,
                /* comment = */ "Max bytes held by chunk cache (rough estimate). 0 disables this limit. Note: Forge config int cap applies."
            ).toLong()

            RenderTuning.opaqueChunkVboCacheMinBuffers = cfg.getInt(
                /* name = */ "opaqueChunkVboCacheMinBuffers",
                /* category = */ CATEGORY_RENDER_OPAQUE_CHUNK_VBO_CACHE,
                /* defaultValue = */ RenderTuning.opaqueChunkVboCacheMinBuffers,
                /* minValue = */ 1,
                /* maxValue = */ 1024,
                /* comment = */ "Minimum buffers in a bucket before chunk cache is used."
            )

            RenderTuning.opaqueChunkVboCacheMinBytes = cfg.getInt(
                /* name = */ "opaqueChunkVboCacheMinBytes",
                /* category = */ CATEGORY_RENDER_OPAQUE_CHUNK_VBO_CACHE,
                /* defaultValue = */ RenderTuning.opaqueChunkVboCacheMinBytes,
                /* minValue = */ 0,
                /* maxValue = */ 256 * 1024 * 1024,
                /* comment = */ "Minimum total bytes in a bucket before chunk cache is used."
            )

            RenderTuning.geckoBulkQuadWrite = cfg.getBoolean(
                /* name = */ "geckoBulkQuadWrite",
                /* category = */ CATEGORY_RENDER_GECKO,
                /* defaultValue = */ RenderTuning.geckoBulkQuadWrite,
                /* comment = */ "If true, GeckoModelBaker will pack POSITION_TEX_COLOR_NORMAL quads and submit them via BufferBuilder.addVertexData(int[]) to reduce per-vertex call overhead."
            )

            RenderTuning.geckoModernVertexPipeline = cfg.getBoolean(
                /* name = */ "geckoModernVertexPipeline",
                /* category = */ CATEGORY_RENDER_GECKO,
                /* defaultValue = */ RenderTuning.geckoModernVertexPipeline,
                /* comment = */ "If true, GeckoModelBaker will try to use the Java21+ modern-backend vertex pipeline (via PMPlatform) to batch-transform + pack cube vertices. Has no effect if modern-backend is not installed."
            )

            RenderTuning.geckoModernVertexPipelineForceScalar = cfg.getBoolean(
                /* name = */ "geckoModernVertexPipelineForceScalar",
                /* category = */ CATEGORY_RENDER_GECKO,
                /* defaultValue = */ RenderTuning.geckoModernVertexPipelineForceScalar,
                /* comment = */ "If true, force scalar backend for the modern vertex pipeline (A/B profiling)."
            )

            RenderTuning.geckoBoneBatching = cfg.getBoolean(
                /* name = */ "geckoBoneBatching",
                /* category = */ CATEGORY_RENDER_GECKO,
                /* defaultValue = */ RenderTuning.geckoBoneBatching,
                /* comment = */ "Experimental: batch multiple contiguous cubes under the same bone when cube-local rotation is identity. Can increase effective batch size for the modern pipeline (esp. vector backend)."
            )

            RenderTuning.geckoBoneBatchingMinVertices = cfg.getInt(
                /* name = */ "geckoBoneBatchingMinVertices",
                /* category = */ CATEGORY_RENDER_GECKO,
                /* defaultValue = */ RenderTuning.geckoBoneBatchingMinVertices,
                /* minValue = */ 0,
                /* maxValue = */ 1_000_000,
                /* comment = */ "Minimum vertices for a contiguous identity-cube run before attempting a bone-level batch. 0 = always attempt."
            )

            RenderTuning.geckoCubePreBake = cfg.getBoolean(
                /* name = */ "geckoCubePreBake",
                /* category = */ CATEGORY_RENDER_GECKO,
                /* defaultValue = */ RenderTuning.geckoCubePreBake,
                /* comment = */ "Pre-bake cube-local transforms (pivot/rotation) into cached vertex data. " +
                    "Enables effective bone-level batching for ALL cubes, not just identity-rotation ones. " +
                    "Memory cost: ~24 bytes per vertex."
            )

            RenderTuning.sanitize()
        } catch (t: Throwable) {
            // Don't fail startup on config issues.
            PrototypeMachinery.logger.warn("Failed to load config, using defaults.", t)
        } finally {
            if (cfg.hasChanged()) {
                cfg.save()
            }
        }
    }
}
