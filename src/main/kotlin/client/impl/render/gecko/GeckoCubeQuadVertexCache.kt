package github.kasuminova.prototypemachinery.client.impl.render.gecko

import software.bernie.geckolib3.geo.render.built.GeoCube
import software.bernie.geckolib3.geo.render.built.GeoQuad
import java.util.concurrent.ConcurrentMap

/**
 * Caches per-cube quad vertex attributes in a compact primitive array to reduce pointer chasing in hot paths.
 *
 * We intentionally cache at **cube** granularity (not quad), because per-quad map lookups can dominate CPU time.
 *
 * Layout: for each *non-null* quad, for each of 4 vertices: [x, y, z, u, v] (floats).
 * Total length = quadCount * 4 * 5.
 *
 * Uses weak keys so entries can be GC'd on model reload.
 * Backed by a concurrent map to avoid global locking in hot paths.
 */
internal object GeckoCubeQuadVertexCache {

    internal class CubeData(
        val quadCount: Int,
        /** xyzuv per vertex, sequential by non-null quad order */
        val xyzuv: FloatArray,
    )

    // GeoCubes are owned by Gecko model instances; use weak keys to avoid pinning models forever.
    private val cache: ConcurrentMap<GeoCube, CubeData> =
        com.google.common.collect.MapMaker().weakKeys().makeMap()

    internal fun getOrBake(cube: GeoCube): CubeData {
        val hit = cache[cube]
        if (hit != null) return hit

        // Lock-free publication: in rare races, multiple threads may bake the same cube.
        // This is acceptable (CubeData is immutable) and usually cheaper than a global lock.
        val baked = bake(cube)
        val prev = cache.putIfAbsent(cube, baked)
        return prev ?: baked
    }

    private fun bake(cube: GeoCube): CubeData {
        val quads = cube.quads
        if (quads.isEmpty()) {
            return CubeData(quadCount = 0, xyzuv = FloatArray(0))
        }

        var quadCount = 0
        for (q in quads) {
            if (q != null) {
                val vn = q.vertices.size
                check(vn == 4) { "GeoQuad.vertices must have size 4, got $vn" }
                quadCount++
            }
        }

        if (quadCount == 0) {
            return CubeData(quadCount = 0, xyzuv = FloatArray(0))
        }

        val out = FloatArray(quadCount * 4 * 5)
        var o = 0
        for (q in quads) {
            if (q == null) continue
            bakeQuad(q, out, o)
            o += 4 * 5
        }
        return CubeData(quadCount = quadCount, xyzuv = out)
    }

    private fun bakeQuad(quad: GeoQuad, out: FloatArray, base: Int) {
        val verts = quad.vertices
        val vn = verts.size
        check(vn == 4) { "GeoQuad.vertices must have size 4, got $vn" }

        var i = 0
        var o = base
        while (i < 4) {
            val v = verts[i]
            val p = v.position
            out[o + 0] = p.x
            out[o + 1] = p.y
            out[o + 2] = p.z
            out[o + 3] = v.textureU.toFloat()
            out[o + 4] = v.textureV.toFloat()
            i++
            o += 5
        }
    }
}
