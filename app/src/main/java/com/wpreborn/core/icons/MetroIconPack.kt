package com.wpreborn.core.icons

import android.content.pm.ApplicationInfo
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * ZUNEX's own Metro icon pack: flat white glyphs for well-known Android apps, chosen by
 * package name, by keywords in the app name, and finally by the app category. The glyphs
 * describe what an app does (mail, chat, video...) instead of copying brand logos, the way
 * Windows Phone drew its own tiles.
 */
object MetroIconPack {

    /** Returns the Metro glyph for an app, or null when the pack has nothing for it. */
    fun glyphFor(packageName: String, label: String, category: Int): ImageVector? {
        val pkg = packageName.lowercase()
        val name = label.lowercase()
        byPackage.entries.firstOrNull { (prefix, _) -> pkg == prefix || pkg.startsWith("$prefix.") }?.let { return it.value }
        byKeyword.firstOrNull { (words, _) -> words.any { pkg.contains(it) || name.contains(it) } }?.let { return it.second }
        return when (category) {
            ApplicationInfo.CATEGORY_GAME -> Glyphs.Gamepad
            ApplicationInfo.CATEGORY_AUDIO -> WpIcons.MusicNote
            ApplicationInfo.CATEGORY_VIDEO -> Glyphs.Video
            ApplicationInfo.CATEGORY_IMAGE -> WpIcons.Photos
            ApplicationInfo.CATEGORY_SOCIAL -> Glyphs.Social
            ApplicationInfo.CATEGORY_NEWS -> Glyphs.News
            ApplicationInfo.CATEGORY_MAPS -> Glyphs.MapPin
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> Glyphs.Document
            else -> null
        }
    }

