package com.wpreborn.keyboard

/** Emoji pages of the keyboard (Windows Phone 8.1 had a categorised emoji keyboard). */
object EmojiData {
    private fun cps(vararg ranges: IntRange): List<String> = ranges.flatMap { r -> r.map { String(Character.toChars(it)) } }

    val categories: List<Pair<String, List<String>>> = listOf(
        "☺" to cps(0x1F600..0x1F64F) + cps(0x1F910..0x1F92F) + cps(0x1F970..0x1F976) + listOf("🥺", "🫠", "🫡", "🫢", "🫣", "🫤", "🥹"),
        "👍" to cps(0x1F44A..0x1F450) + listOf("👍", "👎", "👌", "✌", "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆", "👇", "☝", "✋", "🤚", "🖐", "🖖", "👋", "🤝", "🙏", "💪", "🦾", "✍", "💅", "🤳", "👀", "👁", "👄", "💋", "🫶") +
            cps(0x1F466..0x1F469) + listOf("🧑", "👴", "👵", "👶", "🧒", "👮", "👷", "💂", "🕵", "👩‍💻", "👨‍💻", "🧑‍🎓", "🧑‍🍳", "🧑‍🚀", "🤴", "👸", "🎅", "🤶", "🦸", "🧙", "🧚", "🧛", "🧜", "🧝", "💃", "🕺", "👯", "🧘"),
        "❤" to listOf("❤", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟", "♥", "💌", "💯", "💢", "💥", "💫", "💦", "💨", "🕳", "💬", "💭", "💤", "✨", "🔥", "⭐", "🌟", "⚡", "☀", "🌈", "☁", "❄", "☔", "🎉", "🎊"),
        "🐱" to cps(0x1F400..0x1F43C) + listOf("🦁", "🦊", "🦝", "🦒", "🦓", "🦄", "🐝", "🦋", "🐞", "🕷", "🦀", "🦑", "🐙", "🦈", "🐬", "🐳", "🌵", "🌲", "🌳", "🌴", "🌱", "🌿", "🍀", "🍁", "🍂", "🌷", "🌹", "🌺", "🌸", "🌼", "🌻"),
        "🍔" to cps(0x1F345..0x1F37F) + listOf("🥑", "🥦", "🥕", "🌽", "🥐", "🥖", "🧀", "🥚", "🥓", "🥩", "🌮", "🌯", "🥗", "🍿", "🧁", "🥤", "🧃", "🧉", "🍷", "🍺"),
        "⚽" to listOf("⚽", "🏀", "🏈", "⚾", "🎾", "🏐", "🏉", "🎱", "🏓", "🏸", "🥊", "🥋", "⛳", "🎣", "🎿", "🏂", "🏋", "🤸", "🚴", "🏊", "🏄", "🏆", "🥇", "🥈", "🥉", "🏅", "🎮", "🕹", "🎲", "🧩", "♟", "🎯", "🎳", "🎸", "🎹", "🎺", "🎻", "🥁", "🎤", "🎧", "🎬", "🎨", "🎭"),
        "🚗" to cps(0x1F680..0x1F6A4) + listOf("🚗", "🚕", "🚙", "🏎", "🚓", "🚑", "🚒", "🚚", "🛵", "🏍", "🚲", "✈", "🛫", "🛬", "🚢", "⛵", "🏠", "🏡", "🏢", "🏥", "🏦", "🏨", "🏫", "⛪", "🕌", "🗽", "🗼", "🏰", "🌋", "🏖", "🏝", "🌅", "🌃", "🌉", "🎡", "🎢"),
        "💡" to listOf("⌚", "📱", "💻", "⌨", "🖥", "🖨", "🖱", "💾", "💿", "📷", "📸", "📹", "🎥", "📞", "☎", "📺", "📻", "⏰", "⏳", "🔋", "🔌", "💡", "🔦", "🕯", "💸", "💵", "💰", "💳", "💎", "⚖", "🔧", "🔨", "⚙", "🔫", "💣", "🔪", "🛡", "🔮", "💊", "💉", "🧬", "🔬", "🔭", "🧹", "🧺", "🛒", "🎁", "🎈", "✉", "📦", "📝", "📚", "📌", "📎", "✂", "🔒", "🔑"),
        "✅" to listOf("✅", "☑", "✔", "❌", "❎", "➕", "➖", "➗", "✖", "❓", "❔", "❗", "❕", "‼", "⁉", "⚠", "🚫", "⛔", "🔞", "♻", "🆗", "🆕", "🆒", "🆓", "🆘", "🔴", "🟠", "🟡", "🟢", "🔵", "🟣", "⚫", "⚪", "🟥", "🟩", "🟦", "▶", "⏸", "⏹", "⏺", "⏭", "⏮", "🔀", "🔁", "🔂", "🔼", "🔽", "➡", "⬅", "⬆", "⬇", "↩", "↪", "🔃", "🔄", "©", "®", "™", "#️⃣", "*️⃣"),
        "🏳" to listOf("🏳", "🏴", "🏁", "🚩", "🏳️‍🌈", "🇧🇷", "🇵🇹", "🇺🇸", "🇬🇧", "🇪🇸", "🇫🇷", "🇩🇪", "🇮🇹", "🇯🇵", "🇰🇷", "🇨🇳", "🇮🇳", "🇷🇺", "🇺🇦", "🇦🇷", "🇲🇽", "🇨🇦", "🇦🇴", "🇲🇿", "🇨🇻", "🇳🇱", "🇹🇷", "🇬🇷", "🇸🇪", "🇳🇴", "🇫🇮", "🇵🇱"),
    )
}

/**
 * Korean dubeolsik automaton: combines jamo into syllables (initial + medial + final),
 * including compound vowels/finals and moving a final consonant to the next syllable.
 */
class HangulComposer {
    private val initials = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    private val medials = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
    private val finals = " ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"
    private val vowelPairs = mapOf("ㅗㅏ" to 'ㅘ', "ㅗㅐ" to 'ㅙ', "ㅗㅣ" to 'ㅚ', "ㅜㅓ" to 'ㅝ', "ㅜㅔ" to 'ㅞ', "ㅜㅣ" to 'ㅟ', "ㅡㅣ" to 'ㅢ')
    private val finalPairs = mapOf("ㄱㅅ" to 'ㄳ', "ㄴㅈ" to 'ㄵ', "ㄴㅎ" to 'ㄶ', "ㄹㄱ" to 'ㄺ', "ㄹㅁ" to 'ㄻ', "ㄹㅂ" to 'ㄼ', "ㄹㅅ" to 'ㄽ', "ㄹㅌ" to 'ㄾ', "ㄹㅍ" to 'ㄿ', "ㄹㅎ" to 'ㅀ', "ㅂㅅ" to 'ㅄ')

