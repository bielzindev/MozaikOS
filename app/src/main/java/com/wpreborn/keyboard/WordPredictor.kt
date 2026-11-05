package com.wpreborn.keyboard

import android.content.Context
import java.io.File
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.min

/**
 * Word suggestions, autocorrection, next-word prediction and Word Flow shape matching.
 *
 * Dictionaries are frequency-ordered word lists (rank 0 = most common). Words the user types
 * are learned per language and ranked above the dictionary, as are word pairs for next-word
 * predictions.
 */
class WordPredictor(private val context: Context) {

    /**
     * A dictionary with everything a keystroke needs worked out once, when it loads: ranks,
     * the sorted list for completions, and each word with its accents folded, grouped by
     * first letter (folding with Normalizer on every keystroke froze the keyboard).
     */
    private class Dict(val words: List<String>) {
        val rank: Map<String, Int> = HashMap<String, Int>(words.size * 2).also { m -> words.forEachIndexed { i, w -> m.putIfAbsent(w, i) } }
        val sorted: List<String> = words.sorted()
        /** First letter (folded) -> words and their folded spelling, most common first. */
        val byFirst: Map<Char, List<Pair<String, String>>> = words.asSequence().filter { it.isNotEmpty() }
            .map { it to fold(it) }.filter { it.second.isNotEmpty() }.groupBy { it.second[0] }
    }

    private val empty = Dict(emptyList())
    private val loading = ConcurrentHashMap.newKeySet<String>()

    private val dicts = ConcurrentHashMap<String, Dict>()
    private val learned = ConcurrentHashMap<String, MutableMap<String, Int>>()
    private val bigrams = ConcurrentHashMap<String, MutableMap<String, MutableMap<String, Int>>>()

    /**
     * The language's dictionary. It is never built on the caller's thread: until the
     * background load is done, typing works with an empty dictionary instead of freezing.
     */
    private fun dict(lang: KbLanguage): Dict {
        val name = lang.dictionary ?: return empty
        dicts[name]?.let { return it }
        preload(lang)
        return empty
    }

    /** Loads the dictionary in the background (once), so keystrokes are never slowed down. */
    fun preload(lang: KbLanguage) {
        val name = lang.dictionary ?: return
        if (dicts.containsKey(name) || !loading.add(name)) return
        Thread {
            try {
                val words = try {
                    context.assets.open("dict/$name.txt").bufferedReader().readLines()
                } catch (_: Exception) {
                    emptyList()
                }
                dicts[name] = Dict(words)
                learnedFor(lang.code)
            } catch (_: Throwable) {
            } finally {
                loading.remove(name)
            }
        }.apply { priority = Thread.MIN_PRIORITY + 1 }.start()
    }

    private fun learnedFile(code: String) = File(context.filesDir, "kb_learned_$code.txt")

    private fun learnedFor(code: String): MutableMap<String, Int> = learned.getOrPut(code) {
        val map = ConcurrentHashMap<String, Int>()
        try {
            learnedFile(code).readLines().forEach { line ->
                val parts = line.split('\t')
                if (parts.size == 2) map[parts[0]] = parts[1].toIntOrNull() ?: 1
            }
        } catch (_: Exception) {
        }
        map
    }

    fun isKnown(lang: KbLanguage, word: String): Boolean {
        val w = word.lowercase()
        return dict(lang).rank.containsKey(w) || (learnedFor(lang.code)[w] ?: 0) > 0
    }

    /** Remembers a committed word (and the pair with the previous one). */
    fun learn(lang: KbLanguage, word: String, previous: String?) {
        val w = word.lowercase().trim { !it.isLetter() && it != '\'' }
        if (w.length < 2 || w.any { it.isDigit() }) return
        val map = learnedFor(lang.code)
        map[w] = (map[w] ?: 0) + 1
        if (previous != null) {
            val p = previous.lowercase()
            val pairs = bigrams.getOrPut(lang.code) { ConcurrentHashMap() }.getOrPut(p) { ConcurrentHashMap() }
            pairs[w] = (pairs[w] ?: 0) + 1
        }
        if (map.size % 10 == 0) save(lang.code)
    }

    fun forget(lang: KbLanguage, word: String) {
        learnedFor(lang.code).remove(word.lowercase())
        save(lang.code)
    }

    fun save(code: String) {
        val map = learned[code] ?: return
        try {
            learnedFile(code).writeText(map.entries.sortedByDescending { it.value }.take(5000).joinToString("\n") { "${it.key}\t${it.value}" })
        } catch (_: Exception) {
        }
    }

    /** Score: lower is better. Learned words beat the dictionary. */
    private fun score(lang: KbLanguage, w: String): Int {
        val learnedCount = learnedFor(lang.code)[w] ?: 0
        val rank = dict(lang).rank[w] ?: 50_000
        return rank - learnedCount * 2_000
    }