    private val byPackage: Map<String, ImageVector> = mapOf(
        // Browsers: the Internet Explorer slot.
        "com.android.chrome" to WpIcons.Globe, "org.mozilla.firefox" to WpIcons.Globe, "com.microsoft.emmx" to WpIcons.Globe,
        "com.opera.browser" to WpIcons.Globe, "com.brave.browser" to WpIcons.Globe, "com.sec.android.app.sbrowser" to WpIcons.Globe,
        "com.duckduckgo.mobile.android" to WpIcons.Globe, "com.android.browser" to WpIcons.Globe,
        // Mail
        "com.google.android.gm" to WpIcons.Mail, "com.microsoft.office.outlook" to WpIcons.Mail, "com.yahoo.mobile.client.android.mail" to WpIcons.Mail,
        "com.samsung.android.email.provider" to WpIcons.Mail, "com.android.email" to WpIcons.Mail,
        // Chat
        "com.whatsapp" to Glyphs.Chat, "com.whatsapp.w4b" to Glyphs.Chat, "org.telegram.messenger" to Glyphs.Paperplane,
        "org.thoughtcrime.securesms" to Glyphs.Chat, "com.facebook.orca" to Glyphs.Chat, "com.discord" to Glyphs.Chat,
        "com.google.android.apps.messaging" to WpIcons.Messaging, "com.samsung.android.messaging" to WpIcons.Messaging,
        "jp.naver.line.android" to Glyphs.Chat, "com.viber.voip" to Glyphs.Chat, "com.skype.raider" to Glyphs.Chat,
        // Social
        "com.instagram.android" to Glyphs.PhotoSquare, "com.facebook.katana" to Glyphs.Social, "com.facebook.lite" to Glyphs.Social,
        "com.twitter.android" to Glyphs.Bird, "com.linkedin.android" to Glyphs.Briefcase, "com.reddit.frontpage" to Glyphs.Social,
        "com.pinterest" to WpIcons.Pin, "com.snapchat.android" to Glyphs.Ghost, "com.zhiliaoapp.musically" to WpIcons.MusicNote,
        "com.ss.android.ugc.trill" to WpIcons.MusicNote, "com.instagram.barcelona" to Glyphs.Social,
        // Video
        "com.google.android.youtube" to Glyphs.Video, "com.netflix.mediaclient" to Glyphs.Video, "com.amazon.avod.thirdpartyclient" to Glyphs.Video,
        "com.disney.disneyplus" to Glyphs.Video, "com.globo.globotv" to Glyphs.Video, "com.hbo.hbonow" to Glyphs.Video,
        "com.google.android.apps.youtube.music" to WpIcons.MusicNote, "tv.twitch.android.app" to Glyphs.Video,
        // Music
        "com.spotify.music" to WpIcons.Music, "deezer.android.app" to WpIcons.Music, "com.amazon.mp3" to WpIcons.Music,
        "com.apple.android.music" to WpIcons.Music, "com.soundcloud.android" to WpIcons.Music,
        // Google & system
        "com.android.vending" to WpIcons.Store, "com.google.android.apps.maps" to Glyphs.MapPin, "com.waze" to Glyphs.Car,
        "com.google.android.apps.photos" to WpIcons.Photos, "com.google.android.apps.docs" to Glyphs.Cloud,
        "com.google.android.keep" to Glyphs.Note, "com.google.android.calendar" to WpIcons.Calendar,
        "com.google.android.contacts" to WpIcons.People, "com.google.android.dialer" to WpIcons.Phone,
        "com.google.android.deskclock" to WpIcons.Alarm, "com.google.android.calculator" to Glyphs.Calculator,
        "com.google.android.apps.translate" to Glyphs.Translate, "com.google.android.googlequicksearchbox" to WpIcons.Search,
        "com.google.android.apps.nbu.files" to WpIcons.Folder, "com.google.android.documentsui" to WpIcons.Folder,
        "com.google.android.apps.fitness" to Glyphs.Heart, "com.google.android.apps.walletnfcrel" to Glyphs.Wallet,
        "com.google.android.apps.tachyon" to Glyphs.VideoCall, "com.google.android.apps.meetings" to Glyphs.VideoCall,
        "com.google.android.apps.podcasts" to Glyphs.Microphone, "com.google.android.apps.googleassistant" to Glyphs.Microphone,
        "com.android.settings" to WpIcons.Settings, "com.android.camera" to WpIcons.Camera, "com.android.camera2" to WpIcons.Camera,
        "com.google.android.GoogleCamera" to WpIcons.Camera, "com.sec.android.app.camera" to WpIcons.Camera,
        "com.android.calculator2" to Glyphs.Calculator, "com.sec.android.app.popupcalculator" to Glyphs.Calculator,
        "com.android.deskclock" to WpIcons.Alarm, "com.sec.android.app.clockpackage" to WpIcons.Alarm,
        "com.android.contacts" to WpIcons.People, "com.android.dialer" to WpIcons.Phone, "com.samsung.android.dialer" to WpIcons.Phone,
        "com.android.gallery3d" to WpIcons.Photos, "com.sec.android.gallery3d" to WpIcons.Photos, "com.miui.gallery" to WpIcons.Photos,
        "com.android.calendar" to WpIcons.Calendar, "com.samsung.android.calendar" to WpIcons.Calendar,
        "com.sec.android.app.myfiles" to WpIcons.Folder, "com.android.fmradio" to Glyphs.Radio,
        // Microsoft
        "com.microsoft.office.word" to Glyphs.Document, "com.microsoft.office.excel" to Glyphs.Grid,
        "com.microsoft.office.powerpoint" to Glyphs.Slides, "com.microsoft.office.officehubrow" to Glyphs.Document,
        "com.microsoft.skydrive" to Glyphs.Cloud, "com.microsoft.teams" to Glyphs.VideoCall, "com.microsoft.todos" to WpIcons.Check,
        "com.microsoft.bing" to WpIcons.Search, "com.microsoft.copilot" to Glyphs.Sparkle, "com.microsoft.xboxone.smartglass" to Glyphs.Gamepad,
        "com.microsoft.xcloud" to Glyphs.Gamepad, "com.microsoft.translator" to Glyphs.Translate,
        // Brazil essentials
        "com.nu.production" to Glyphs.Wallet, "br.com.intermedium" to Glyphs.Wallet, "com.itau" to Glyphs.Bank,
        "br.com.bb.android" to Glyphs.Bank, "com.bradesco" to Glyphs.Bank, "br.com.gabba.caixa" to Glyphs.Bank,
        "br.gov.caixa.fgts.trabalhador" to Glyphs.Bank, "br.com.caixa.caixatem" to Glyphs.Bank, "com.mercadopago.wallet" to Glyphs.Wallet,
        "com.picpay" to Glyphs.Wallet, "com.mercadolibre" to Glyphs.Bag, "com.shopee.br" to Glyphs.Bag, "com.amazon.mShop.android.shopping" to Glyphs.Bag,
        "br.com.brainweb.ifood" to Glyphs.Food, "com.ubercab" to Glyphs.Car, "com.taxis99" to Glyphs.Car, "com.ubercab.eats" to Glyphs.Food,
        "br.gov.meugovbr" to Glyphs.Shield, "br.gov.serpro.cnhe" to Glyphs.Car,
        // TVs and remotes
        "com.hisense.vidaa.remote" to Glyphs.Tv, "com.samsung.android.oneconnect" to Glyphs.Home, "com.google.android.apps.chromecast.app" to Glyphs.Tv,
    )

