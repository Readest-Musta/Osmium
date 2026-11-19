package org.osmium.keyboard

import org.json.JSONObject

/** Bounded, decaying on-device learning state for glide/ranking personalisation. */
object UserLexicon {

    const val MAX_WORDS = 5000
    const val MAX_NGRAMS = 20000
    const val HALF_LIFE_DAYS = 60L
    private val DAY_MS = 24L * 60 * 60 * 1000

    data class WordStat(var count: Int, var lastUsed: Long)

    class State(
        val words: MutableMap<String, WordStat> = mutableMapOf(),
        val bigrams: MutableMap<String, MutableMap<String, Int>> = mutableMapOf(),
        val charNgrams: MutableMap<String, Int> = mutableMapOf(),
        val weights: FloatArray = FloatArray(7),
    )

    fun recordCommit(state: State, word: String, context: List<String>, now: Long, allowed: Boolean) {
        if (!allowed || word.isEmpty()) return
        val key = word.lowercase()
        val stat = state.words[key] ?: WordStat(0, now)
        stat.count++
        stat.lastUsed = now
        state.words[key] = stat
        val prev = context.lastOrNull()?.lowercase()
        if (prev != null) {
            val row = state.bigrams.getOrPut(prev) { mutableMapOf() }
            row[key] = (row[key] ?: 0) + 1
        }
        for (n in 3..5) {
            if (key.length >= n) {
                for (i in 0..key.length - n) {
                    val gram = key.substring(i, i + n)
                    state.charNgrams[gram] = (state.charNgrams[gram] ?: 0) + 1
                }
            }
        }
        age(state, now)
        evict(state)
    }

    /** Exponential decay with a fixed half-life; drops entries below threshold. */
    fun age(state: State, now: Long) {
        fun decay(value: Int, last: Long): Int {
            val days = (now - last).toDouble() / DAY_MS
            val factor = Math.pow(2.0, -days / HALF_LIFE_DAYS)
            return (value * factor).toInt()
        }
        val it = state.words.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            val next = decay(e.value.count, e.value.lastUsed)
            if (next <= 0) it.remove() else e.value.count = next
        }
        for (row in state.bigrams.values) {
            val i = row.entries.iterator()
            while (i.hasNext()) if (i.next().value <= 0) i.remove()
        }
        val ci = state.charNgrams.entries.iterator()
        while (ci.hasNext()) if (ci.next().value <= 1) ci.remove()
    }

    private fun evict(state: State) {
        while (state.words.size > MAX_WORDS) {
            state.words.remove(state.words.minByOrNull { it.value.lastUsed }?.key)
        }
        while (state.bigrams.size > MAX_NGRAMS) {
            state.bigrams.remove(state.bigrams.keys.first())
        }
        while (state.charNgrams.size > MAX_NGRAMS) {
            state.charNgrams.remove(state.charNgrams.keys.first())
        }
    }

    fun encode(state: State): String {
        val root = JSONObject()
        val words = JSONObject()
        for ((k, v) in state.words) words.put(k, "${v.count},${v.lastUsed}")
        root.put("words", words)
        val grams = JSONObject()
        for ((k, v) in state.charNgrams) grams.put(k, v)
        root.put("charNgrams", grams)
        val bi = JSONObject()
        for ((k, row) in state.bigrams) bi.put(k, JSONObject(row as Map<*, *>))
        root.put("bigrams", bi)
        val w = org.json.JSONArray()
        state.weights.forEach { w.put(it) }
        root.put("weights", w)
        return root.toString()
    }

    fun decode(raw: String?): State {
        val state = State()
        if (raw.isNullOrBlank()) return state
        try {
            val root = JSONObject(raw)
            val words = root.optJSONObject("words") ?: return state
            for (k in words.keys()) {
                val parts = words.getString(k).split(',')
                if (parts.size == 2) {
                    state.words[k] = WordStat(parts[0].toIntOrNull() ?: 0, parts[1].toLongOrNull() ?: 0L)
                }
            }
            val grams = root.optJSONObject("charNgrams") ?: return state
            for (k in grams.keys()) state.charNgrams[k] = grams.getInt(k)
            val bi = root.optJSONObject("bigrams") ?: return state
            for (k in bi.keys()) {
                val row = bi.getJSONObject(k)
                state.bigrams[k] = row.keys().associateWith { row.getInt(it) }.toMutableMap()
            }
            val w = root.optJSONArray("weights") ?: return state
            for (i in state.weights.indices) state.weights[i] = w.optDouble(i, 0.0).toFloat().coerceIn(-1f, 1f)
        } catch (_: Throwable) {
            return State()
        }
        return state
    }
}
