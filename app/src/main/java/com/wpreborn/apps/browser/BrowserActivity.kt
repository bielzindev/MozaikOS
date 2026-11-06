package com.wpreborn.apps.browser

import com.wpreborn.core.ui.PanelEdge
import com.wpreborn.core.ui.WpPanelOverlay
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Message
import android.print.PrintManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebResourceResponse
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.wpreborn.R
import com.wpreborn.apps.common.WpActivity
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpAppBar
import com.wpreborn.core.ui.AppBarButton
import com.wpreborn.core.ui.WpPivot
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.core.ui.wpCombinedClickable

/** One open tab: its own WebView (Chromium, the system's Android WebView) and what it shows. */
class BrowserTab(val id: Long, val private: Boolean) {
    var web: WebView? = null
    var url by mutableStateOf("")
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(100)
    var loading by mutableStateOf(false)
    var thumb by mutableStateOf<Bitmap?>(null)
    var desktop by mutableStateOf(false)
    /** Ads and trackers blocked on the page shown. */
    var blocked by mutableIntStateOf(0)
}

/** What was pressed and held on a page: a link, a picture, or a picture that is a link. */
class LinkMenu(val link: String?, val image: String?)

/** The reading view of a page: its title and its text as headings and paragraphs. */
class ReaderPage(val title: String, val blocks: List<Pair<Boolean, String>>)

private enum class Panel { None, Tabs, Menu, Hub, Settings, Find, Reader, Downloads }

private const val MAX_TABS = 9
private const val DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"

/**
 * Internet Explorer in the Metro style of Windows Phone 8.1 / Windows 8, running on Chromium
 * (the Android System WebView): address bar at the bottom with the tabs and refresh buttons
 * and the "..." menu, tab thumbnails, InPrivate tabs, favourites and history (hub), frequent
 * sites on new tabs, find on page, desktop version, downloads, uploads, full-screen video,
 * and it can be the phone's default browser.
 */