    private val byKeyword: List<Pair<List<String>, ImageVector>> = listOf(
        listOf("browser", "navegador") to WpIcons.Globe,
        listOf("mail", "email") to WpIcons.Mail,
        listOf("camera", "câmera", "camara") to WpIcons.Camera,
        listOf("gallery", "galeria", "photo", "foto") to WpIcons.Photos,
        listOf("music", "música", "musica", "radio", "rádio") to WpIcons.Music,
        listOf("video", "vídeo", "player", "tv", "stream") to Glyphs.Video,
        listOf("chat", "messenger", "message", "mensag") to Glyphs.Chat,
        listOf("map", "mapa", "gps", "nav") to Glyphs.MapPin,
        listOf("weather", "clima", "tempo") to Glyphs.Weather,
        listOf("news", "notícia", "noticia", "jornal") to Glyphs.News,
        listOf("bank", "banco", "pay", "wallet", "carteira") to Glyphs.Wallet,
        listOf("shop", "store", "loja", "market", "compra") to Glyphs.Bag,
        listOf("food", "delivery", "comida") to Glyphs.Food,
        listOf("taxi", "ride", "car", "carro", "uber") to Glyphs.Car,
        listOf("note", "nota", "memo") to Glyphs.Note,
        listOf("file", "arquivo", "manager") to WpIcons.Folder,
        listOf("clock", "relógio", "relogio", "alarm") to WpIcons.Alarm,
        listOf("calc") to Glyphs.Calculator,
        listOf("health", "saúde", "saude", "fit") to Glyphs.Heart,
        listOf("book", "livro", "read", "kindle") to Glyphs.Book,
        listOf("game", "jogo") to Glyphs.Gamepad,
        listOf("remote", "controle") to Glyphs.Tv,
        listOf("vpn", "security", "segur", "gov") to Glyphs.Shield,
    )
}

/** Extra glyphs used by the icon pack (48x48 grid, like [WpIcons]). */
object Glyphs {
    private class Scope(val builder: ImageVector.Builder) {
        fun fill(evenOdd: Boolean = false, block: PathBuilder.() -> Unit) {
            builder.path(
                fill = SolidColor(Color.White),
                pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
                pathBuilder = block,
            )
        }

        fun stroke(width: Float = 3f, block: PathBuilder.() -> Unit) {
            builder.path(stroke = SolidColor(Color.White), strokeLineWidth = width, strokeLineCap = StrokeCap.Butt, pathBuilder = block)
        }
    }

