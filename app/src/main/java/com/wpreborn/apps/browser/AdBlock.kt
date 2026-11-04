package com.wpreborn.apps.browser

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * A small built-in ad and tracker blocker: requests to well-known ad networks, ad servers and
 * trackers are answered with an empty response, so pages load faster and show fewer ads. The
 * page itself (the main frame) is never blocked, so a link to one of these sites still opens.
 */
object AdBlock {
    private val hosts = hashSetOf(
        // Google ads and tracking
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "google-analytics.com",
        "googletagservices.com", "googletagmanager.com", "adservice.google.com", "pagead2.googlesyndication.com",
        "imasdk.googleapis.com", "app-measurement.com", "2mdn.net", "admob.com", "adsense.com",
        // Ad exchanges and networks
        "adnxs.com", "adsrvr.org", "criteo.com", "criteo.net", "pubmatic.com", "rubiconproject.com",
        "openx.net", "casalemedia.com", "smartadserver.com", "taboola.com", "outbrain.com",
        "teads.tv", "media.net", "amazon-adsystem.com", "adform.net", "adcolony.com", "applovin.com",
        "unityads.unity3d.com", "moatads.com", "adsafeprotected.com", "doubleverify.com",
        "bidswitch.net", "contextweb.com", "sharethrough.com", "33across.com", "yieldmo.com",
        "indexww.com", "lijit.com", "sovrn.com", "spotxchange.com", "springserve.com",
        "triplelift.com", "gumgum.com", "undertone.com", "zedo.com", "propellerads.com",
        "popads.net", "popcash.net", "adsterra.com", "exoclick.com", "juicyads.com",
        "trafficjunky.net", "mgid.com", "revcontent.com", "adblade.com", "infolinks.com",
        "inmobi.com", "mopub.com", "vungle.com", "chartboost.com", "startappservice.com",
        "adskeeper.co.uk", "hilltopads.net", "clickadu.com", "a-ads.com", "yllix.com",
        "admixer.net", "adition.com", "adtech.de", "advertising.com", "yieldlab.net",
        "seedtag.com", "richaudience.com", "smartclip.net", "stickyadstv.com", "tremorhub.com",
        "zemanta.com", "nativo.com", "plista.com", "ligatus.com", "adhese.com",
        // Trackers and analytics
        "scorecardresearch.com", "quantserve.com", "hotjar.com", "mixpanel.com", "segment.io",
        "segment.com", "amplitude.com", "branch.io", "adjust.com", "appsflyer.com", "kochava.com",
        "crashlytics.com", "newrelic.com", "nr-data.net", "chartbeat.com", "chartbeat.net",
        "krxd.net", "bluekai.com", "demdex.net", "everesttech.net", "omtrdc.net", "2o7.net",
        "addthis.com", "sharethis.com", "exelator.com", "agkn.com", "rlcdn.com", "tapad.com",
        "mathtag.com", "turn.com", "eyeota.net", "crwdcntrl.net", "adsymptotic.com",
        "clarity.ms", "bat.bing.com", "ads.linkedin.com", "analytics.tiktok.com", "ads.tiktok.com",
        "ads-twitter.com", "static.ads-twitter.com", "an.facebook.com", "pixel.facebook.com",
        "connect.facebook.net", "ads.yahoo.com", "analytics.yahoo.com", "yieldmanager.com",
        "mc.yandex.ru", "an.yandex.ru", "top-fwz1.mail.ru", "hm.baidu.com", "cnzz.com",
        "statcounter.com", "histats.com", "clicky.com", "getclicky.com", "mouseflow.com",
        "fullstory.com", "luckyorange.com", "crazyegg.com", "inspectlet.com", "smartlook.com",
        "onesignal.com", "pushcrew.com", "pushengage.com", "subscribers.com",
    )

    private val empty get() = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    /** True when [host] or one of its parent domains is on the list. */
    fun isBlocked(host: String?): Boolean {
        var h = host?.lowercase()?.removePrefix("www.") ?: return false
        while (true) {
            if (h in hosts) return true
            val dot = h.indexOf('.')
            if (dot < 0 || h.indexOf('.', dot + 1) < 0) return false
            h = h.substring(dot + 1)
        }
    }

    /** The empty answer for a blocked request, or null to load it normally. */
    fun intercept(url: Uri, mainFrame: Boolean): WebResourceResponse? =
        if (!mainFrame && isBlocked(url.host)) empty else null
}
