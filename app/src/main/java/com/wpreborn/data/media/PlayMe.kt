package com.wpreborn.data.media

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thumbs up / thumbs down on songs, as in MixRadio. Up makes the song and its artist come back
 * more often in "play me"; down skips it and keeps it (and, a little, its artist) away.
 */
class Votes private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("music_votes", Context.MODE_PRIVATE)
    private val _votes = MutableStateFlow(prefs.all.mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }.toMap())
    /** Song id → +1 (thumbs up) or -1 (thumbs down). */
    val votes: StateFlow<Map<String, Int>> = _votes.asStateFlow()

    fun vote(songId: String, value: Int) {
        val now = _votes.value[songId]
        val v = if (now == value) 0 else value // pressing again takes the vote back
        _votes.value = if (v == 0) _votes.value - songId else _votes.value + (songId to v)
        prefs.edit().apply { if (v == 0) remove(songId) else putInt(songId, v) }.apply()
    }

    companion object {
        @Volatile private var instance: Votes? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: Votes(context.applicationContext).also { instance = it } }
    }
}

/**
 * "play me", offline: personal radio from your own music, in the spirit of MixRadio.
 * It starts from one of your favourite artists (most played, liked, starred), mixes in your
 * other favourites, then broadens to music like it (same genre or decade); thumbs-down songs
 * never come back. Pressing "play me" again starts from the next favourite artist, so each
 * press is a different radio.
 */
object PlayMe {
    fun artistScores(col: MusicCollection, plays: Map<Long, Int>, favorites: Set<String>, votes: Map<String, Int>): Map<String, Double> {
        val score = HashMap<String, Double>()
        for (s in col.songs) {
            if (s.artist.isBlank() || s.artist == MusicLibrary.UNKNOWN) continue
            val id = s.id.toString()
            var v = (plays[s.id] ?: 0).toDouble()
            if (id in favorites) v += 4
            when (votes[id]) {
                1 -> v += 5
                -1 -> v -= 4
            }
            score[s.artist] = (score[s.artist] ?: 0.0) + v
        }
        return score
    }

    /** A radio of about [size] songs seeded from the [seed]-th favourite artist. */
    fun build(col: MusicCollection, plays: Map<Long, Int>, favorites: Set<String>, votes: Map<String, Int>, seed: Int, size: Int = 40): Pair<String, List<Song>> {
        val allowed = col.songs.filter { votes[it.id.toString()] != -1 }
        if (allowed.isEmpty()) return "" to emptyList()
        val scores = artistScores(col, plays, favorites, votes)
        val ranked = scores.entries.filter { it.value > 0 }.sortedByDescending { it.value }.map { it.key }
            .ifEmpty { allowed.map { it.artist }.filter { it.isNotBlank() }.distinct().shuffled() }
        if (ranked.isEmpty()) return "" to allowed.shuffled().take(size)
        val seedArtist = ranked[Math.floorMod(seed, ranked.size)]
        val bySeed = allowed.filter { it.artist == seedArtist }
        val genres = bySeed.mapNotNull { it.genre?.lowercase() }.toSet()
        val decades = bySeed.filter { it.year > 0 }.map { it.year / 10 }.toSet()
        val favouriteArtists = ranked.take(8).toSet() - seedArtist

        fun weight(s: Song): Double {
            val id = s.id.toString()
            var w = 1.0 + (plays[s.id] ?: 0) * 0.3
            if (id in favorites) w += 2.0
            if (votes[id] == 1) w += 3.0
            return w * Math.random()
        }
        // About 35% seed artist, 35% favourite artists, 30% "music like it".
        val fromSeed = bySeed.sortedByDescending { weight(it) }.take((size * 0.35).toInt().coerceAtLeast(1))
        val fromFavourites = allowed.filter { it.artist in favouriteArtists }.sortedByDescending { weight(it) }.take((size * 0.35).toInt())
        val alike = allowed.filter { s ->
            s.artist != seedArtist && s.artist !in favouriteArtists &&
                ((s.genre?.lowercase() in genres) || (s.year > 0 && s.year / 10 in decades))
        }.sortedByDescending { weight(it) }
        val broaden = (alike + allowed.shuffled()).distinctBy { it.id }.filter { it.id !in (fromSeed + fromFavourites).map { s -> s.id } }
            .take(size - fromSeed.size - fromFavourites.size)
        // Start with the seed artist, then interleave so one artist doesn't play twice in a row.
        val pool = (fromFavourites + broaden).shuffled().toMutableList()
        val out = ArrayList<Song>()
        val seedQueue = fromSeed.toMutableList()
        if (seedQueue.isNotEmpty()) out += seedQueue.removeAt(0)
        while (pool.isNotEmpty() || seedQueue.isNotEmpty()) {
            val last = out.lastOrNull()?.artist
            val takeSeed = seedQueue.isNotEmpty() && (pool.isEmpty() || out.size % 3 == 0)
            val src = if (takeSeed) seedQueue else pool
            val i = src.indexOfFirst { it.artist != last }.takeIf { it >= 0 } ?: 0
            out += src.removeAt(i)
        }
        return seedArtist to out.take(size)
    }

    /** "create a mix": up to three artists you choose, plus music like theirs. */
    fun fromArtists(col: MusicCollection, artists: List<String>, votes: Map<String, Int>, size: Int = 40): List<Song> {
        val allowed = col.songs.filter { votes[it.id.toString()] != -1 }
        val chosen = allowed.filter { it.artist in artists }
        val genres = chosen.mapNotNull { it.genre?.lowercase() }.toSet()
        val alike = allowed.filter { it.artist !in artists && it.genre?.lowercase() in genres }.shuffled()
        return (chosen.shuffled().take((size * 0.7).toInt()) + alike).distinctBy { it.id }.take(size).shuffled()
    }
}
