package org.osmium.keyboard

/** One normalized touch sample, in keyboard-local pixels. */
data class GlidePoint(val x: Float, val y: Float)

/** A key's live rectangle, used to map a touch path onto physical key codes. */
data class GlideKey(
    val code: String,
    val cx: Float,
    val cy: Float,
    val halfW: Float,
    val halfH: Float,
)

/**
 * Android-free geometry for glide typing: resampling, nearest-key lookup and
 * key-crossing detection. Deterministic and unit-testable on the JVM.
 */
object GlidePath {

    /** Resamples [points] into [count] points evenly spaced by arc length. */
    fun resample(points: List<GlidePoint>, count: Int = 8): List<GlidePoint> {
        if (points.isEmpty() || count <= 0) return emptyList()
        if (points.size == 1) return List(count) { points.first() }
        val segments = points.zipWithNext()
        val lengths = segments.map { (a, b) -> kotlin.math.hypot(b.x - a.x, b.y - a.y) }
        val total = lengths.sum()
        if (total <= 0f) return List(count) { points.first() }
        return List(count) { index ->
            val target = total * index / (count - 1).coerceAtLeast(1)
            var walked = 0f
            for (i in lengths.indices) {
                val len = lengths[i]
                if (walked + len >= target || i == lengths.lastIndex) {
                    val t = if (len <= 0f) 0f else ((target - walked) / len).coerceIn(0f, 1f)
                    val (a, b) = segments[i]
                    return@List GlidePoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
                }
                walked += len
            }
            points.last()
        }
    }

    /** Nearest key whose rectangle is within the point, or the closest overall. */
    fun nearestKey(p: GlidePoint, keys: List<GlideKey>): GlideKey? {
        var best: GlideKey? = null
        var bestDist = Float.MAX_VALUE
        for (key in keys) {
            val dx = (p.x - key.cx).coerceIn(-key.halfW, key.halfW) - (p.x - key.cx)
            val dy = (p.y - key.cy).coerceIn(-key.halfH, key.halfH) - (p.y - key.cy)
            val dist = dx * dx + dy * dy
            if (dist < bestDist) {
                bestDist = dist
                best = key
            }
        }
        return best
    }

    /** Distinct key codes crossed by the path; repeats and nulls are dropped. */
    fun crossingSequence(
        points: List<GlidePoint>,
        keys: List<GlideKey>,
        minDistancePx: Float,
    ): List<String> {
        val out = mutableListOf<String>()
        var lastCode: String? = null
        var lastAnchor: GlidePoint? = null
        for (p in points) {
            val key = nearestKey(p, keys) ?: continue
            if (lastAnchor != null &&
                kotlin.math.hypot(p.x - lastAnchor.x, p.y - lastAnchor.y) < minDistancePx
            ) continue
            if (key.code != lastCode) {
                out += key.code
                lastCode = key.code
                lastAnchor = p
            }
        }
        return out
    }
}
