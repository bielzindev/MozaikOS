package com.wpreborn.data.media

import kotlin.math.abs
import kotlin.math.ln
import kotlin.random.Random

/**
 * Autoplay radio, like Spotify's: when what you chose to play (a single, an album, a
 * playlist) is about to end, more songs from your own music are added - by the same artists,
 * of the same genres and years, your favourites and most played first, never a song that is
 * already in the queue or that you voted down, and never long runs of one artist.
 */
object Radio {
    fun pick(
        col: MusicCollection,
        queued: List<Long>,
        plays: Map<Long, Int>,
        votes: Map<String, Int>,
        favorites: Set<String>,
        /** Songs played lately (newest last): not picked again. */
        recent: List<Long> = emptyList(),
        size: Int = 12,
        random: Random = Random(System.nanoTime()),
    ): List<Song> {
        val byId = col.songs.associateBy { it.id }
        val seeds = queued.takeLast(5).mapNotNull { byId[it] }
        if (seeds.isEmpty()) return emptyList()
        val inQueue = (queued + recent).toHashSet()
        // The same song in another file (downloaded twice, another album) counts as the same.
        fun key(s: Song) = s.title.lowercase().filter { it.isLetterOrDigit() } + "|" + s.artist.lowercase().filter { it.isLetterOrDigit() }
        val playedKeys = (queued + recent).mapNotNull { byId[it] }.map(::key).toHashSet()
        val artists = seeds.map { it.artist.lowercase() }.toSet()
        val genres = seeds.mapNotNull { it.genre?.lowercase()?.takeIf { g -> g.isNotBlank() } }.toSet()
        val years = seeds.map { it.year }.filter { it > 0 }
        val favs = favorites.map { it.lowercase() }.toSet()
        val scored = col.songs.asSequence()
            .filter { it.id !in inQueue && key(it) !in playedKeys && (votes[it.id.toString()] ?: 0) >= 0 }
            .map { s ->
                val artist = s.artist.lowercase()
                var w = 1.0
                if (artist in artists) w += 3.0
                if (s.genre?.lowercase()?.let { it in genres } == true) w += 2.0
                if (s.year > 0 && years.any { abs(it - s.year) <= 5 }) w += 1.0
                if (artist in favs) w += 1.5
                if ((votes[s.id.toString()] ?: 0) > 0) w += 2.0
                w += ln(1.0 + (plays[s.id] ?: 0)) * 0.6
                // A little chance, so the radio is never the same twice.
                s to w * (0.6 + random.nextDouble() * 0.8)
            }
            .sortedByDescending { it.second }
            .toList()
        val out = ArrayList<Song>(size)
        val perArtist = HashMap<String, Int>()
        val picked = HashSet<String>()
        for ((s, _) in scored) {
            if (out.size >= size) break
            if (!picked.add(key(s))) continue
            val a = s.artist.lowercase()
            if ((perArtist[a] ?: 0) >= 4) continue
            if (out.size >= 2 && out.takeLast(2).all { it.artist.lowercase() == a }) continue
            out += s
            perArtist[a] = (perArtist[a] ?: 0) + 1
        }
        return out
    }
}
