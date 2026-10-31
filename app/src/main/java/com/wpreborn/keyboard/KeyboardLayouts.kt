package com.wpreborn.keyboard

/** What a key does. Char keys output [Key.text]. */
enum class KeyType { Char, Shift, Backspace, Enter, Space, Symbols, Letters, SymbolsPage, Emoji, Language, Comma, Period, Action }

data class Key(
    val label: String,
    val type: KeyType = KeyType.Char,
    val width: Float = 1f,
    /** Text inserted; defaults to the label. */
    val text: String = label,
    /** Shifted label/text (for scripts without case, e.g. Korean double consonants). */
    val shifted: String? = null,
)

typealias KeyRow = List<Key>

/** How typed letters become text. */
enum class Composer { None, Hangul, Kana }

/**
 * A keyboard language: its letter rows, dictionary, writing direction and composer.
 * "All languages" in practice: dozens of languages through dedicated layouts, and every
 * Latin-script language through the long-press accent sets.
 */
data class KbLanguage(
    val code: String,
    val name: String,
    val rows: List<String>,
    val dictionary: String? = null,
    val rtl: Boolean = false,
    val composer: Composer = Composer.None,
    /** Shifted rows for scripts that use shift for other letters instead of upper case. */
    val shiftedRows: List<String>? = null,
    val alternates: Map<Char, String> = emptyMap(),
    /** Whether the script has upper case (auto-capitalisation, shift). */
    val cased: Boolean = true,
)

object KeyboardLayouts {

    private val QWERTY = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    private val QWERTZ = listOf("qwertzuiop", "asdfghjkl", "yxcvbnm")

    /** Accents for every Latin-script language, shown on long press. */
    val latinAlternates: Map<Char, String> = mapOf(
        'a' to "áàâäãåāąæă", 'c' to "çćčĉ", 'd' to "ďđð", 'e' to "éèêëēęěėə", 'g' to "ğĝģ",
        'h' to "ĥħ", 'i' to "íìîïīįı", 'j' to "ĵ", 'k' to "ķ", 'l' to "łľĺļ", 'n' to "ñńňņŋ",
        'o' to "óòôöõøōőœơ", 'r' to "řŕ", 's' to "ßśšşșŝ", 't' to "ťţțþ", 'u' to "úùûüūůűųư",
        'w' to "ŵ", 'y' to "ýÿŷ", 'z' to "žźż",
        '1' to "¹½⅓¼", '2' to "²⅔", '3' to "³¾", '0' to "°∅",
        '.' to ",?!;:…", '-' to "–—_", '\'' to "‘’‚‹›", '"' to "“”„«»", '$' to "€£¥₹₩¢₽₺", '?' to "¿", '!' to "¡",
    )

    private val vietnamese = mapOf(
        'a' to "áàảãạăắằẳẵặâấầẩẫậ", 'e' to "éèẻẽẹêếềểễệ", 'i' to "íìỉĩị",
        'o' to "óòỏõọôốồổỗộơớờởỡợ", 'u' to "úùủũụưứừửữự", 'y' to "ýỳỷỹỵ", 'd' to "đ",
    )

