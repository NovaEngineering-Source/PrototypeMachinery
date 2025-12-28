package github.kasuminova.prototypemachinery.client.impl.render.gecko

import github.kasuminova.prototypemachinery.PrototypeMachinery
import net.minecraft.client.resources.IResourceManager
import net.minecraft.util.ResourceLocation
import software.bernie.geckolib3.core.IAnimatable
import software.bernie.geckolib3.core.IAnimatableModel
import software.bernie.geckolib3.core.PlayState
import software.bernie.geckolib3.core.builder.Animation
import software.bernie.geckolib3.core.builder.AnimationBuilder
import software.bernie.geckolib3.core.controller.AnimationController
import software.bernie.geckolib3.core.event.predicate.AnimationEvent
import software.bernie.geckolib3.core.manager.AnimationData
import software.bernie.geckolib3.core.manager.AnimationFactory
import software.bernie.geckolib3.core.processor.AnimationProcessor
import software.bernie.geckolib3.file.AnimationFile
import software.bernie.geckolib3.file.AnimationFileLoader
import software.bernie.geckolib3.geo.render.built.GeoBone
import software.bernie.geckolib3.geo.render.built.GeoModel
import software.bernie.shadowed.eliotlash.molang.MolangParser
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * GeckoLib animation evaluation for our off-thread bake pipeline.
 *
 * Notes:
 * - We keep controller state (tickOffset / looping state) per ownerKey so animations progress.
 * - We clear GeckoLib's bone snapshot cache every build because GeoModel instances are reloaded
 *   (new bone objects), and GeckoLib snapshots hold IBone references.
 */
internal object GeckoAnimationDriver {

	private val initialized = AtomicBoolean(false)

	// Per-instance animation state keyed by Renderable.ownerKey (same semantics as RenderTaskCache).
	private val runtimes: MutableMap<Any, Runtime> = Collections.synchronizedMap(WeakHashMap())

	internal fun runtimeCount(): Int = synchronized(runtimes) { runtimes.size }

	internal fun clearAll() {
		synchronized(runtimes) {
			runtimes.clear()
		}
	}

	internal fun init() {
		if (!initialized.compareAndSet(false, true)) return

		// Resolve our IAnimatable -> IAnimatableModel mapping for AnimationController.setAnimation(...)
		@Suppress("UNCHECKED_CAST")
		AnimationController.addModelFetcher(
			AnimationController.ModelFetcher<Runtime> { animatable ->
				(animatable as? Runtime)?.model
			} as AnimationController.ModelFetcher<*>
		)
	}

	internal fun apply(
		ownerKey: Any,
		geoModel: GeoModel,
		animationLocation: ResourceLocation,
		defaultAnimationName: String?,
		resourceManager: IResourceManager,
		seekTimeTicks: Double,
	) {
		// Back-compat shim: old API drives a single animation.
		val names = listOfNotNull(defaultAnimationName).filter { it.isNotBlank() }
		apply(
			ownerKey = ownerKey,
			geoModel = geoModel,
			animationLocation = animationLocation,
			animationNames = names,
			resourceManager = resourceManager,
			seekTimeTicks = seekTimeTicks,
		)
	}

	internal fun apply(
		ownerKey: Any,
		geoModel: GeoModel,
		animationLocation: ResourceLocation,
		animationNames: List<String>,
		resourceManager: IResourceManager,
		seekTimeTicks: Double,
	) {
		// Fail-safe: the client proxy should call init(), but background tasks might run first in dev.
		if (!initialized.get()) {
			init()
		}

		val runtime = synchronized(runtimes) {
			val existing = runtimes[ownerKey]
			if (existing != null &&
				existing.animationLocation == animationLocation &&
				existing.animationNames == animationNames
			) {
				existing
			} else {
				Runtime(animationLocation, animationNames).also { runtimes[ownerKey] = it }
			}
		}

		runtime.applyToModel(geoModel, resourceManager, seekTimeTicks)
	}