class BrowserActivity : WpActivity() {

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val fileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
        fileCallback = null
    }

    val tabs = mutableStateListOf<BrowserTab>()
    var current by mutableIntStateOf(0)
    var customView by mutableStateOf<View?>(null)
    var linkMenu by mutableStateOf<LinkMenu?>(null)
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var nextId = 1L
    private val store by lazy { BrowserStore.get(this) }

    @Composable
    override fun Content() {
        LaunchedEffect(Unit) {
            if (tabs.isEmpty()) {
                val incoming = incomingUrl(intent)
                // The tabs left open last time, then the link Android asked to open, in front.
                store.openTabs.take(MAX_TABS - 1).forEach { newTab(it, private = false, select = false) }
                if (incoming != null) openLink(incoming) else if (tabs.isEmpty()) newTab("")
                current = current.coerceIn(0, tabs.lastIndex)
            }
        }
        if (tabs.isNotEmpty()) BrowserScreen(this, store)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingUrl(intent)?.let { openLink(it) }
    }

    /** The address another app asked to open: a link (VIEW), or a link shared as text. */
    private fun incomingUrl(i: Intent?): String? {
        i ?: return null
        i.dataString?.takeIf { it.isNotBlank() }?.let { return it }
        if (i.action == Intent.ACTION_SEND || i.action == Intent.ACTION_WEB_SEARCH) {
            val text = (i.getStringExtra(Intent.EXTRA_TEXT) ?: i.getStringExtra(android.app.SearchManager.QUERY)).orEmpty().trim()
            if (text.isEmpty()) return null
            return Regex("https?://\\S+").find(text)?.value ?: text
        }
        return null
    }

    /**
     * Opens a link from outside in a tab of its own, in front: the tab already showing it, or a
     * new one. With every tab in use the oldest one makes room (the link is never refused).
     */
    fun openLink(url: String) {
        tabs.indexOfFirst { !it.private && it.url == url }.takeIf { it >= 0 }?.let { current = it; return }
        if (tabs.size >= MAX_TABS) {
            val oldest = tabs.firstOrNull { it !== tab() } ?: tabs.first()
            closeTab(oldest)
        }
        newTab(url)
    }

    override fun onStop() {
        super.onStop()
        store.openTabs = tabs.filter { !it.private && it.url.isNotBlank() }.map { it.url }
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        tabs.forEach { it.web?.destroy() }
        super.onDestroy()
    }

    fun tab(): BrowserTab = tabs[current.coerceIn(0, tabs.lastIndex)]

    fun newTab(url: String, private: Boolean = false, select: Boolean = true) {
        if (tabs.size >= MAX_TABS) {
            Toast.makeText(this, getString(R.string.browser_too_many_tabs), Toast.LENGTH_SHORT).show()
            return
        }
        val t = BrowserTab(nextId++, private)
        t.web = makeWebView(t)
        tabs += t
        if (select) current = tabs.lastIndex
        if (url.isNotBlank()) load(t, url)
    }

    fun closeTab(t: BrowserTab) {
        val i = tabs.indexOf(t)
        if (i < 0) return
        t.web?.apply {
            stopLoading()
            if (t.private) clearHistory()
            destroy()
        }
        tabs.removeAt(i)
        if (tabs.isEmpty()) newTab("")
        current = (if (i <= current) current - 1 else current).coerceIn(0, tabs.lastIndex)
    }

    /** Text from the address bar: an address, or words to search for. */
    fun load(t: BrowserTab, input: String) {
        val text = input.trim()
        if (text.isEmpty()) return
        val url = when {
            Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(text) && !text.contains(' ') -> text
            !text.contains(' ') && Regex("^[^/]+\\.[a-zA-Z]{2,}(:\\d+)?(/.*)?$").matches(text) -> "https://$text"
            !text.contains(' ') && text.startsWith("localhost") -> "http://$text"
            else -> store.engine.url + Uri.encode(text)
        }
        t.url = url
        t.web?.loadUrl(url)
    }

    fun setDesktop(t: BrowserTab, on: Boolean) {
        t.desktop = on
        t.web?.settings?.apply {
            userAgentString = if (on) DESKTOP_UA else null
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        t.web?.reload()
    }

    fun capture(t: BrowserTab) {
        val w = t.web ?: return
        if (w.width <= 0 || w.height <= 0) return
        runCatching {
            val s = 0.35f
            val bmp = Bitmap.createBitmap((w.width * s).toInt().coerceAtLeast(1), (w.width * s * 1.25f).toInt().coerceAtLeast(1), Bitmap.Config.RGB_565)
            val c = android.graphics.Canvas(bmp)
            c.scale(s, s)
            c.translate(-w.scrollX.toFloat(), -w.scrollY.toFloat())
            w.draw(c)
            t.thumb = bmp
        }
    }

    fun exitFullscreen() {
        customCallback?.onCustomViewHidden()
        customCallback = null
        customView = null
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }

    fun clearData() {
        store.clearHistory()
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
        tabs.forEach { it.web?.clearCache(true) }
        Toast.makeText(this, getString(R.string.browser_cleared), Toast.LENGTH_SHORT).show()
    }

    fun download(url: String, ua: String?, disposition: String?, mime: String?) {
        runCatching {
            val name = URLUtil.guessFileName(url, disposition, mime)
            val req = DownloadManager.Request(Uri.parse(url))
                .setTitle(name)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            if (!mime.isNullOrBlank()) req.setMimeType(mime)
            if (!ua.isNullOrBlank()) req.addRequestHeader("User-Agent", ua)
            CookieManager.getInstance().getCookie(url)?.let { req.addRequestHeader("Cookie", it) }
            val id = (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            store.addDownload(id)
            Toast.makeText(this, getString(R.string.browser_downloading, name), Toast.LENGTH_SHORT).show()
        }
    }

    fun copyLink(url: String) {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(url, url))
        Toast.makeText(this, getString(R.string.browser_link_copied), Toast.LENGTH_SHORT).show()
    }

    fun shareLink(url: String, title: String = "") {
        runCatching {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url).putExtra(Intent.EXTRA_SUBJECT, title), null))
        }
    }

    fun printPage(t: BrowserTab) {
        val w = t.web ?: return
        runCatching {
            val name = t.title.ifBlank { displayAddress(t) }.ifBlank { "page" }
            getSystemService(PrintManager::class.java)?.print(name, w.createPrintDocumentAdapter(name), null)
        }
    }

    fun translatePage(t: BrowserTab) {
        if (t.url.isBlank()) return
        val lang = java.util.Locale.getDefault().language.ifBlank { "en" }
        load(t, "https://translate.google.com/translate?sl=auto&tl=$lang&u=" + Uri.encode(t.url))
    }

    /** Text size and dark pages, from the settings, on every open tab. */
    fun applyLook() {
        tabs.forEach { t ->
            t.web?.settings?.textZoom = store.textZoom
            t.web?.let { applyDark(it) }
        }
    }

    private fun applyDark(w: WebView) {
        val on = store.forceDark
        w.evaluateJavascript(
            "(function(){var id='mozaik-dark';var e=document.getElementById(id);" +
                "if(!$on){if(e)e.remove();return;}if(e)return;e=document.createElement('style');e.id=id;" +
                "e.textContent='html{filter:invert(1) hue-rotate(180deg)!important;background:#fff!important}" +
                "img,video,picture,canvas,iframe,svg image,[style*=\"background-image\"]{filter:invert(1) hue-rotate(180deg)!important}';" +
                "(document.head||document.documentElement).appendChild(e);})()",
            null,
        )
    }

    /** Reads the article of the page (the text of its <article> or <main>) for the reading view. */
    fun readPage(t: BrowserTab, onResult: (ReaderPage?) -> Unit) {
        val w = t.web ?: return onResult(null)
        val js = "(function(){var r=document.querySelector('article')||document.querySelector('main')||document.querySelector('[role=main]')||document.body;" +
            "var n=r.querySelectorAll('h1,h2,h3,p,li,blockquote,pre');var o=[];" +
            "for(var i=0;i<n.length;i++){var x=n[i];if(x.closest('nav,footer,aside,header,form,[aria-hidden=true]'))continue;" +
            "var s=(x.innerText||'').replace(/\\s+/g,' ').trim();var h=/^H/.test(x.tagName);" +
            "if((h&&s.length>2)||s.length>=40)o.push([h?1:0,s]);}" +
            "return JSON.stringify({t:document.title,p:o});})()"
        w.evaluateJavascript(js) { raw ->
            val page = runCatching {
                val json = org.json.JSONObject(org.json.JSONTokener(raw).nextValue() as String)
                val p = json.getJSONArray("p")
                val blocks = List(p.length()) { i -> p.getJSONArray(i).let { (it.getInt(0) == 1) to it.getString(1) } }
                    .distinctBy { it.second }
                ReaderPage(json.optString("t"), blocks)
            }.getOrNull()
            onResult(page?.takeIf { it.blocks.count { b -> !b.first } >= 2 })
        }
    }

    private fun has(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("SetJavaScriptEnabled")
    private fun makeWebView(t: BrowserTab): WebView = WebView(this).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            mediaPlaybackRequiresUserGesture = true
            textZoom = store.textZoom
            // target="_blank" links and window.open open a new tab (see onCreateWindow).
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
            setGeolocationEnabled(true)
            if (t.private) cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, !t.private)
        setOnLongClickListener {
            val hit = hitTestResult
            when (hit.type) {
                WebView.HitTestResult.SRC_ANCHOR_TYPE -> hit.extra?.let { linkMenu = LinkMenu(it, null) }
                WebView.HitTestResult.IMAGE_TYPE -> hit.extra?.let { linkMenu = LinkMenu(null, it) }
                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    // extra is the picture; the link it sits in comes from requestFocusNodeHref.
                    val msg = android.os.Handler(android.os.Looper.getMainLooper()).obtainMessage()
                    requestFocusNodeHref(msg)
                    val href = msg.data?.getString("url")?.takeIf { it.isNotBlank() }
                    linkMenu = LinkMenu(href ?: hit.extra, hit.extra)
                }
                else -> return@setOnLongClickListener false
            }
            linkMenu != null
        }
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (!store.adBlock) return null
                val r = AdBlock.intercept(request.url, request.isForMainFrame)
                if (r != null) view.post { t.blocked++ }
                return r
            }

            override fun onPageCommitVisible(view: WebView, url: String) {
                if (store.forceDark) applyDark(view)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                val scheme = u.scheme?.lowercase()
                if (scheme == "http" || scheme == "https" || scheme == "about" || scheme == "data" || scheme == "blob" || scheme == "javascript") return false
                // tel:, mailto:, intent:, market:, apps' own links: hand them to Android.
                runCatching {
                    val i = if (scheme == "intent") Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME) else Intent(Intent.ACTION_VIEW, u)
                    i.addCategory(Intent.CATEGORY_BROWSABLE)
                    i.component = null
                    startActivity(i)
                }.onFailure {
                    if (scheme == "intent") {
                        runCatching { Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME).getStringExtra("browser_fallback_url") }
                            .getOrNull()?.let { view.loadUrl(it) }
                    }
                }
                return true
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                t.url = url
                t.loading = true
                t.blocked = 0
            }

            override fun onPageFinished(view: WebView, url: String) {
                t.url = url
                t.loading = false
                t.title = view.title.orEmpty()
                if (store.forceDark) applyDark(view)
                if (!t.private) store.visit(t.title, url)
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                t.progress = newProgress
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                t.title = title.orEmpty()
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                customCallback = callback
                customView = view
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
            }

            override fun onHideCustomView() = exitFullscreen()

            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                // Only windows the user asked for (a tap on a link): pop-ups stay blocked.
                if (!isUserGesture || tabs.size >= MAX_TABS) return false
                val nt = BrowserTab(nextId++, t.private)
                val web = makeWebView(nt)
                nt.web = web
                tabs += nt
                current = tabs.lastIndex
                (resultMsg.obj as? WebView.WebViewTransport)?.webView = web
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView) {
                tabs.firstOrNull { it.web === window }?.let { closeTab(it) }
            }

            // Sites that ask for the location, camera or microphone get them when Mozaik has them.
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                val ok = has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (!ok) requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 7)
                callback.invoke(origin, ok, false)
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                val granted = request.resources.filter {
                    when (it) {
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> has(Manifest.permission.CAMERA)
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> has(Manifest.permission.RECORD_AUDIO)
                        PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> true
                        else -> false
                    }
                }
                if (granted.isEmpty()) request.deny() else request.grant(granted.toTypedArray())
            }

            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return runCatching { fileLauncher.launch(params.createIntent()) }.isSuccess.also { if (!it) fileCallback = null }
            }
        }
        setDownloadListener { url, ua, disposition, mime, _ -> download(url, ua, disposition, mime) }
    }
}

