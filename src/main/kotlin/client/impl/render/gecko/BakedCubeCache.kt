package github.kasuminova.prototypemachinery.client.impl.render.gecko

import github.kasuminova.prototypemachinery.client.impl.render.RenderStats
import software.bernie.geckolib3.geo.render.built.GeoCube
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Caches pre-baked cube vertices with cube-local transforms already applied.
 *
 * This allows all cubes under the same bone to share the bone matrix at runtime,
 * enabling efficient bone-level batching regardless of individual cube rotations.
 *
 * The cache uses [WeakHashMap] so entries are automatically evicted when the
 * [GeoCube] instance is no longer referenced (e.g., model reload).
 */
internal object BakedCubeCache {

    /**
     * Pre-baked vertex data for a single cube.
     *
     * Positions and normals have cube-local transforms (pivot/rotation) already applied.
     * At render time, only the bone matrix needs to be applied.
     */
    internal class BakedCube(
        /** Number of valid quads in this cube. */
        val quadCount: Int,
        /** Total vertex count (quadCount * 4 for standard quads). */
        val vertexCount: Int,
        /**
         * Pre-transformed positions: [x0, y0, z0, x1, y1, z1, ...].
         * Length = vertexCount * 3.
         */
        val positions: FloatArray,
        /**
         * Pre-transformed normals per quad: [nx0, ny0, nz0, nx1, ny1, nz1, ...].
         * Length = quadCount * 3.
         * Each quad's 4 vertices share the same normal.
         */
        val normals: FloatArray,
        /**
         * UV coordinates: [u0, v0, u1, v1, ...].
         * Length = vertexCount * 2.
         */
        val uvs: FloatArray,
        /**
         * Flat-cube flags per quad for shading fix.
         * Encoded as: bit0 = fixNx, bit1 = fixNy, bit2 = fixNz.
         * Length = quadCount.
         */
        val flatFlags: ByteArray,
    )

    // Use WeakHashMap so cache entries are GC'd when GeoCube instances are released.
    // WeakHashMap is NOT thread-safe; baking can run on multiple threads in some pipelines,
    // so we guard all map access with a single lock.
    private val lock = Any()
    private val cache = WeakHashMap<GeoCube, BakedCube>()

    // Per-thread front cache to avoid global lock contention in multi-threaded bake pipelines.
    // WeakHashMap keys are weak, so this will not pin GeoCube instances indefinitely.
    private val threadLocalCache: ThreadLocal<WeakHashMap<GeoCube, BakedCube>> =
        ThreadLocal.withInitial { WeakHashMap<GeoCube, BakedCube>(256) }

    // Statistics
    private val cacheHitsCounter = AtomicLong(0)
    private val cacheMissesCounter = AtomicLong(0)

    internal val cacheHits: Long
        get() = cacheHitsCounter.get()

    internal val cacheMisses: Long
        get() = cacheMissesCounter.get()

    @Volatile
    internal var cacheSize: Int = 0
        private set

    /**
     * Get or create baked data for the given cube.
     *
     * @return Baked cube data, or null if the cube has unexpected topology.
     */
    internal fun getOrBake(cube: GeoCube): BakedCube? {
        // Fastest path: per-thread cache hit (no locking).
        val local = threadLocalCache.get()
        val localHit = local[cube]
        if (localHit != null) {
            if (RenderStats.enabled) cacheHitsCounter.incrementAndGet()
            return localHit
        }

        // Next: global cache lookup (locked), then seed thread-local cache.
        val globalHit = synchronized(lock) { cache[cube] }
        if (globalHit != null) {
            local[cube] = globalHit
            if (RenderStats.enabled) cacheHitsCounter.incrementAndGet()
            return globalHit
        }

        // Miss: bake outside the lock to avoid blocking other readers.
        if (RenderStats.enabled) cacheMissesCounter.incrementAndGet()

        val baked = bake(cube) ?: return null

        // Publish / de-duplicate.
        val published = synchronized(lock) {
            val again = cache[cube]
            if (again != null) {
                again
            } else {
                cache[cube] = baked
                cacheSize = cache.size
                baked
            }
        }

        // Seed local cache for subsequent queries on this thread.
        if (published != null) {
            local[cube] = published
        }

        return published
    }