    private var cho: Char? = null
    private var jung: Char? = null
    private var jong: Char? = null

    val composing: String get() = render()
    val isEmpty: Boolean get() = cho == null && jung == null && jong == null

    private fun render(): String {
        val c = cho
        val v = jung
        if (c == null && v == null) return ""
        if (c == null) return v.toString()
        if (v == null) return c.toString()
        val ci = initials.indexOf(c)
        val vi = medials.indexOf(v)
        val fi = jong?.let { finals.indexOf(it) } ?: 0
        if (ci < 0 || vi < 0) return "$c$v"
        return (0xAC00 + (ci * 21 + vi) * 28 + fi).toChar().toString()
    }

    /** Feeds a jamo; returns text that is finished and must be committed. */
    fun input(j: Char): String {
        val isVowel = medials.contains(j)
        if (isVowel) {
            val v = jung
            val f = jong
            when {
                cho != null && v == null -> { jung = j; return "" }
                v != null && f == null && vowelPairs.containsKey("$v$j") -> { jung = vowelPairs["$v$j"]; return "" }
                f != null -> {
                    // The final consonant (or its second half) starts the next syllable.
                    val split = finalPairs.entries.firstOrNull { it.value == f }
                    val moving: Char
                    if (split != null) {
                        jong = split.key[0]; moving = split.key[1]
                    } else {
                        jong = null; moving = f
                    }
                    val done = render()
                    cho = moving; jung = j; jong = null
                    return done
                }
                else -> {
                    val done = render()
                    cho = null; jung = j; jong = null
                    return done
                }
            }
        } else {
            val c = cho
            val v = jung
            val f = jong
            when {
                c == null && v == null -> { cho = j; return "" }
                c != null && v != null && f == null && finals.contains(j) && j != ' ' -> { jong = j; return "" }
                f != null && finalPairs.containsKey("$f$j") -> { jong = finalPairs["$f$j"]; return "" }
                else -> {
                    val done = render()
                    cho = j; jung = null; jong = null
                    return done
                }
            }
        }
    }