    val languages: List<KbLanguage> = listOf(
        KbLanguage("pt", "Português", listOf("qwertyuiop", "asdfghjklç", "zxcvbnm"), "pt"),
        KbLanguage("en", "English", QWERTY, "en"),
        KbLanguage("es", "Español", listOf("qwertyuiop", "asdfghjklñ", "zxcvbnm"), "es"),
        KbLanguage("fr", "Français", listOf("azertyuiop", "qsdfghjklm", "wxcvbn'"), "fr"),
        KbLanguage("de", "Deutsch", listOf("qwertzuiopü", "asdfghjklöä", "yxcvbnmß"), "de"),
        KbLanguage("it", "Italiano", QWERTY, "it"),
        KbLanguage("nl", "Nederlands", QWERTY, "nl"),
        KbLanguage("pl", "Polski", QWERTY, "pl"),
        KbLanguage("ro", "Română", QWERTY, "ro"),
        KbLanguage("cs", "Čeština", QWERTZ, "cs"),
        KbLanguage("hu", "Magyar", listOf("qwertzuiopő", "asdfghjkléá", "yxcvbnmöü"), "hu"),
        KbLanguage("sv", "Svenska", listOf("qwertyuiopå", "asdfghjklöä", "zxcvbnm"), "sv"),
        KbLanguage("da", "Dansk", listOf("qwertyuiopå", "asdfghjklæø", "zxcvbnm")),
        KbLanguage("nb", "Norsk", listOf("qwertyuiopå", "asdfghjkløæ", "zxcvbnm")),
        KbLanguage("fi", "Suomi", listOf("qwertyuiopå", "asdfghjklöä", "zxcvbnm")),
        KbLanguage("tr", "Türkçe", listOf("qwertyuıopğü", "asdfghjklşi", "zxcvbnmöç"), "tr"),
        KbLanguage("id", "Indonesia", QWERTY, "id"),
        KbLanguage("ms", "Melayu", QWERTY),
        KbLanguage("tl", "Filipino", QWERTY),
        KbLanguage("vi", "Tiếng Việt", QWERTY, "vi", alternates = vietnamese),
        KbLanguage("ca", "Català", listOf("qwertyuiop", "asdfghjklç", "zxcvbnm")),
        KbLanguage("gl", "Galego", listOf("qwertyuiop", "asdfghjklñ", "zxcvbnm")),
        KbLanguage("eu", "Euskara", QWERTY),
        KbLanguage("hr", "Hrvatski", listOf("qwertzuiopš", "asdfghjklčć", "yxcvbnmž")),
        KbLanguage("sk", "Slovenčina", QWERTZ),
        KbLanguage("sl", "Slovenščina", listOf("qwertzuiopš", "asdfghjklčž", "yxcvbnm")),
        KbLanguage("lt", "Lietuvių", QWERTY),
        KbLanguage("lv", "Latviešu", QWERTY),
        KbLanguage("et", "Eesti", listOf("qwertyuiopüõ", "asdfghjklöä", "zxcvbnm")),
        KbLanguage("sq", "Shqip", QWERTZ),
        KbLanguage("af", "Afrikaans", QWERTY),
        KbLanguage("sw", "Kiswahili", QWERTY),
        KbLanguage("ha", "Hausa", QWERTY),
        KbLanguage("yo", "Yorùbá", QWERTY),
        KbLanguage("ru", "Русский", listOf("йцукенгшщзх", "фывапролджэ", "ячсмитьбю"), "ru", alternates = mapOf('е' to "ё", 'ь' to "ъ")),
        KbLanguage("uk", "Українська", listOf("йцукенгшщзхї", "фівапролджє", "ячсмитьбю"), "uk", alternates = mapOf('г' to "ґ", 'і' to "ы", 'ь' to "ъ")),
        KbLanguage("be", "Беларуская", listOf("йцукенгшўзх", "фывапролджэ", "ячсмітьбю")),
        KbLanguage("bg", "Български", listOf("явертъуиопчш", "асдфгхйклщ", "зьцжбнмю")),
        KbLanguage("sr", "Српски", listOf("љњертзуиопш", "асдфгхјклчћ", "џцвбнмђж")),
        KbLanguage("kk", "Қазақша", listOf("йцукенгшщзх", "фывапролджэ", "ячсмитьбю"), alternates = mapOf('а' to "ә", 'о' to "ө", 'у' to "ұү", 'к' to "қ", 'н' to "ң", 'г' to "ғ", 'и' to "і", 'х' to "һ")),
        KbLanguage("el", "Ελληνικά", listOf("ςερτυθιοπ", "ασδφγηξκλ", "ζχψωβνμ"), "el", alternates = mapOf('α' to "ά", 'ε' to "έ", 'η' to "ή", 'ι' to "ίϊΐ", 'ο' to "ό", 'υ' to "ύϋΰ", 'ω' to "ώ")),
        KbLanguage("ar", "العربية", listOf("ضصثقفغعهخحج", "شسيبلاتنمكط", "ئءؤرىةوزظد"), "ar", rtl = true, cased = false, alternates = mapOf('ا' to "أإآٱ", 'ي' to "ى", 'ه' to "ة", 'و' to "ؤ")),
        KbLanguage("fa", "فارسی", listOf("ضصثقفغعهخحج", "شسیبلاتنمکگ", "ظطزرذدپوچ"), rtl = true, cased = false, alternates = mapOf('ا' to "آأ", 'ز' to "ژ")),
        KbLanguage("ur", "اردو", listOf("طصھدٹپتبجح", "مورنلہاکیس", "قفےشغعڈچژز"), rtl = true, cased = false),
        KbLanguage("he", "עברית", listOf("קראטוןםפ", "שדגכעיחלךף", "זסבהנמצתץ"), "he", rtl = true, cased = false),
        KbLanguage("hi", "हिन्दी", listOf("ौैाीूबहगदजड", "ोेृिुपरकतचट", "ंमनवलसयशषण"), cased = false,
            shiftedRows = listOf("औऐआईऊभङघधझढ", "ओएऋइउफ़ऱखथछठ", "ँःऩ़ळऴयशक्षञ"),
            alternates = mapOf('क' to "क़क्ष", 'ज' to "ज़ज्ञ", 'र' to "ऋ्र", 'द' to "द्ध", 'न' to "ञ", '्' to "॥।")),
        KbLanguage("bn", "বাংলা", listOf("ৌৈাীূবহগদজড", "োেৃিুপরকতচট", "ংমনবলসযশষণ"), cased = false),
        KbLanguage("ta", "தமிழ்", listOf("ஞறநசவலரைடமனக", "யளனகபாிீுூெே", "அஇஉஎஒதஙழணஷ"), cased = false),
        KbLanguage("th", "ไทย", listOf("ๆไำพะัีรนยบล", "ฟหกดเ้่าสวง", "ผปแอิืทมใฝ"), cased = false,
            shiftedRows = listOf("๐\"ฎฑธํ๊ณฯญฐ,", "ฤฆฏโฌ็๋ษศซ.", "()ฉฮฺ์?ฒฬฦ")),
        KbLanguage("ko", "한국어", listOf("ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔ", "ㅁㄴㅇㄹㅎㅗㅓㅏㅣ", "ㅋㅌㅊㅍㅠㅜㅡ"), cased = false,
            composer = Composer.Hangul, shiftedRows = listOf("ㅃㅉㄸㄲㅆㅛㅕㅑㅒㅖ", "ㅁㄴㅇㄹㅎㅗㅓㅏㅣ", "ㅋㅌㅊㅍㅠㅜㅡ")),
        KbLanguage("ja", "日本語 (ローマ字)", QWERTY, cased = false, composer = Composer.Kana),
        KbLanguage("hy", "Հայերեն", listOf("էթփձջրչճժծ", "քոեռտըւիօպ", "ասդֆգհյկլ", "զղցվբնմ"), cased = true),
        KbLanguage("ka", "ქართული", listOf("ქწერტყუიოპ", "ასდფგჰჯკლ", "ზხცვბნმ"), cased = false, alternates = mapOf('წ' to "ჭ", 'რ' to "ღ", 'ტ' to "თ", 'ს' to "შ", 'ჯ' to "ჟ", 'ზ' to "ძ", 'ც' to "ჩ")),
    )