@Composable
private fun BrowserScreen(a: BrowserActivity, store: BrowserStore) {
    val context = a
    var panel by remember { mutableStateOf(Panel.None) }
    val t = a.tab()
    val focus = LocalFocusManager.current
    var editing by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    val focusRequester = remember { FocusRequester() }
    var findText by remember { mutableStateOf("") }
    var reader by remember { mutableStateOf<ReaderPage?>(null) }
    // On the dark InPrivate bar the buttons are white, in any theme.
    val barFg = if (t.private) Color.White else Wp.palette.foreground

    // Back: full screen video → panels → address bar → page history → close.
    BackHandler(enabled = a.customView != null || panel != Panel.None || editing || t.web?.canGoBack() == true || true) {
        when {
            a.customView != null -> a.exitFullscreen()
            a.linkMenu != null -> a.linkMenu = null
            panel != Panel.None -> {
                if (panel == Panel.Find) t.web?.clearMatches()
                panel = Panel.None
            }
            editing -> focus.clearFocus()
            t.web?.canGoBack() == true -> t.web?.goBack()
            else -> a.closeWithTurnstile()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().background(Color.White)) {
                // The page of the current tab (each tab keeps its own WebView alive).
                AndroidView(
                    factory = { FrameLayout(it) },
                    modifier = Modifier.fillMaxSize(),
                    update = { frame ->
                        val web = t.web
                        if (web != null && (frame.childCount != 1 || frame.getChildAt(0) !== web)) {
                            frame.removeAllViews()
                            (web.parent as? ViewGroup)?.removeView(web)
                            frame.addView(web)
                        }
                    },
                )
                if (t.url.isBlank()) StartPage(store, t.private) { a.load(t, it) }
                if (editing && field.text.isNotBlank()) Suggestions(store, field.text, store.engine.label) {
                    focus.clearFocus()
                    a.load(t, it)
                }
            }
            if (panel == Panel.Find) {
                FindBar(findText, { findText = it; t.web?.findAllAsync(it) }, onNext = { t.web?.findNext(true) }, onPrev = { t.web?.findNext(false) }) {
                    t.web?.clearMatches()
                    panel = Panel.None
                }
            }
            // Progress: a thin accent line, like IE on Windows Phone.
            Box(Modifier.fillMaxWidth().height(3.dp).background(Wp.palette.chrome)) {
                if (t.loading || t.progress < 100) {
                    Box(Modifier.fillMaxWidth(t.progress.coerceIn(5, 100) / 100f).fillMaxHeight().background(Wp.accent))
                }
            }
            // The address bar, at the bottom.
            Row(
                Modifier.fillMaxWidth().height(56.dp).background(if (t.private) Color(0xFF1F1F1F) else Wp.palette.chrome).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!editing && t.url.startsWith("https://")) {
                    WpIcon(WpIcons.Lock, tint = barFg.copy(alpha = 0.7f), size = 14.dp, modifier = Modifier.padding(start = 4.dp))
                } else if (!editing && t.url.startsWith("http://")) {
                    WpIcon(WpIcons.Info, tint = barFg.copy(alpha = 0.7f), size = 14.dp, modifier = Modifier.padding(start = 4.dp))
                }
                if (t.private && !editing) {
                    Box(Modifier.background(Wp.accent).padding(horizontal = 5.dp, vertical = 2.dp)) {
                        WpText("InPrivate", style = WpType.Small, color = Color.White)
                    }
                    Spacer(Modifier.width(6.dp))
                }
                val shown = if (editing) field else TextFieldValue(displayAddress(t))
                BasicTextField(
                    value = shown,
                    onValueChange = { field = it },
                    singleLine = true,
                    // While typing the box is white, so the text is black in every theme.
                    textStyle = WpType.Normal.copy(color = if (editing) Color.Black else barFg, fontSize = 16.sp),
                    cursorBrush = SolidColor(Wp.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onGo = {
                        a.load(t, field.text)
                        focus.clearFocus()
                    }),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .background(if (editing) Color.White else Color.Transparent)
                        .border(2.dp, if (editing) Wp.accent else Color.Transparent)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { s ->
                            if (s.isFocused && !editing) {
                                val u = t.url
                                field = TextFieldValue(u, TextRange(0, u.length))
                            }
                            editing = s.isFocused
                        },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (shown.text.isEmpty()) WpText(stringResource(R.string.browser_address_hint), style = WpType.Normal, color = if (editing) Color(0xFF8A8A8A) else barFg.copy(alpha = 0.6f), maxLines = 1)
                            inner()
                        }
                    },
                )
                if (!editing) {
                    BarButton(if (t.loading) WpIcons.Close else WpIcons.Refresh, stringResource(if (t.loading) R.string.browser_stop else R.string.refresh), barFg) {
                        if (t.loading) t.web?.stopLoading() else t.web?.reload()
                    }
                    // Tabs: a square with the number of tabs.
                    Box(
                        Modifier.size(44.dp).wpClickable {
                            a.tabs.forEach { if (it === t) a.capture(it) }
                            panel = if (panel == Panel.Tabs) Panel.None else Panel.Tabs
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(24.dp).border(2.dp, barFg), contentAlignment = Alignment.Center) {
                            WpText(a.tabs.size.toString(), style = WpType.Small.copy(fontSize = 12.sp), color = barFg)
                        }
                    }
                    BarButton(WpIcons.More, stringResource(R.string.browser_more), barFg) { panel = if (panel == Panel.Menu) Panel.None else Panel.Menu }
                }
            }
        }

        // The full pages (tabs, hub, settings, reader, downloads) slide up and fade in, and go back
        // down when closed; the last one stays drawn while it leaves.
        val fullPage = panel == Panel.Tabs || panel == Panel.Hub || panel == Panel.Settings || panel == Panel.Reader || panel == Panel.Downloads
        var lastPage by remember { mutableStateOf(Panel.Tabs) }
        if (fullPage && lastPage != panel) androidx.compose.runtime.SideEffect { lastPage = panel }
        androidx.compose.animation.AnimatedVisibility(
            visible = fullPage,
            enter = androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(320, easing = com.wpreborn.core.motion.WpEasing.ExpOut)) { it / 4 } +
                androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)),
            exit = androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(210, easing = com.wpreborn.core.motion.WpEasing.ExpIn)) { it / 4 } +
                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(170)),
        ) {
            when (if (fullPage) panel else lastPage) {
                Panel.Tabs -> TabsPage(a, onClose = { panel = Panel.None })
                Panel.Hub -> HubPage(store, onOpen = { a.load(t, it); panel = Panel.None })
                Panel.Settings -> SettingsPage(a, store)
                Panel.Reader -> reader?.let { ReaderView(it, store) }
                Panel.Downloads -> DownloadsPage(a, store)
                else -> {}
            }
        }
        if (panel == Panel.Menu) {
            MenuPanel(a, store, t, onDismiss = { panel = Panel.None }) { p ->
                if (p == Panel.Reader) {
                    a.readPage(t) { page ->
                        if (page == null) Toast.makeText(a, a.getString(R.string.browser_reader_empty), Toast.LENGTH_SHORT).show()
                        else { reader = page; panel = Panel.Reader }
                    }
                } else panel = p
            }
        }
        a.linkMenu?.let { m -> LinkMenuPanel(a, m, t.private) { a.linkMenu = null } }
        a.customView?.let { v ->
            AndroidView(
                factory = { ctx -> FrameLayout(ctx).apply { setBackgroundColor(android.graphics.Color.BLACK) } },
                modifier = Modifier.fillMaxSize(),
                update = { f ->
                    if (f.childCount == 0 || f.getChildAt(0) !== v) {
                        f.removeAllViews()
                        (v.parent as? ViewGroup)?.removeView(v)
                        f.addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    }
                },
            )
        }
    }
}