    /** Backspace inside the syllable; returns false when there was nothing to delete. */
    fun backspace(): Boolean {
        when {
            jong != null -> {
                val split = finalPairs.entries.firstOrNull { it.value == jong }
                jong = split?.key?.get(0)
            }
            jung != null -> {
                val split = vowelPairs.entries.firstOrNull { it.value == jung }
                jung = split?.key?.get(0)
            }
            cho != null -> cho = null
            else -> return false
        }
        return true
    }

    fun reset(): String {
        val done = render()
        cho = null; jung = null; jong = null
        return done
    }
}

/** Romaji to hiragana, typed as composing text (no kanji conversion). */
class KanaComposer {
    private var buffer = StringBuilder()
    private val kana = StringBuilder()

    val composing: String get() = kana.toString() + buffer
    val isEmpty: Boolean get() = buffer.isEmpty() && kana.isEmpty()

    fun input(c: Char): String {
        buffer.append(c.lowercaseChar())
        convert()
        return ""
    }

    private fun convert() {
        while (buffer.isNotEmpty()) {
            val b = buffer.toString()
            // Double consonant -> small tsu.
            if (b.length >= 2 && b[0] == b[1] && b[0] !in "aeioun") {
                kana.append('っ'); buffer.deleteCharAt(0); continue
            }
            if (b.length >= 2 && b[0] == 'n' && b[1] !in "aeiouy") {
                kana.append('ん'); buffer.deleteCharAt(0); continue
            }
            val match = (3 downTo 1).firstOrNull { len -> b.length >= len && table.containsKey(b.substring(0, len)) }
            if (match != null) {
                kana.append(table[b.substring(0, match)]); buffer.delete(0, match); continue
            }
            // Still a prefix of something: wait for more letters.
            if (table.keys.any { it.startsWith(b) }) return
            kana.append(buffer[0]); buffer.deleteCharAt(0)
        }
    }

    fun backspace(): Boolean {
        when {
            buffer.isNotEmpty() -> buffer.deleteCharAt(buffer.length - 1)
            kana.isNotEmpty() -> kana.deleteCharAt(kana.length - 1)
            else -> return false
        }
        return true
    }

    fun reset(): String {
        if (buffer.toString() == "n") { kana.append('ん'); buffer.clear() }
        val done = composing
        buffer.clear(); kana.clear()
        return done
    }

    private val table: Map<String, String> = buildMap {
        val rows = mapOf(
            "" to "あいうえお", "k" to "かきくけこ", "s" to "さしすせそ", "t" to "たちつてと", "n" to "なにぬねの",
            "h" to "はひふへほ", "m" to "まみむめも", "r" to "らりるれろ", "g" to "がぎぐげご", "z" to "ざじずぜぞ",
            "d" to "だぢづでど", "b" to "ばびぶべぼ", "p" to "ぱぴぷぺぽ",
        )
        val vowels = "aiueo"
        rows.forEach { (c, ks) -> vowels.forEachIndexed { i, v -> put(c + v, ks[i].toString()) } }
        putAll(mapOf("ya" to "や", "yu" to "ゆ", "yo" to "よ", "wa" to "わ", "wo" to "を", "nn" to "ん", "shi" to "し", "chi" to "ち", "tsu" to "つ", "fu" to "ふ", "ji" to "じ", "-" to "ー"))
        val yoon = mapOf("k" to "き", "s" to "し", "t" to "ち", "n" to "に", "h" to "ひ", "m" to "み", "r" to "り", "g" to "ぎ", "j" to "じ", "b" to "び", "p" to "ぴ")
        yoon.forEach { (c, base) -> put("${c}ya", base + "ゃ"); put("${c}yu", base + "ゅ"); put("${c}yo", base + "ょ") }
        putAll(mapOf("sha" to "しゃ", "shu" to "しゅ", "sho" to "しょ", "cha" to "ちゃ", "chu" to "ちゅ", "cho" to "ちょ", "ja" to "じゃ", "ju" to "じゅ", "jo" to "じょ"))
    }
}