    /**
     * Suggestions for the word being typed: completions of the prefix and close corrections.
     * The first element is the autocorrect candidate when [autoCorrect] decides to replace.
     */
    fun suggest(lang: KbLanguage, typed: String, max: Int = 3): Suggestions {
        if (typed.isEmpty()) return Suggestions(emptyList(), null)
        val lower = typed.lowercase()
        val d = dict(lang)
        val candidates = HashMap<String, Int>()
        // Completions.
        val start = d.sorted.binarySearch(lower).let { if (it < 0) -it - 1 else it }
        var i = start
        while (i < d.sorted.size && d.sorted[i].startsWith(lower) && candidates.size < 400) {
            candidates[d.sorted[i]] = score(lang, d.sorted[i]) + (d.sorted[i].length - lower.length) * 300
            i++
        }
        learnedFor(lang.code).keys.filter { it.startsWith(lower) }.forEach { candidates[it] = score(lang, it) }
        // Corrections: edit distance with keyboard-neighbour weighting, same first letter.
        var correction: String? = null
        if (lower.length >= 2 && !isKnown(lang, lower)) {
            val fl = fold(lower)
            val first = fl.firstOrNull() ?: return Suggestions(emptyList(), null)
            val pool = d.byFirst[first].orEmpty().asSequence().take(12_000) +
                learnedFor(lang.code).keys.asSequence().filter { it.isNotEmpty() }.map { it to fold(it) }.filter { it.second.firstOrNull() == first }
            var best = Int.MAX_VALUE
            for ((w, fw) in pool) {
                if (abs(fw.length - fl.length) > 2) continue
                val dist = distance(fl, fw, lang)
                if (dist > if (lower.length <= 4) 1.0 else 2.0) continue
                val s = score(lang, w) + (dist * 4_000).toInt()
                candidates[w] = minOf(candidates[w] ?: Int.MAX_VALUE, s)
                if (s < best) { best = s; correction = w }
            }
        }
        val ordered = candidates.entries.sortedBy { it.value }.map { it.key }.filter { it != lower }.take(max)
        return Suggestions(ordered.map { matchCase(it, typed) }, correction?.let { matchCase(it, typed) })
    }

    /** Next-word predictions after a committed word. */
    fun predictNext(lang: KbLanguage, previous: String, max: Int = 3): List<String> =
        bigrams[lang.code]?.get(previous.lowercase())?.entries?.sortedByDescending { it.value }?.map { it.key }?.take(max).orEmpty()

    /**
     * Word Flow: candidate words for a swipe path. [keys] is the sequence of keys the finger
     * passed over (consecutive duplicates removed). A word matches when it starts with the
     * first key, ends near the last key, and its letters appear in order along the path.
     */
    fun matchSwipe(lang: KbLanguage, keys: String, max: Int = 4): List<String> {
        if (keys.length < 2) return emptyList()
        val path = fold(keys.lowercase())
        val first = path.first()
        val last = path.last()
        val d = dict(lang)
        val pool = d.byFirst[first].orEmpty().asSequence().take(20_000) +
            learnedFor(lang.code).keys.asSequence().filter { it.isNotEmpty() }.map { it to fold(it) }.filter { it.second.firstOrNull() == first }
        val scored = ArrayList<Pair<String, Int>>()
        for ((w, fw) in pool) {
            if (w.length < 2 || fw.isEmpty()) continue
            if (fw.last() != last && !neighbours(fw.last(), last, lang)) continue
            if (!isSubsequence(fw, path)) continue
            // Prefer words that use most of the path, then frequency.
            val unused = path.length - fw.toSet().size
            val s = score(lang, w) + unused * 600 + (if (fw.last() == last) 0 else 3_000)
            scored += w to s
        }
        return scored.sortedBy { it.second }.map { it.first }.distinct().take(max)
    }

    private fun isSubsequence(word: String, path: String): Boolean {
        var j = 0
        for (c in word) {
            // Double letters may come from a single key on the path.
            if (j > 0 && path[j - 1] == c) continue
            while (j < path.length && path[j] != c) j++
            if (j == path.length) return false
            j++
        }
        return true
    }

    /** Damerau-Levenshtein where substituting a neighbouring key costs half. */
    private fun distance(a: String, b: String, lang: KbLanguage): Double {
        val d = Array(a.length + 1) { DoubleArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i.toDouble()
        for (j in 0..b.length) d[0][j] = j.toDouble()
        for (i in 1..a.length) for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0.0 else if (neighbours(a[i - 1], b[j - 1], lang)) 0.5 else 1.0
            var v = min(min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = min(v, d[i - 2][j - 2] + 0.5)
            d[i][j] = v
        }
        return d[a.length][b.length]
    }

    private val positions = ConcurrentHashMap<String, Map<Char, Pair<Int, Int>>>()

    private fun neighbours(a: Char, b: Char, lang: KbLanguage): Boolean {
        val pos = positions.getOrPut(lang.code) {
            buildMap { lang.rows.forEachIndexed { r, row -> row.forEachIndexed { c, ch -> put(fold(ch), r to c) } } }
        }
        val pa = pos[a] ?: return false
        val pb = pos[b] ?: return false
        return abs(pa.first - pb.first) <= 1 && abs(pa.second - pb.second) <= 1
    }

    data class Suggestions(val words: List<String>, val correction: String?)

    companion object {
        /** Folds accents so "voce" finds "você" and swipes ignore diacritics. */
        /** Plain ASCII needs no folding (most words): skips the slow Normalizer. */
        fun fold(s: String): String =
            if (s.all { it.code < 128 }) s
            else Normalizer.normalize(s, Normalizer.Form.NFD).filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        fun fold(c: Char): Char = fold(c.toString()).firstOrNull() ?: c

        private fun matchCase(word: String, typed: String): String = when {
            typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> word.uppercase()
            typed.firstOrNull()?.isUpperCase() == true -> word.replaceFirstChar { it.uppercase() }
            else -> word
        }
    }
}