	private class Runtime(
		internal val animationLocation: ResourceLocation,
		internal val animationNames: List<String>,
	) : IAnimatable {

		private val factory: AnimationFactory = AnimationFactory(this)
		internal val model: RuntimeModel = RuntimeModel()

		private val parser: MolangParser = MolangParser()
		private val loader: AnimationFileLoader = AnimationFileLoader()

		@Volatile
		private var selectedAnimationNames: List<String> = emptyList()

		private val uniqueId: Int = 0

		override fun registerControllers(data: AnimationData) {
			// Avoid slow "reset-to-default" lerps for bones not keyed in an animation.
			// data.setResetSpeedInTicks(0.0) // Potential division by zero source?
			data.setResetSpeedInTicks(1.0)

			// 0-tick transitions: deterministic, cache-friendly playback.
			// One controller per layer; later controllers will overwrite conflicting bone transforms.
			val count = maxOf(1, animationNames.size)
			for (i in 0 until count) {
				val controllerName = "pm_gecko_$i"
				// Use a small non-zero transition to avoid potential NaN if 0 is used as divisor
				val controller = AnimationController<Runtime>(this, controllerName, 0.001f) { event ->
					val list = selectedAnimationNames
					val name = list.getOrNull(i)
					if (name.isNullOrEmpty()) {
						return@AnimationController PlayState.STOP
					}

					event.controller.setAnimation(AnimationBuilder().addAnimation(name))
					PlayState.CONTINUE
				}
				data.addAnimationController(controller)
			}
		}

		override fun getFactory(): AnimationFactory = factory

		private fun ensureAnimationLoaded(resourceManager: IResourceManager) {
			val current = model.animationFile
			if (current != null && selectedAnimationNames.isNotEmpty()) return

			val loaded = loader.loadAllAnimations(parser, animationLocation, resourceManager)
			model.animationFile = loaded

			selectedAnimationNames = pickAnimationNames(loaded, animationNames)
		}

		private fun pickAnimationNames(file: AnimationFile, preferredList: List<String>): List<String> {
			val preferred = preferredList
				.map { it.trim() }
				.filter { it.isNotEmpty() }
				.filter { file.getAnimation(it) != null }

			if (preferred.isNotEmpty()) {
				return preferred
			}

			// Deterministic fallback: sort by animation name.
			val names = file.getAllAnimations()
				.mapNotNull { it.animationName }
				.sorted()

			return names.take(1)
		}

		internal fun applyToModel(geoModel: GeoModel, resourceManager: IResourceManager, seekTimeTicks: Double) {
			ensureAnimationLoaded(resourceManager)
			if (selectedAnimationNames.isEmpty()) return

			// GeoModel is reloaded per build in our pipeline, so we must:
			// - clear processor bone list
			// - clear snapshot cache (snapshots hold IBone references)
			// - register bones for THIS model instance
			val data = factory.getOrCreateAnimationData(uniqueId)
			data.clearSnapshotCache()

			val processor = model.animationProcessor
			processor.clearModelRendererList()
			registerBones(geoModel.topLevelBones, processor)

			val event = AnimationEvent(this, 0f, 0f, 0f, false, emptyList())

			// Give model a chance to set additional Molang queries.
			processor.preAnimationSetup(this, seekTimeTicks)

			// Apply animations to bones.
			try {
				processor.tickAnimation(this, uniqueId, seekTimeTicks, event, parser, false)
			} catch (e: Exception) {
				PrototypeMachinery.logger.warn(
					"[PM] Exception in GeckoLib tickAnimation (seekTimeTicks=$seekTimeTicks, animations=$selectedAnimationNames)",
					e
				)
			}
		}

		private fun registerBones(roots: List<GeoBone>, processor: AnimationProcessor<Runtime>) {
			for (root in roots) {
				registerBoneRecursively(root, processor)
			}
		}

		private fun registerBoneRecursively(bone: GeoBone, processor: AnimationProcessor<Runtime>) {
			processor.registerModelRenderer(bone)
			bone.childBones?.forEach { registerBoneRecursively(it, processor) }
		}

		private inner class RuntimeModel : IAnimatableModel<Runtime> {

			internal val animationProcessor: AnimationProcessor<Runtime> = AnimationProcessor(this)

			@Volatile
			internal var animationFile: AnimationFile? = null

			override fun setLivingAnimations(entity: Runtime, uniqueID: Int, customPredicate: AnimationEvent<*>?) {
				// No-op: we drive animation explicitly from the bake task.
			}

			override fun getAnimationProcessor(): AnimationProcessor<Runtime> = animationProcessor

			override fun getAnimation(name: String, animatable: IAnimatable): Animation? {
				return animationFile?.getAnimation(name)
			}

			override fun setMolangQueries(animatable: IAnimatable, currentTick: Double) {
				// MVP: nothing extra.
				// AnimationController.process will set query.life_time and query.anim_time.
			}
		}
	}
}

