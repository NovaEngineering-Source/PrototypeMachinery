package github.kasuminova.prototypemachinery.client.util

import com.cleanroommc.modularui.utils.fakeworld.DummyWorld
import net.minecraft.block.Block
import net.minecraft.block.material.Material
import net.minecraft.block.state.IBlockState
import net.minecraft.client.Minecraft
import net.minecraft.init.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.math.Vec3d
import net.minecraftforge.fluids.FluidRegistry
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.FluidUtil

/**
 * Utilities for building *display* ItemStacks from block requirements.
 *
 * Many blocks encode non-item-relevant properties into meta (e.g. facing/formed flags).
 * Using that meta directly for an ItemStack may result in the missing-model (purple/black) item.
 */
internal object ItemStackDisplayUtil {

    private object DummyPickWorld {
        private val pos: BlockPos = BlockPos(0, 0, 0)
        private val hit: Vec3d = Vec3d(0.5, 0.5, 0.5)

        // Keep this extremely small: single-position, single-state cache.
        private var lastKey: String? = null

        fun pick(block: Block, state: IBlockState, player: net.minecraft.entity.player.EntityPlayer): ItemStack? {
            val world = DummyWorld.INSTANCE

            val key = (block.registryName?.toString() ?: block.javaClass.name) + "|" + state.toString()
            if (key != lastKey) {
                lastKey = key

                try {
                    // Ensure we don't retain stale TE when swapping block/state.
                    world.removeTileEntity(pos)
                } catch (_: Throwable) {
                    // ignore
                }

                try {
                    // Flags are irrelevant in DummyWorld (neighbors/updates are NOOP).
                    world.setBlockState(pos, state, 2)
                } catch (_: Throwable) {
                    // If setBlockState fails, still allow calling getPickBlock (some impls don't need world state).
                }

                try {
                    if (block.hasTileEntity(state)) {
                        val te = block.createTileEntity(world, state)
                        if (te != null) {
                            try {
                                te.setWorld(world)
                            } catch (_: Throwable) {
                                // ignore
                            }
                            try {
                                te.setPos(pos)
                            } catch (_: Throwable) {
                                // ignore
                            }
                            world.setTileEntity(pos, te)
                        }
                    }
                } catch (_: Throwable) {
                    // ignore
                }
            }

            return try {
                val rtr = RayTraceResult(hit, EnumFacing.UP, pos)
                @Suppress("DEPRECATION")
                block.getPickBlock(state, rtr, world, pos, player)
            } catch (_: Throwable) {
                null
            }
        }
    }

    /** Cache of a "known-good" item meta per block registry name (display only). */
    private val goodMetaCache = HashMap<String, Int>(64)

    fun stackForBlockId(blockId: ResourceLocation, stateMeta: Int, count: Int): ItemStack? {
        val block = Block.REGISTRY.getObject(blockId) ?: return null
        return stackForBlock(block, stateMeta, count)
    }

    fun stackForBlock(block: Block, stateMeta: Int, count: Int): ItemStack? {
        if (block == Blocks.AIR) return null

        val clampedCount = count.coerceAtLeast(1)
        val state = safeStateFromMeta(block, stateMeta)

        // Some mod blocks rely on pick-block to return the actual item representation.
        // This is best-effort: we don't have a real world position/tile, so TE-dependent pick results may not work.
        val picked = safePickBlock(block, state)
        if (picked != null && !picked.isEmpty) {
            if (!isMissingItemModel(picked)) {
                picked.count = clampedCount
                return picked
            }
        }

        // Prefer the dropped damage for display (ignores orientation-only meta bits for many blocks).
        val droppedMeta = safeDamageDropped(block, state) ?: stateMeta

        // If we have a cached "good" meta for this block, try it early.
        val cacheKey = block.registryName?.toString() ?: block.javaClass.name
        val cachedMeta = goodMetaCache[cacheKey]

        val candidates = LinkedHashSet<Int>(20)
        if (cachedMeta != null) candidates.add(cachedMeta)
        candidates.add(droppedMeta)
        candidates.add(stateMeta)
        candidates.add(0)

        var firstNonEmpty: ItemStack? = null

        fun tryMeta(meta: Int): ItemStack? {
            val st = ItemStack(block, clampedCount, meta)
            if (st.isEmpty) return null
            if (firstNonEmpty == null) firstNonEmpty = st
            if (isMissingItemModel(st)) return null
            goodMetaCache[cacheKey] = meta
            return st
        }

        for (m in candidates) {
            val ok = tryMeta(m)
            if (ok != null) return ok
        }

        // Last resort: scan 0..15 (common metadata range) to find a renderable variant.
        for (m in 0..15) {
            if (candidates.contains(m)) continue
            val ok = tryMeta(m)
            if (ok != null) return ok
        }

        // Fluid blocks: use a filled bucket for display (stable even when block item is missing).
        // Keep this as a fallback to avoid changing display for blocks that already have a valid item model.
        val bucket = tryFluidBucket(block, state, clampedCount)
        if (bucket != null) return bucket

        return firstNonEmpty
    }

    private fun safePickBlock(block: Block, state: IBlockState?): ItemStack? {
        if (state == null) return null
        return try {
            val mc = Minecraft.getMinecraft()
            val player = mc.player ?: return null

            // Use a virtual world and attach a TileEntity when needed.
            DummyPickWorld.pick(block, state, player)
        } catch (_: Throwable) {
            null
        }
    }

    private fun safeStateFromMeta(block: Block, meta: Int): IBlockState? {
        return try {
            @Suppress("DEPRECATION")
            block.getStateFromMeta(meta)
        } catch (_: Throwable) {
            null
        }
    }

    private fun safeDamageDropped(block: Block, state: IBlockState?): Int? {
        if (state == null) return null
        return try {
            @Suppress("DEPRECATION")
            block.damageDropped(state)
        } catch (_: Throwable) {
            null
        }
    }

    private fun tryFluidBucket(block: Block, state: IBlockState?, count: Int): ItemStack? {
        val fluid = FluidRegistry.lookupFluidForBlock(block)
            ?: when (state?.material) {
                Material.WATER -> FluidRegistry.WATER
                Material.LAVA -> FluidRegistry.LAVA
                else -> null
            }

        if (fluid != null) {
            val bucket = FluidUtil.getFilledBucket(FluidStack(fluid, 1000))
            if (!bucket.isEmpty) {
                bucket.count = count
                return bucket
            }
        }
        return null
    }

    /**
     * Best-effort missing-model detection.
     * If anything goes wrong, treat as "not missing" to avoid false negatives breaking display.
     */
    private fun isMissingItemModel(stack: ItemStack): Boolean {
        return try {
            val mc = Minecraft.getMinecraft()
            val mesher = mc.renderItem.itemModelMesher
            val model = mesher.getItemModel(stack)

            val modelManager: Any? = try {
                mesher.javaClass.getMethod("getModelManager").invoke(mesher)
            } catch (_: Throwable) {
                try {
                    val f = mesher.javaClass.getDeclaredField("modelManager")
                    f.isAccessible = true
                    f.get(mesher)
                } catch (_: Throwable) {
                    null
                }
            }

            val missing: Any? = try {
                modelManager?.javaClass?.getMethod("getMissingModel")?.invoke(modelManager)
            } catch (_: Throwable) {
                null
            }

            when {
                missing != null -> model === missing
                // Heuristic fallback (should be rare): class name contains Missing.
                else -> model.javaClass.name.contains("Missing", ignoreCase = true)
            }
        } catch (_: Throwable) {
            false
        }
    }
}