    /**
     * Bake a cube: apply cube-local transforms to all vertices/normals.
     */
    private fun bake(cube: GeoCube): BakedCube? {
        val quads = cube.quads
        if (quads.isEmpty()) {
            // Empty cube: return a degenerate baked cube.
            return BakedCube(
                quadCount = 0,
                vertexCount = 0,
                positions = FloatArray(0),
                normals = FloatArray(0),
                uvs = FloatArray(0),
                flatFlags = ByteArray(0),
            )
        }

        // Count and validate quads.
        var quadCount = 0
        for (q in quads) {
            if (q != null) {
                if (q.vertices.size != 4) {
                    // Unexpected topology.
                    return null
                }
                quadCount++
            }
        }
        if (quadCount == 0) {
            return BakedCube(
                quadCount = 0,
                vertexCount = 0,
                positions = FloatArray(0),
                normals = FloatArray(0),
                uvs = FloatArray(0),
                flatFlags = ByteArray(0),
            )
        }

        val vertexCount = quadCount * 4

        // Build cube-local transform matrix.
        // Transform: translate(pivot/16) * rotate(rotation) * translate(-pivot/16)
        // Note: pivot is in BlockBench units (pixels), vertex positions are in model units (1/16 block).
        val pivot = cube.pivot
        val rotation = cube.rotation

        // Convert pivot from BlockBench units to model units (divide by 16).
        val px = (pivot?.x ?: 0f) / 16f
        val py = (pivot?.y ?: 0f) / 16f
        val pz = (pivot?.z ?: 0f) / 16f

        val rx = rotation?.x ?: 0f
        val ry = rotation?.y ?: 0f
        val rz = rotation?.z ?: 0f

        // If rotation is identity, we can skip matrix math entirely.
        val isIdentity = rx == 0f && ry == 0f && rz == 0f

        // Rotation matrix (ZYX Euler, matching GeckoLib's convention).
        // For identity, these are just I.
        val cosX = if (isIdentity) 1f else kotlin.math.cos(rx).toFloat()
        val sinX = if (isIdentity) 0f else kotlin.math.sin(rx).toFloat()
        val cosY = if (isIdentity) 1f else kotlin.math.cos(ry).toFloat()
        val sinY = if (isIdentity) 0f else kotlin.math.sin(ry).toFloat()
        val cosZ = if (isIdentity) 1f else kotlin.math.cos(rz).toFloat()
        val sinZ = if (isIdentity) 0f else kotlin.math.sin(rz).toFloat()

        // Combined rotation matrix R = Rz * Ry * Rx (column-major logic, row-major storage).
        val r00 = cosY * cosZ
        val r01 = sinX * sinY * cosZ - cosX * sinZ
        val r02 = cosX * sinY * cosZ + sinX * sinZ
        val r10 = cosY * sinZ
        val r11 = sinX * sinY * sinZ + cosX * cosZ
        val r12 = cosX * sinY * sinZ - sinX * cosZ
        val r20 = -sinY
        val r21 = sinX * cosY
        val r22 = cosX * cosY

        // Allocate output arrays.
        val positions = FloatArray(vertexCount * 3)
        val normals = FloatArray(quadCount * 3)
        val uvs = FloatArray(vertexCount * 2)
        val flatFlags = ByteArray(quadCount)

        // Fill arrays.
        var vOut = 0
        var qOut = 0
        for (quad in quads) {
            if (quad == null) continue

            // Transform normal.
            val qnx = quad.normal.x.toFloat()
            val qny = quad.normal.y.toFloat()
            val qnz = quad.normal.z.toFloat()

            val tnx: Float
            val tny: Float
            val tnz: Float
            if (isIdentity) {
                tnx = qnx
                tny = qny
                tnz = qnz
            } else {
                tnx = r00 * qnx + r01 * qny + r02 * qnz
                tny = r10 * qnx + r11 * qny + r12 * qnz
                tnz = r20 * qnx + r21 * qny + r22 * qnz
            }

            normals[qOut * 3 + 0] = tnx
            normals[qOut * 3 + 1] = tny
            normals[qOut * 3 + 2] = tnz

            // Flat-cube shading fix flags.
            var flags = 0
            if (cube.size.y == 0f || cube.size.z == 0f) flags = flags or 1
            if (cube.size.x == 0f || cube.size.z == 0f) flags = flags or 2
            if (cube.size.x == 0f || cube.size.y == 0f) flags = flags or 4
            flatFlags[qOut] = flags.toByte()

            // Transform vertices.
            val verts = quad.vertices
            for (vi in 0 until 4) {
                val vertex = verts[vi]
                val pos = vertex.position

                val ox = pos.x
                val oy = pos.y
                val oz = pos.z

                // Apply: translate(-pivot), rotate, translate(pivot).
                val lx = ox - px
                val ly = oy - py
                val lz = oz - pz

                val tpx: Float
                val tpy: Float
                val tpz: Float
                if (isIdentity) {
                    tpx = ox
                    tpy = oy
                    tpz = oz
                } else {
                    tpx = r00 * lx + r01 * ly + r02 * lz + px
                    tpy = r10 * lx + r11 * ly + r12 * lz + py
                    tpz = r20 * lx + r21 * ly + r22 * lz + pz
                }

                positions[vOut * 3 + 0] = tpx
                positions[vOut * 3 + 1] = tpy
                positions[vOut * 3 + 2] = tpz

                uvs[vOut * 2 + 0] = vertex.textureU.toFloat()
                uvs[vOut * 2 + 1] = vertex.textureV.toFloat()

                vOut++
            }

            qOut++
        }

        return BakedCube(
            quadCount = quadCount,
            vertexCount = vertexCount,
            positions = positions,
            normals = normals,
            uvs = uvs,
            flatFlags = flatFlags,
        )
    }

    /**
     * Clear the cache. Useful for resource reload or profiling.
     */
    internal fun clear() {
        synchronized(lock) {
            cache.clear()
            cacheSize = 0
        }

        // Also clear thread-local caches (best effort: current thread only).
        threadLocalCache.get().clear()
    }

    /**
     * Reset statistics.
     */
    internal fun resetStats() {
        cacheHitsCounter.set(0)
        cacheMissesCounter.set(0)
    }

    /**
     * Get current cache statistics snapshot.
     */
    internal fun statsSnapshot(): Triple<Long, Long, Int> {
        // cacheSize is volatile; hits/misses are atomic.
        return Triple(cacheHitsCounter.get(), cacheMissesCounter.get(), cacheSize)
    }
}