private fun displayAddress(t: BrowserTab): String {
    if (t.url.isBlank()) return ""
    val u = Uri.parse(t.url)
    return (u.host ?: t.url).removePrefix("www.")
}

@Composable
private fun BarButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = Wp.palette.foreground, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).wpClickable(onClick = onClick), contentAlignment = Alignment.Center) {
        WpIcon(icon, tint = tint, size = 24.dp)
    }
}

/** New tab: frequent sites as tiles and the favourites, like IE's start page. */
@Composable
private fun StartPage(store: BrowserStore, private: Boolean, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize().background(Wp.palette.background).verticalScroll(rememberScrollState()).padding(16.dp)) {
        if (private) {
            WpText("InPrivate", style = WpType.Header)
            WpText(stringResource(R.string.browser_inprivate_info), style = WpType.Normal, color = Wp.palette.subtle, modifier = Modifier.padding(top = 8.dp))
            return@Column
        }
        val frequent = store.frequent()
        WpText(stringResource(R.string.browser_frequent), style = WpType.Large, modifier = Modifier.padding(bottom = 10.dp))
        if (frequent.isEmpty()) {
            WpText(stringResource(R.string.browser_frequent_empty), style = WpType.Normal, color = Wp.palette.subtle)
        }
        frequent.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { e -> SiteTile(e, Modifier.weight(1f)) { onOpen(e.url) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (store.favorites.isNotEmpty()) {
            WpText(stringResource(R.string.browser_favorites), style = WpType.Large, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            store.favorites.take(12).forEach { e -> EntryRow(e, onOpen = { onOpen(e.url) }) }
        }
    }
}

@Composable
private fun SiteTile(e: WebEntry, modifier: Modifier, onClick: () -> Unit) {
    val host = (Uri.parse(e.url).host ?: e.url).removePrefix("www.")
    val colors = listOf(0xFF1BA1E2, 0xFF00ABA9, 0xFF0050EF, 0xFF6A00FF, 0xFFD80073, 0xFFE51400, 0xFFFA6800, 0xFF60A917)
    Box(
        modifier.aspectRatio(2f).background(Color(colors[Math.floorMod(host.hashCode(), colors.size)])).wpClickable(onClick = onClick).padding(8.dp),
    ) {
        WpText(host.take(1).uppercase(), style = WpType.Header.copy(fontSize = 34.sp, lineHeight = 36.sp), color = Color.White, modifier = Modifier.align(Alignment.TopStart))
        WpText(host, style = WpType.Small, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun EntryRow(e: WebEntry, onLongClick: (() -> Unit)? = null, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().wpCombinedClickable(onLongClick = onLongClick, onClick = onOpen).padding(vertical = 6.dp)) {
        WpText(e.title, style = WpType.Large.copy(fontSize = 19.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        WpText(e.url, style = WpType.Small, color = Wp.palette.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** While typing: matching favourites and history above the address bar. */
@Composable
private fun Suggestions(store: BrowserStore, text: String, engine: String, onPick: (String) -> Unit) {
    val q = text.trim().lowercase()
    val list = remember(text) {
        (store.favorites + store.history).filter { it.url.lowercase().contains(q) || it.title.lowercase().contains(q) }.distinctBy { it.url }.take(5)
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        Column(Modifier.fillMaxWidth().background(Wp.palette.chrome).padding(horizontal = 16.dp, vertical = 6.dp)) {
            list.forEach { e -> EntryRow(e) { onPick(e.url) } }
            // Always offered: search the words with the chosen engine.
            Row(Modifier.fillMaxWidth().wpClickable { onPick(text) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                WpIcon(WpIcons.Search, tint = Wp.accent, size = 20.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    WpText(stringResource(R.string.browser_search_for, text.trim()), style = WpType.Large.copy(fontSize = 19.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    WpText(engine, style = WpType.Small, color = Wp.palette.subtle)
                }
            }
        }
    }
}

@Composable
private fun FindBar(text: String, onText: (String) -> Unit, onNext: () -> Unit, onPrev: () -> Unit, onClose: () -> Unit) {
    val req = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { req.requestFocus() } }
    Row(Modifier.fillMaxWidth().background(Wp.palette.chrome).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            text, onText, singleLine = true,
            textStyle = WpType.Normal.copy(color = Color.Black, fontSize = 16.sp),
            cursorBrush = SolidColor(Wp.accent),
            modifier = Modifier.weight(1f).background(Color.White).border(2.dp, Wp.accent).padding(8.dp).focusRequester(req),
        )
        BarButton(WpIcons.Back, stringResource(R.string.browser_find_prev), onClick = onPrev)
        BarButton(WpIcons.Forward, stringResource(R.string.browser_find_next), onClick = onNext)
        BarButton(WpIcons.Close, stringResource(R.string.cancel), onClick = onClose)
    }
}

/** The "..." menu: a list of text items rising above the bar, as on Windows Phone. */
@Composable
private fun MenuPanel(a: BrowserActivity, store: BrowserStore, t: BrowserTab, onDismiss: () -> Unit, open: (Panel) -> Unit) {
    WpPanelOverlay(
        onDismiss = onDismiss, edge = PanelEdge.Bottom, scrim = 0.3f,
        padding = androidx.compose.foundation.layout.PaddingValues(bottom = 62.dp, top = 8.dp), maxHeight = 560.dp, scroll = true,
    ) { close ->
            @Composable
            fun Item(label: String, enabled: Boolean = true, onClick: () -> Unit) {
                WpText(
                    label,
                    style = WpType.Large.copy(fontSize = 20.sp),
                    color = if (enabled) Wp.palette.foreground else Wp.palette.disabled,
                    modifier = Modifier.fillMaxWidth().then(if (enabled) Modifier.wpClickable { close { onDismiss(); onClick() } } else Modifier).padding(horizontal = 20.dp, vertical = 9.dp),
                )
            }
            Item(stringResource(R.string.browser_forward), enabled = t.web?.canGoForward() == true) { t.web?.goForward() }
            Item(stringResource(R.string.browser_new_tab)) { a.newTab("") }
            Item(stringResource(R.string.browser_new_inprivate)) { a.newTab("", private = true) }
            Item(stringResource(if (store.isFavorite(t.url)) R.string.browser_remove_favorite else R.string.browser_add_favorite), enabled = t.url.isNotBlank()) {
                store.toggleFavorite(t.title, t.url)
            }
            Item(stringResource(R.string.browser_hub)) { open(Panel.Hub) }
            Item(stringResource(R.string.browser_reader), enabled = t.url.isNotBlank()) { open(Panel.Reader) }
            Item(stringResource(R.string.share), enabled = t.url.isNotBlank()) { a.shareLink(t.url, t.title) }
            Item(stringResource(R.string.browser_find), enabled = t.url.isNotBlank()) { open(Panel.Find) }
            Item(stringResource(R.string.browser_translate), enabled = t.url.isNotBlank()) { a.translatePage(t) }
            Item(stringResource(R.string.browser_print), enabled = t.url.isNotBlank()) { a.printPage(t) }
            Item(stringResource(R.string.browser_downloads)) { open(Panel.Downloads) }
            Item(stringResource(if (t.desktop) R.string.browser_mobile_site else R.string.browser_desktop_site), enabled = t.url.isNotBlank()) { a.setDesktop(t, !t.desktop) }
            Item(stringResource(R.string.browser_settings)) { open(Panel.Settings) }
            if (t.blocked > 0) {
                WpText(
                    pluralSafe(stringResource(R.string.browser_blocked), t.blocked),
                    style = WpType.Small, color = Wp.palette.subtle,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
    }
}

/** Tab switcher: thumbnails in two columns, the current one in the accent colour. */
@Composable
private fun TabsPage(a: BrowserActivity, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Wp.palette.background)) {
        WpText(stringResource(R.string.browser_tabs).uppercase(), style = WpType.AppTitle, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 10.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(a.tabs, key = { it.id }) { tab ->
                val selected = tab === a.tab()
                Column(Modifier.wpClickable { a.current = a.tabs.indexOf(tab); onClose() }) {
                    Box(Modifier.fillMaxWidth().aspectRatio(0.8f).background(Color.White).border(3.dp, if (selected) Wp.accent else Wp.palette.raised)) {
                        tab.thumb?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize().padding(3.dp), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter) }
                        if (tab.private) {
                            Box(Modifier.align(Alignment.TopStart).background(Wp.accent).padding(horizontal = 4.dp, vertical = 1.dp)) {
                                WpText("InPrivate", style = WpType.Small, color = Color.White)
                            }
                        }
                        Box(
                            Modifier.align(Alignment.TopEnd).size(34.dp).background(Color.Black.copy(alpha = 0.6f)).wpClickable { a.closeTab(tab) },
                            contentAlignment = Alignment.Center,
                        ) { WpIcon(WpIcons.Close, tint = Color.White, size = 18.dp) }
                    }
                    WpText(tab.title.ifBlank { displayAddress(tab).ifBlank { stringResource(R.string.browser_new_tab) } }, style = WpType.Small, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(Wp.palette.chrome).padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextAction(stringResource(R.string.browser_new_tab)) { a.newTab(""); onClose() }
            TextAction(stringResource(R.string.browser_new_inprivate)) { a.newTab("", private = true); onClose() }
        }
    }
}

@Composable
private fun TextAction(label: String, onClick: () -> Unit) {
    Column(Modifier.wpClickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(40.dp).border(2.dp, Wp.palette.foreground, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
            WpIcon(WpIcons.Add, tint = Wp.palette.foreground, size = 22.dp)
        }
        WpText(label, style = WpType.Small, modifier = Modifier.padding(top = 2.dp))
    }
}

/** The hub: favourites and history, as a pivot. */
@Composable
private fun HubPage(store: BrowserStore, onOpen: (String) -> Unit) {
    Box(Modifier.fillMaxSize().background(Wp.palette.background)) {
        WpPivot(
            title = stringResource(R.string.app_browser),
            headers = listOf(stringResource(R.string.browser_favorites), stringResource(R.string.browser_history)),
        ) { index ->
            var query by remember { mutableStateOf("") }
            val all = if (index == 0) store.favorites.toList() else store.history.toList()
            val q = query.trim().lowercase()
            val list = if (q.isEmpty()) all else all.filter { it.title.lowercase().contains(q) || it.url.lowercase().contains(q) }
            Column(Modifier.fillMaxSize()) {
            if (index == 1 && all.isNotEmpty()) {
                BasicTextField(
                    query, { query = it }, singleLine = true,
                    textStyle = WpType.Normal.copy(color = Color.Black, fontSize = 16.sp),
                    cursorBrush = SolidColor(Wp.accent),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth().background(Color.White).border(2.dp, Wp.palette.subtle).padding(8.dp),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) WpText(stringResource(R.string.browser_search_history), style = WpType.Normal, color = Color(0xFF8A8A8A), maxLines = 1)
                            inner()
                        }
                    },
                )
            }
            if (list.isEmpty()) {
                WpText(stringResource(if (index == 0) R.string.browser_no_favorites else R.string.browser_no_history), style = WpType.Normal, color = Wp.palette.subtle, modifier = Modifier.padding(20.dp))
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                    items(list, key = { it.url + it.time }) { e ->
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.animateItem()) {
                        // Press and hold removes it.
                        EntryRow(e, onLongClick = { if (index == 0) store.removeFavorite(e) else store.removeHistory(e) }) { onOpen(e.url) }
                    
        }
    }
                }
            }
            }
        }
    }
}

@Composable
private fun SettingsPage(a: BrowserActivity, store: BrowserStore) {
    var engine by remember { mutableStateOf(store.engine) }
    Column(Modifier.fillMaxSize().background(Wp.palette.background).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp)) {
        WpText(stringResource(R.string.app_browser).uppercase(), style = WpType.AppTitle)
        WpText(stringResource(R.string.browser_settings), style = WpType.Header, modifier = Modifier.padding(bottom = 16.dp))
        WpText(stringResource(R.string.browser_search_engine), style = WpType.Subtle, color = Wp.palette.subtle)
        SearchEngine.entries.forEach { e ->
            Row(Modifier.fillMaxWidth().wpClickable { engine = e; store.engine = e }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).border(2.dp, Wp.palette.foreground, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                    if (engine == e) Box(Modifier.size(10.dp).background(Wp.palette.foreground, androidx.compose.foundation.shape.CircleShape))
                }
                Spacer(Modifier.width(12.dp))
                WpText(e.label, style = WpType.Large)
            }
        }
        Spacer(Modifier.height(12.dp))
        var adBlock by remember { mutableStateOf(store.adBlock) }
        com.wpreborn.core.ui.WpToggle(stringResource(R.string.browser_adblock), adBlock) { adBlock = it; store.adBlock = it }
        var dark by remember { mutableStateOf(store.forceDark) }
        com.wpreborn.core.ui.WpToggle(stringResource(R.string.browser_force_dark), dark) { dark = it; store.forceDark = it; a.applyLook() }
        Spacer(Modifier.height(8.dp))
        WpText(stringResource(R.string.browser_text_size), style = WpType.Subtle, color = Wp.palette.subtle)
        var zoom by remember { mutableIntStateOf(store.textZoom) }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(85, 100, 115, 130, 150, 175).forEach { z ->
                val on = zoom == z
                Box(
                    Modifier.weight(1f).height(40.dp).background(if (on) Wp.accent else Color.Transparent).border(2.dp, if (on) Wp.accent else Wp.palette.subtle)
                        .wpClickable { zoom = z; store.textZoom = z; a.applyLook() },
                    contentAlignment = Alignment.Center,
                ) { WpText("$z%", style = WpType.Small, color = if (on) Color.White else Wp.palette.foreground) }
            }
        }
        Spacer(Modifier.height(16.dp))
        com.wpreborn.core.ui.WpButton(stringResource(R.string.browser_clear)) { a.clearData() }
        WpText(stringResource(R.string.browser_engine_info), style = WpType.Subtle, color = Wp.palette.subtle, modifier = Modifier.padding(top = 16.dp))
        Spacer(Modifier.height(10.dp))
        com.wpreborn.core.ui.WpButton(stringResource(R.string.browser_make_default)) {
            runCatching { a.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
        }
    }
}

private fun pluralSafe(format: String, n: Int): String = runCatching { String.format(format, n) }.getOrDefault(n.toString())

/** Press and hold on a link or picture: what to do with it, as a Windows Phone context menu. */
@Composable
private fun LinkMenuPanel(a: BrowserActivity, m: LinkMenu, private: Boolean, onDismiss: () -> Unit) {
    WpPanelOverlay(onDismiss = onDismiss, edge = PanelEdge.Center, scrim = 0.5f, padding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp)) { close ->
            WpText(
                m.link ?: m.image.orEmpty(), style = WpType.Small, color = Wp.palette.subtle, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            @Composable
            fun Item(label: String, onClick: () -> Unit) {
                WpText(
                    label, style = WpType.Large.copy(fontSize = 20.sp),
                    modifier = Modifier.fillMaxWidth().wpClickable { close { onDismiss(); onClick() } }.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
            m.link?.let { link ->
                Item(stringResource(R.string.browser_open_new_tab)) { a.newTab(link, private = private) }
                if (!private) Item(stringResource(R.string.browser_open_inprivate)) { a.newTab(link, private = true) }
                Item(stringResource(R.string.browser_copy_link)) { a.copyLink(link) }
                Item(stringResource(R.string.browser_share_link)) { a.shareLink(link) }
            }
            m.image?.let { img ->
                if (img.startsWith("http")) Item(stringResource(R.string.browser_save_image)) { a.download(img, null, null, null) }
                Item(stringResource(R.string.browser_open_image)) { a.newTab(img, private = private) }
            }
    }
}

/** Reading view: the article alone, in large text, without ads or menus. */
@Composable
private fun ReaderView(page: ReaderPage, store: BrowserStore) {
    var size by remember { mutableIntStateOf(store.readerSize) }
    Column(Modifier.fillMaxSize().background(Wp.palette.background)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            WpText(stringResource(R.string.browser_reader).uppercase(), style = WpType.AppTitle, modifier = Modifier.weight(1f))
            listOf(-2 to "A-", 2 to "A+").forEach { (d, label) ->
                Box(
                    Modifier.size(44.dp).wpClickable { size = (size + d).coerceIn(14, 32); store.readerSize = size },
                    contentAlignment = Alignment.Center,
                ) { WpText(label, style = WpType.Large, color = Wp.palette.foreground) }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp)) {
            item {
                WpText(
                    page.title, style = WpType.Header.copy(fontSize = (size + 11).sp, lineHeight = (size + 17).sp),
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
            items(page.blocks.size) { i ->
                val (heading, text) = page.blocks[i]
                if (heading) {
                    WpText(text, style = WpType.Large.copy(fontSize = (size + 4).sp, lineHeight = (size + 10).sp), modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                } else {
                    WpText(text, style = WpType.Normal.copy(fontSize = size.sp, lineHeight = (size * 1.5f).sp), modifier = Modifier.padding(bottom = 12.dp))
                }
            }
        }
    }
}


/** One download of the browser as its page shows it. */
private class Download(
    val id: Long,
    val title: String,
    val status: Int,
    val soFar: Long,
    val total: Long,
    val mime: String?,
)

private fun queryDownloads(dm: DownloadManager, ids: List<Long>): List<Download> {
    if (ids.isEmpty()) return emptyList()
    val out = ArrayList<Download>()
    runCatching {
        dm.query(DownloadManager.Query().setFilterById(*ids.toLongArray()))?.use { c ->
            val iId = c.getColumnIndex(DownloadManager.COLUMN_ID)
            val iTitle = c.getColumnIndex(DownloadManager.COLUMN_TITLE)
            val iStatus = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val iSoFar = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val iTotal = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val iMime = c.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE)
            while (c.moveToNext()) {
                out += Download(c.getLong(iId), c.getString(iTitle).orEmpty(), c.getInt(iStatus), c.getLong(iSoFar), c.getLong(iTotal), c.getString(iMime))
            }
        }
    }
    return out.sortedByDescending { it.id }
}

/**
 * The browser's own downloads page, like Internet Explorer's: what is downloading with its
 * progress, what finished (tap to open), what failed; cancel, delete, open the folder.
 */
@Composable
private fun DownloadsPage(a: BrowserActivity, store: BrowserStore) {
    val dm = remember { a.getSystemService(DownloadManager::class.java) }
    var rows by remember { mutableStateOf<List<Download>?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var deleting by remember { mutableStateOf<Download?>(null) }
    // Progress refreshes while the page is open.
    LaunchedEffect(refresh) {
        while (true) {
            val ids = store.downloads
            rows = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { queryDownloads(dm, ids) }
            kotlinx.coroutines.delay(700)
        }
    }
    fun open(d: Download) {
        runCatching {
            val uri = dm.getUriForDownloadedFile(d.id) ?: return
            a.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_VIEW).setDataAndType(uri, d.mime ?: a.contentResolver.getType(uri) ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    d.title,
                ),
            )
        }
    }
    Column(Modifier.fillMaxSize().background(Wp.palette.background)) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp)) {
            WpText(stringResource(R.string.app_browser).uppercase(), style = WpType.AppTitle)
            WpText(stringResource(R.string.browser_downloads), style = WpType.Header)
        }
        val list = rows
        Box(Modifier.weight(1f)) {
            if (list != null && list.isEmpty()) {
                WpText(stringResource(R.string.browser_dl_empty), style = WpType.Large, color = Wp.palette.subtle, modifier = Modifier.padding(20.dp))
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 16.dp)) {
                items(list.orEmpty(), key = { it.id }) { d ->
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.animateItem()) {
                    val done = d.status == DownloadManager.STATUS_SUCCESSFUL
                    val context = a
                    Row(
                        Modifier.fillMaxWidth().wpClickable(enabled = done) { open(d) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(56.dp).background(if (done) Wp.accent else Wp.palette.raised), contentAlignment = Alignment.Center) {
                            WpIcon(if (done) WpIcons.Check else WpIcons.Download, tint = if (done) Color.White else Wp.palette.foreground, size = 26.dp)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            WpText(d.title, style = WpType.Large.copy(fontSize = 20.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            fun size(b: Long) = android.text.format.Formatter.formatShortFileSize(context, b.coerceAtLeast(0))
                            val line = when (d.status) {
                                DownloadManager.STATUS_SUCCESSFUL -> size(d.total) + " · " + stringResource(R.string.browser_dl_done)
                                DownloadManager.STATUS_FAILED -> stringResource(R.string.browser_dl_failed)
                                DownloadManager.STATUS_PAUSED -> stringResource(R.string.browser_dl_paused)
                                DownloadManager.STATUS_PENDING -> stringResource(R.string.browser_dl_waiting)
                                else -> if (d.total > 0) stringResource(R.string.browser_dl_of, size(d.soFar), size(d.total)) else size(d.soFar)
                            }
                            WpText(line, style = WpType.Small, color = if (d.status == DownloadManager.STATUS_FAILED) Wp.accent else Wp.palette.subtle, maxLines = 1)
                            if (d.status == DownloadManager.STATUS_RUNNING || d.status == DownloadManager.STATUS_PENDING || d.status == DownloadManager.STATUS_PAUSED) {
                                val p = if (d.total > 0) (d.soFar.toFloat() / d.total).coerceIn(0f, 1f) else 0f
                                Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp).background(Wp.palette.raised)) {
                                    Box(Modifier.fillMaxWidth(p.coerceAtLeast(0.02f)).height(4.dp).background(Wp.accent))
                                }
                            }
                        }
                        // Cancel while it downloads; delete (the file too) once it is there.
                        Box(
                            Modifier.size(44.dp).wpClickable {
                                if (done) deleting = d else { runCatching { dm.remove(d.id) }; store.removeDownload(d.id); refresh++ }
                            },
                            contentAlignment = Alignment.Center,
                        ) { WpIcon(if (done) WpIcons.Delete else WpIcons.Close, tint = Wp.palette.foreground, size = 20.dp) }
                    }
                
        }
    }
            }
        }
        WpAppBar(
            buttons = listOf(
                AppBarButton(WpIcons.Folder, stringResource(R.string.browser_dl_folder)) {
                    runCatching { a.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                },
                AppBarButton(WpIcons.SelectAll, stringResource(R.string.browser_dl_clear), enabled = list.orEmpty().any { it.status == DownloadManager.STATUS_SUCCESSFUL || it.status == DownloadManager.STATUS_FAILED }) {
                    // Takes the finished and failed ones off the list (the files stay).
                    list.orEmpty().filter { it.status == DownloadManager.STATUS_SUCCESSFUL || it.status == DownloadManager.STATUS_FAILED }.forEach { store.removeDownload(it.id) }
                    refresh++
                },
            ),
        )
    }
    deleting?.let { d ->
        com.wpreborn.core.ui.WpMessageBox(
            title = stringResource(R.string.browser_dl_delete_title),
            message = d.title,
            confirm = stringResource(R.string.delete),
            onConfirm = {
                runCatching { dm.remove(d.id) }
                store.removeDownload(d.id)
                deleting = null
                refresh++
            },
            dismiss = stringResource(R.string.cancel),
            onDismiss = { deleting = null },
        )
    }
}