    fun byCode(code: String) = languages.firstOrNull { it.code == code } ?: languages.first()

    /** Alternates for a key: language specific first, then the generic Latin set. */
    fun alternatesFor(lang: KbLanguage, c: Char): String {
        val lower = c.lowercaseChar()
        val specific = lang.alternates[lower].orEmpty()
        // Vietnamese brings its own complete vowel sets; everything else also gets the Latin accents.
        val latin = if (lang.code == "vi" && specific.isNotEmpty()) "" else latinAlternates[lower].orEmpty()
        val all = (specific + latin).toList().distinct().joinToString("")
        return if (c.isUpperCase()) all.uppercase() else all
    }

    /** The letter layout for a language, with shift/backspace and the bottom row. */
    fun letters(lang: KbLanguage, shifted: Boolean, numberRow: Boolean, multiLanguage: Boolean, email: Boolean): List<KeyRow> {
        val source = if (shifted && lang.shiftedRows != null) lang.shiftedRows else lang.rows
        val rows = ArrayList<KeyRow>()
        if (numberRow) rows += "1234567890".map { Key(it.toString()) }
        source.forEachIndexed { i, r ->
            val keys = r.map { ch ->
                val label = if (shifted && lang.cased && lang.shiftedRows == null) ch.uppercase() else ch.toString()
                Key(label)
            }.toMutableList()
            if (i == source.lastIndex) {
                keys.add(0, Key("shift", KeyType.Shift, 1.4f))
                keys.add(Key("del", KeyType.Backspace, 1.4f))
            }
            rows += keys
        }
        rows += bottomRow(lettersPage = true, multiLanguage = multiLanguage, email = email)
        return rows
    }

    fun bottomRow(lettersPage: Boolean, multiLanguage: Boolean, email: Boolean): KeyRow = buildList {
        add(Key(if (lettersPage) "&123" else "abc", if (lettersPage) KeyType.Symbols else KeyType.Letters, 1.5f))
        if (email) add(Key("@")) else add(Key(",", KeyType.Comma))
        if (multiLanguage) add(Key("lang", KeyType.Language))
        add(Key("emoji", KeyType.Emoji))
        add(Key("space", KeyType.Space, if (multiLanguage) 3.5f else 4.5f, text = " "))
        add(Key(".", KeyType.Period))
        add(Key("enter", KeyType.Enter, 1.5f))
    }

    fun symbols(page: Int, multiLanguage: Boolean): List<KeyRow> {
        val pages = listOf(
            listOf("1234567890", "@#$%&*-+()", "!\"':;/?"),
            listOf("~`|•√π÷×¶∆", "£€¥^°={}[]", "_<>\\©®™"),
        )
        val p = pages[page.coerceIn(0, 1)]
        return listOf(
            p[0].map { Key(it.toString()) },
            p[1].map { Key(it.toString()) },
            listOf(Key(if (page == 0) "1/2" else "2/2", KeyType.SymbolsPage, 1.4f)) + p[2].map { Key(it.toString()) } + Key("del", KeyType.Backspace, 1.4f),
            bottomRow(lettersPage = false, multiLanguage = multiLanguage, email = false),
        )
    }

    /** Phone/number pad. */
    fun numberPad(phone: Boolean): List<KeyRow> = listOf(
        listOf(Key("1"), Key("2"), Key("3"), Key(if (phone) "+" else "-")),
        listOf(Key("4"), Key("5"), Key("6"), Key(if (phone) "(" else ",")),
        listOf(Key("7"), Key("8"), Key("9"), Key("del", KeyType.Backspace)),
        listOf(Key(if (phone) "*" else "."), Key("0"), Key(if (phone) "#" else " ", text = if (phone) "#" else " "), Key("enter", KeyType.Enter)),
    )
}