    private fun icon(name: String, block: Scope.() -> Unit): ImageVector {
        val b = ImageVector.Builder(name, 48.dp, 48.dp, 48f, 48f)
        Scope(b).block()
        return b.build()
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, false, true, cx + r, cy)
        arcTo(r, r, 0f, false, true, cx - r, cy)
        close()
    }

    private fun PathBuilder.rect(x: Float, y: Float, w: Float, h: Float) {
        moveTo(x, y); lineTo(x + w, y); lineTo(x + w, y + h); lineTo(x, y + h); close()
    }

    val Chat = icon("chat") {
        fill(evenOdd = true) {
            moveTo(24f, 7f); curveTo(34.5f, 7f, 42f, 14f, 42f, 22.5f); curveTo(42f, 31f, 34.5f, 38f, 24f, 38f)
            curveTo(21.5f, 38f, 19.2f, 37.6f, 17f, 36.9f); lineTo(8f, 41f); lineTo(10.5f, 33f)
            curveTo(7.7f, 30.3f, 6f, 26.6f, 6f, 22.5f); curveTo(6f, 14f, 13.5f, 7f, 24f, 7f); close()
            circle(16f, 22.5f, 2.4f); circle(24f, 22.5f, 2.4f); circle(32f, 22.5f, 2.4f)
        }
    }

    val Paperplane = icon("paperplane") {
        fill { moveTo(5f, 23f); lineTo(43f, 8f); lineTo(36f, 41f); lineTo(25f, 32f); lineTo(19f, 39f); lineTo(19f, 29f); lineTo(36f, 14f); lineTo(16f, 27f); close() }
    }

    val Social = icon("social") {
        fill { circle(18f, 15f, 6.5f) }
        fill { moveTo(5f, 37f); curveTo(5f, 28f, 11f, 24f, 18f, 24f); curveTo(25f, 24f, 31f, 28f, 31f, 37f); close() }
        fill { moveTo(30f, 6f); lineTo(44f, 6f); lineTo(44f, 18f); lineTo(38f, 18f); lineTo(34f, 22f); lineTo(34f, 18f); lineTo(30f, 18f); close() }
    }

    val PhotoSquare = icon("photo_square") {
        fill(evenOdd = true) {
            moveTo(14f, 6f); lineTo(34f, 6f); curveTo(38.4f, 6f, 42f, 9.6f, 42f, 14f); lineTo(42f, 34f); curveTo(42f, 38.4f, 38.4f, 42f, 34f, 42f)
            lineTo(14f, 42f); curveTo(9.6f, 42f, 6f, 38.4f, 6f, 34f); lineTo(6f, 14f); curveTo(6f, 9.6f, 9.6f, 6f, 14f, 6f); close()
            moveTo(14f, 9.5f); curveTo(11.5f, 9.5f, 9.5f, 11.5f, 9.5f, 14f); lineTo(9.5f, 34f); curveTo(9.5f, 36.5f, 11.5f, 38.5f, 14f, 38.5f)
            lineTo(34f, 38.5f); curveTo(36.5f, 38.5f, 38.5f, 36.5f, 38.5f, 34f); lineTo(38.5f, 14f); curveTo(38.5f, 11.5f, 36.5f, 9.5f, 34f, 9.5f); close()
        }
        stroke(3.5f) { circle(24f, 24f, 7.5f) }
        fill { circle(34f, 14f, 2.2f) }
    }

    val Bird = icon("bird") {
        fill {
            moveTo(42f, 12f); curveTo(40.5f, 12.7f, 39f, 13.1f, 37.4f, 13.3f); curveTo(39f, 12.3f, 40.3f, 10.8f, 40.9f, 9f)
            curveTo(39.4f, 9.9f, 37.7f, 10.5f, 35.9f, 10.9f); curveTo(32.9f, 7.7f, 27.7f, 7.6f, 24.5f, 10.7f)
            curveTo(22.5f, 12.6f, 21.6f, 15.5f, 22.2f, 18.3f); curveTo(16f, 18f, 10.3f, 15.1f, 6.4f, 10.3f)
            curveTo(4.4f, 13.8f, 5.4f, 18.3f, 8.8f, 20.6f); curveTo(7.6f, 20.6f, 6.4f, 20.2f, 5.3f, 19.6f)
            curveTo(5.3f, 23.3f, 7.9f, 26.4f, 11.5f, 27.2f); curveTo(10.4f, 27.5f, 9.2f, 27.5f, 8.1f, 27.3f)
            curveTo(9.1f, 30.4f, 12f, 32.6f, 15.3f, 32.7f); curveTo(12.1f, 35.2f, 8f, 36.3f, 4f, 35.8f)
            curveTo(23.5f, 48f, 43.6f, 35.2f, 39.8f, 15.2f); curveTo(40.6f, 14.3f, 41.5f, 13.2f, 42f, 12f); close()
        }
    }

    val Briefcase = icon("briefcase") {
        fill(evenOdd = true) {
            rect(6f, 15f, 36f, 25f)
            moveTo(18f, 15f); lineTo(18f, 10f); lineTo(30f, 10f); lineTo(30f, 15f); lineTo(27f, 15f); lineTo(27f, 13f); lineTo(21f, 13f); lineTo(21f, 15f); close()
        }
    }

    val Ghost = icon("ghost") {
        fill(evenOdd = true) {
            moveTo(24f, 6f); curveTo(31.7f, 6f, 36f, 11.5f, 36f, 18f); lineTo(36f, 24f); lineTo(41f, 27f); lineTo(36f, 30f)
            curveTo(38f, 34f, 41f, 35f, 42f, 36f); curveTo(38f, 38f, 34f, 37f, 31f, 40f); curveTo(28f, 42f, 20f, 42f, 17f, 40f)
            curveTo(14f, 37f, 10f, 38f, 6f, 36f); curveTo(7f, 35f, 10f, 34f, 12f, 30f); lineTo(7f, 27f); lineTo(12f, 24f)
            lineTo(12f, 18f); curveTo(12f, 11.5f, 16.3f, 6f, 24f, 6f); close()
        }
    }

    val Video = icon("video") {
        fill(evenOdd = true) {
            moveTo(10f, 10f); lineTo(38f, 10f); curveTo(41.3f, 10f, 44f, 12.7f, 44f, 16f); lineTo(44f, 32f); curveTo(44f, 35.3f, 41.3f, 38f, 38f, 38f)
            lineTo(10f, 38f); curveTo(6.7f, 38f, 4f, 35.3f, 4f, 32f); lineTo(4f, 16f); curveTo(4f, 12.7f, 6.7f, 10f, 10f, 10f); close()
            moveTo(20f, 17f); lineTo(20f, 31f); lineTo(32f, 24f); close()
        }
    }

    val VideoCall = icon("video_call") {
        fill { rect(5f, 13f, 27f, 22f) }
        fill { moveTo(34f, 21f); lineTo(43f, 15f); lineTo(43f, 33f); lineTo(34f, 27f); close() }
    }

    val MapPin = icon("map_pin") {
        fill(evenOdd = true) {
            moveTo(24f, 4f); curveTo(31.7f, 4f, 37f, 9.8f, 37f, 17f); curveTo(37f, 26f, 24f, 44f, 24f, 44f)
            curveTo(24f, 44f, 11f, 26f, 11f, 17f); curveTo(11f, 9.8f, 16.3f, 4f, 24f, 4f); close()
            circle(24f, 17f, 5f)
        }
    }

    val Car = icon("car") {
        fill(evenOdd = true) {
            moveTo(12f, 13f); lineTo(36f, 13f); lineTo(40f, 23f); lineTo(43f, 23f); lineTo(43f, 37f); lineTo(38f, 37f); lineTo(38f, 41f)
            lineTo(33f, 41f); lineTo(33f, 37f); lineTo(15f, 37f); lineTo(15f, 41f); lineTo(10f, 41f); lineTo(10f, 37f); lineTo(5f, 37f)
            lineTo(5f, 23f); lineTo(8f, 23f); close()
            moveTo(14f, 17f); lineTo(34f, 17f); lineTo(36.5f, 23f); lineTo(11.5f, 23f); close()
            circle(12.5f, 30f, 2.6f); circle(35.5f, 30f, 2.6f)
        }
    }

    val Cloud = icon("cloud") {
        fill {
            moveTo(13f, 37f); curveTo(8f, 37f, 4f, 33f, 4f, 28f); curveTo(4f, 23.4f, 7.4f, 19.6f, 11.8f, 19.1f)
            curveTo(13.2f, 13.2f, 18.5f, 9f, 24.6f, 9f); curveTo(31.3f, 9f, 36.8f, 14.1f, 37.4f, 20.7f)
            curveTo(41.2f, 21.6f, 44f, 25f, 44f, 29f); curveTo(44f, 33.4f, 40.4f, 37f, 36f, 37f); close()
        }
    }

    val Weather = icon("weather") {
        fill { circle(17f, 17f, 7f) }
        fill {
            moveTo(18f, 40f); curveTo(14.1f, 40f, 11f, 36.9f, 11f, 33f); curveTo(11f, 29.4f, 13.7f, 26.4f, 17.2f, 26f)
            curveTo(18.3f, 21.4f, 22.4f, 18f, 27.3f, 18f); curveTo(32.5f, 18f, 36.8f, 22f, 37.3f, 27.1f)
            curveTo(40.2f, 27.8f, 42.5f, 30.4f, 42.5f, 33.5f); curveTo(42.5f, 37.1f, 39.6f, 40f, 36f, 40f); close()
        }
    }

    val News = icon("news") {
        fill(evenOdd = true) {
            rect(6f, 8f, 36f, 32f)
            rect(10f, 12f, 13f, 11f); rect(26f, 12f, 12f, 2.5f); rect(26f, 17f, 12f, 2.5f); rect(26f, 22f, 12f, 2.5f)
            rect(10f, 27f, 28f, 2.5f); rect(10f, 32f, 28f, 2.5f)
        }
    }

    val Note = icon("note") {
        fill(evenOdd = true) {
            moveTo(8f, 6f); lineTo(40f, 6f); lineTo(40f, 30f); lineTo(28f, 42f); lineTo(8f, 42f); close()
            rect(13f, 13f, 22f, 2.5f); rect(13f, 19f, 22f, 2.5f); rect(13f, 25f, 14f, 2.5f)
        }
    }

    val Document = icon("document") {
        fill(evenOdd = true) {
            moveTo(10f, 4f); lineTo(30f, 4f); lineTo(40f, 14f); lineTo(40f, 44f); lineTo(10f, 44f); close()
            rect(15f, 20f, 20f, 2.5f); rect(15f, 26f, 20f, 2.5f); rect(15f, 32f, 20f, 2.5f)
        }
    }

    val Grid = icon("grid") {
        fill(evenOdd = true) {
            rect(6f, 8f, 36f, 32f)
            rect(9f, 15f, 9f, 6f); rect(20f, 15f, 9f, 6f); rect(31f, 15f, 8f, 6f)
            rect(9f, 23f, 9f, 6f); rect(20f, 23f, 9f, 6f); rect(31f, 23f, 8f, 6f)
            rect(9f, 31f, 9f, 6f); rect(20f, 31f, 9f, 6f); rect(31f, 31f, 8f, 6f)
        }
    }

    val Slides = icon("slides") {
        fill(evenOdd = true) {
            rect(5f, 8f, 38f, 26f)
            rect(9f, 12f, 30f, 18f)
        }
        fill { rect(22.5f, 34f, 3f, 7f); rect(15f, 40f, 18f, 3f) }
        fill { rect(13f, 16f, 22f, 3f); rect(13f, 22f, 14f, 3f) }
    }

    val Calculator = icon("calculator") {
        fill(evenOdd = true) {
            rect(9f, 4f, 30f, 40f)
            rect(13f, 8f, 22f, 9f)
            for (r in 0 until 4) for (c in 0 until 3) rect(13f + c * 8f, 20f + r * 6f, 6f, 4f)
        }
    }

    val Gamepad = icon("gamepad") {
        fill(evenOdd = true) {
            moveTo(14f, 14f); lineTo(34f, 14f); curveTo(40f, 14f, 44f, 19f, 44f, 26f); curveTo(44f, 33f, 41f, 37f, 37f, 37f)
            curveTo(33f, 37f, 31f, 32f, 28f, 32f); lineTo(20f, 32f); curveTo(17f, 32f, 15f, 37f, 11f, 37f)
            curveTo(7f, 37f, 4f, 33f, 4f, 26f); curveTo(4f, 19f, 8f, 14f, 14f, 14f); close()
            rect(11f, 22.5f, 9f, 3f); rect(14f, 19.5f, 3f, 9f)
            circle(32f, 21f, 2f); circle(36f, 25f, 2f); circle(32f, 29f, 2f); circle(28f, 25f, 2f)
        }
    }

    val Heart = icon("heart") {
        fill {
            moveTo(24f, 41f); curveTo(10f, 31f, 4f, 24f, 4f, 16.5f); curveTo(4f, 10.6f, 8.6f, 6f, 14.5f, 6f)
            curveTo(18.5f, 6f, 22f, 8.2f, 24f, 11.5f); curveTo(26f, 8.2f, 29.5f, 6f, 33.5f, 6f)
            curveTo(39.4f, 6f, 44f, 10.6f, 44f, 16.5f); curveTo(44f, 24f, 38f, 31f, 24f, 41f); close()
        }
    }

    val Wallet = icon("wallet") {
        fill(evenOdd = true) {
            rect(5f, 11f, 38f, 28f)
            rect(30f, 21f, 13f, 9f)
            circle(35f, 25.5f, 2f)
        }
        fill { moveTo(9f, 11f); lineTo(33f, 5f); lineTo(35f, 11f); close() }
    }

    val Bank = icon("bank") {
        fill { moveTo(24f, 4f); lineTo(44f, 15f); lineTo(44f, 18f); lineTo(4f, 18f); lineTo(4f, 15f); close() }
        fill { rect(8f, 21f, 5f, 14f); rect(17f, 21f, 5f, 14f); rect(26f, 21f, 5f, 14f); rect(35f, 21f, 5f, 14f); rect(4f, 38f, 40f, 4f) }
    }

    val Bag = icon("bag") {
        fill { rect(8f, 16f, 32f, 26f) }
        stroke(3f) { moveTo(17f, 22f); lineTo(17f, 13f); arcTo(7f, 7f, 0f, false, true, 31f, 13f); lineTo(31f, 22f) }
    }

    val Food = icon("food") {
        fill { rect(10f, 6f, 2.5f, 12f); rect(15f, 6f, 2.5f, 12f); rect(20f, 6f, 2.5f, 12f) }
        fill { moveTo(10f, 16f); lineTo(22.5f, 16f); lineTo(22.5f, 18f); curveTo(22.5f, 21f, 20f, 23f, 18f, 23f); lineTo(18f, 42f); lineTo(14.5f, 42f); lineTo(14.5f, 23f); curveTo(12.5f, 23f, 10f, 21f, 10f, 18f); close() }
        fill { moveTo(33f, 6f); curveTo(37f, 6f, 39f, 12f, 39f, 20f); lineTo(39f, 26f); lineTo(35f, 26f); lineTo(35f, 42f); lineTo(31.5f, 42f); lineTo(31.5f, 8f); curveTo(31.5f, 7f, 32f, 6f, 33f, 6f); close() }
    }

    val Translate = icon("translate") {
        fill(evenOdd = true) {
            rect(4f, 6f, 24f, 22f)
            moveTo(10f, 23f); lineTo(14.5f, 10f); lineTo(17.5f, 10f); lineTo(22f, 23f); lineTo(19f, 23f); lineTo(18f, 20f); lineTo(14f, 20f); lineTo(13f, 23f); close()
            moveTo(14.8f, 17.5f); lineTo(17.2f, 17.5f); lineTo(16f, 13.5f); close()
        }
        stroke(3f) { moveTo(31f, 20f); lineTo(44f, 20f); lineTo(44f, 42f); lineTo(20f, 42f); lineTo(20f, 31f) }
        fill { rect(26f, 28f, 13f, 2.5f); rect(31.5f, 25f, 2.5f, 4f) }
        stroke(2.5f) { moveTo(28f, 30f); curveTo(29f, 34f, 33f, 38f, 38f, 38f); moveTo(38f, 30f); curveTo(36f, 34f, 32f, 37f, 28f, 38f) }
    }

    val Microphone = icon("microphone") {
        fill {
            moveTo(18f, 12f); curveTo(18f, 8.7f, 20.7f, 6f, 24f, 6f); curveTo(27.3f, 6f, 30f, 8.7f, 30f, 12f); lineTo(30f, 24f)
            curveTo(30f, 27.3f, 27.3f, 30f, 24f, 30f); curveTo(20.7f, 30f, 18f, 27.3f, 18f, 24f); close()
        }
        stroke(3f) { moveTo(12f, 22f); curveTo(12f, 29f, 17.5f, 35f, 24f, 35f); curveTo(30.5f, 35f, 36f, 29f, 36f, 22f); moveTo(24f, 35f); lineTo(24f, 42f) }
    }

    val Radio = icon("radio") {
        fill(evenOdd = true) {
            rect(5f, 16f, 38f, 26f)
            circle(16f, 29f, 6f)
            rect(27f, 23f, 12f, 2.5f); rect(27f, 28f, 12f, 2.5f); rect(27f, 33f, 12f, 2.5f)
        }
        stroke(2.5f) { moveTo(12f, 15f); lineTo(36f, 5f) }
    }

    val Book = icon("book") {
        fill { moveTo(4f, 9f); curveTo(10f, 7f, 17f, 7f, 22.5f, 10.5f); lineTo(22.5f, 41f); curveTo(17f, 38f, 10f, 38f, 4f, 40f); close() }
        fill { moveTo(44f, 9f); curveTo(38f, 7f, 31f, 7f, 25.5f, 10.5f); lineTo(25.5f, 41f); curveTo(31f, 38f, 38f, 38f, 44f, 40f); close() }
    }

    val Shield = icon("shield") {
        fill { moveTo(24f, 4f); lineTo(40f, 10f); lineTo(40f, 22f); curveTo(40f, 32f, 33f, 40f, 24f, 44f); curveTo(15f, 40f, 8f, 32f, 8f, 22f); lineTo(8f, 10f); close() }
    }

    val Tv = icon("tv") {
        fill(evenOdd = true) {
            rect(4f, 9f, 40f, 26f)
            rect(7.5f, 12.5f, 33f, 19f)
        }
        fill { rect(16f, 37f, 16f, 3f) }
    }

    val Home = icon("home") {
        fill { moveTo(24f, 6f); lineTo(43f, 22f); lineTo(38f, 22f); lineTo(38f, 41f); lineTo(28f, 41f); lineTo(28f, 29f); lineTo(20f, 29f); lineTo(20f, 41f); lineTo(10f, 41f); lineTo(10f, 22f); lineTo(5f, 22f); close() }
    }

    val Sparkle = icon("sparkle") {
        fill { moveTo(24f, 4f); curveTo(25.5f, 15f, 29f, 20f, 40f, 22f); curveTo(29f, 24f, 25.5f, 29f, 24f, 40f); curveTo(22.5f, 29f, 19f, 24f, 8f, 22f); curveTo(19f, 20f, 22.5f, 15f, 24f, 4f); close() }
        fill { moveTo(38f, 32f); curveTo(38.6f, 36f, 40f, 37.4f, 44f, 38f); curveTo(40f, 38.6f, 38.6f, 40f, 38f, 44f); curveTo(37.4f, 40f, 36f, 38.6f, 32f, 38f); curveTo(36f, 37.4f, 37.4f, 36f, 38f, 32f); close() }
    }
}
