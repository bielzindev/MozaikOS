package com.wpreborn.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.wpreborn.R
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Events from the keyboard view to the IME service. */
interface KeyboardListener {
    fun onKey(key: Key)
    fun onText(text: String)
    fun onSuggestion(index: Int, word: String)
    fun onSwipe(keys: String)
    fun onCursor(steps: Int)
    fun onDeleteWord()
    fun onLongPressSpace()
    /** A tool in the strip (clipboard, voice, edit, settings). */
    fun onToolbar(id: String) {}
}

/**
 * The ZUNEX keyboard, drawn like the Windows Phone 8.1 keyboard: flat rectangular keys with
 * thin gaps, lowercase Open Sans labels, the pressed key in the accent colour, a suggestion
 * strip on top and the accent-coloured Word Flow trail while swiping.
 */
@SuppressLint("ViewConstructor")
class KeyboardView(context: Context, private val listener: KeyboardListener) : View(context) {

    // --- Appearance -------------------------------------------------------------------------
    var dark = true
        set(v) { field = v; invalidate() }
    var accent = 0xFF0050EF.toInt()
        set(v) { field = v; invalidate() }

    private val density = resources.displayMetrics.density
    private val font: Typeface = ResourcesCompat.getFont(context, R.font.opensans_regular) ?: Typeface.DEFAULT
    private val fontLight: Typeface = ResourcesCompat.getFont(context, R.font.opensans_light) ?: Typeface.DEFAULT
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font; textAlign = Paint.Align.CENTER }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f * resources.displayMetrics.density; strokeJoin = Paint.Join.MITER }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }

    private val bg get() = if (dark) 0xFF000000.toInt() else 0xFFDEDEDE.toInt()
    private val keyBg get() = if (dark) 0xFF2E2E2E.toInt() else 0xFFFFFFFF.toInt()
    private val specialBg get() = if (dark) 0xFF1C1C1C.toInt() else 0xFFC8C8C8.toInt()
    private val fg get() = if (dark) Color.WHITE else Color.BLACK
    private val subtle get() = if (dark) 0x99FFFFFF.toInt() else 0x99000000.toInt()

    // --- Content ----------------------------------------------------------------------------
    var rows: List<KeyRow> = emptyList()
        set(v) { field = v; layoutKeys(); invalidate() }
    var suggestions: List<String> = emptyList()
        set(v) { field = v; invalidate() }
    /** Index of the suggestion that space will insert (autocorrect), highlighted. */
    var highlighted: Int = -1
        set(v) { field = v; invalidate() }
    var shiftState = 0 // 0 off, 1 once, 2 caps lock
        set(v) { field = v; invalidate() }
    var spaceLabel = ""
        set(v) { field = v; invalidate() }
    var enterLabel: String? = null
        set(v) { field = v; invalidate() }
    /** Tools shown in the strip when there are no suggestions: (glyph, id). */
    var toolbar: List<Pair<String, String>> = emptyList()
        set(v) { field = v; invalidate() }
    var swipeEnabled = true
    var lettersPage = true
    var language: KbLanguage = KeyboardLayouts.languages.first()
    var feedback: (View) -> Unit = {}

    /** Emoji mode: grid of emoji instead of rows. */
    var emojiMode = false
        set(v) {
            field = v
            // Open on recents when there are some, like the WP keyboard.
            emojiCategory = 0
            emojiPage = 0
            dragX = 0f
            layoutKeys(); invalidate()
        }
    private var emojiCategory = 0
    private var emojiPage = 0
    /** Horizontal offset of the emoji pages while the finger drags them. */
    private var dragX = 0f
    private var pageDrag = false
    private var settle: android.animation.ValueAnimator? = null

    private val recents = EmojiRecents(context)

    /** Recently used emoji first (when there are some), then the categories. */
    private fun cats(): List<Pair<String, List<String>>> {
        val r = recents.list()
        return if (r.isEmpty()) EmojiData.categories else listOf("🕘" to r) + EmojiData.categories
    }

    private val emojiCols = 8
    private val emojiRows = 4
    private val perPage get() = emojiCols * emojiRows
    private fun pagesOf(cat: Int) = max(1, (cats()[cat].second.size + perPage - 1) / perPage)

    /** The page before/after (cat, page), flowing across categories; null at the ends. */
    private fun neighbour(cat: Int, page: Int, forward: Boolean): Pair<Int, Int>? = when {
        forward && page + 1 < pagesOf(cat) -> cat to page + 1
        forward && cat + 1 < cats().size -> cat + 1 to 0
        !forward && page > 0 -> cat to page - 1
        !forward && cat > 0 -> cat - 1 to pagesOf(cat - 1) - 1
        else -> null
    }

    /** Key height in dp (from the size setting). */
    var keyHeightDp = 54f
        set(v) { if (field != v) { field = v; layoutKeys(); requestLayout(); invalidate() } }

    private val stripHeight get() = 44f * density
    private val keyHeight get() = keyHeightDp * density
    /** Space between keys; touches in it still go to the nearest key. */
    private val gap get() = 5f * density

    private data class Placed(val key: Key, val rect: RectF)
    private val placed = ArrayList<Placed>()

    private var pressed: Placed? = null
    private var downX = 0f
    private var downY = 0f
    private var swiping = false
    private var spaceDragging = false
    private var spaceSteps = 0
    private val swipePath = Path()
    private val swipeKeys = StringBuilder()
    private var alternates: List<String> = emptyList()
    private var altRect = RectF()
    private var altSelected = -1
    private val handler = Handler(Looper.getMainLooper())
    private var repeating = false

    private val longPress = Runnable {
        val p = pressed ?: return@Runnable
        when (p.key.type) {
            KeyType.Backspace -> startRepeat()
            KeyType.Space -> { listener.onLongPressSpace(); pressed = null; invalidate() }
            KeyType.Char, KeyType.Period, KeyType.Comma -> showAlternates(p)
            else -> {}
        }
    }

    private val repeat = object : Runnable {
        override fun run() {
            if (!repeating) return
            listener.onKey(Key("del", KeyType.Backspace))
            handler.postDelayed(this, 55)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val keyRows = if (emojiMode) 5 else rows.size
        val h = stripHeight + keyRows * keyHeight + gap + paddingBottom
        setMeasuredDimension(width, h.toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = layoutKeys()

    private fun layoutKeys() {
        placed.clear()
        if (width == 0) { requestLayout(); return }
        if (emojiMode) { layoutEmoji(); return }
        var y = stripHeight
        val unit = width / 10f
        rows.forEach { row ->
            val total = row.sumOf { it.width.toDouble() }.toFloat()
            // Rows with fewer keys are centred, like the 2nd row of QWERTY.
            val keyUnit = if (total > 10f) width / total else unit
            var x = (width - total * keyUnit) / 2f
            row.forEach { k ->
                val w = k.width * keyUnit
                placed += Placed(k, RectF(x + gap / 2, y + gap * 1.2f, x + w - gap / 2, y + keyHeight))
                x += w
            }
            y += keyHeight
        }
        requestLayout()
    }

    private fun layoutEmoji() {
        val categories = cats()
        emojiCategory = emojiCategory.coerceIn(0, categories.lastIndex)
        val emojis = categories[emojiCategory].second
        val cols = emojiCols
        emojiPage = emojiPage.coerceIn(0, pagesOf(emojiCategory) - 1)
        val cell = width / cols.toFloat()
        emojis.drop(emojiPage * perPage).take(perPage).forEachIndexed { i, e ->
            val r = i / cols
            val c = i % cols
            val top = stripHeight + r * keyHeight
            placed += Placed(Key(e), RectF(c * cell, top, (c + 1) * cell, top + keyHeight))
        }
        // Bottom row: abc, space, backspace (categories are tabs in the strip above).
        val y = stripHeight + emojiRows * keyHeight
        val u = width / 10f
        placed += Placed(Key("abc", KeyType.Letters), RectF(gap / 2, y + gap, 2 * u - gap / 2, y + keyHeight))
        placed += Placed(Key(" ", KeyType.Space), RectF(2 * u + gap / 2, y + gap, 8 * u - gap / 2, y + keyHeight))
        placed += Placed(Key("del", KeyType.Backspace), RectF(8 * u + gap / 2, y + gap, 10 * u - gap / 2, y + keyHeight))
    }

    // --- Drawing ----------------------------------------------------------------------------
    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(bg)
        drawStrip(canvas)
        if (emojiMode) {
            // The grid is drawn page by page so it can follow the finger; the bottom row is keys.
            canvas.save()
            canvas.clipRect(0f, stripHeight, width.toFloat(), stripHeight + emojiRows * keyHeight)
            drawEmojiPage(canvas, emojiCategory, emojiPage, dragX)
            if (dragX < 0) neighbour(emojiCategory, emojiPage, true)?.let { (c, pg) -> drawEmojiPage(canvas, c, pg, dragX + width) }
            if (dragX > 0) neighbour(emojiCategory, emojiPage, false)?.let { (c, pg) -> drawEmojiPage(canvas, c, pg, dragX - width) }
            canvas.restore()
            placed.forEach { if (it.key.type != KeyType.Char) drawKey(canvas, it) }
        } else {
            placed.forEach { drawKey(canvas, it) }
        }
        if (swiping) {
            trailPaint.color = accent
            trailPaint.strokeWidth = 6f * density
            canvas.drawPath(swipePath, trailPaint)
        }
        pressed?.let { p ->
            if (!swiping && alternates.isEmpty() && p.key.type == KeyType.Char && !emojiMode) drawPreview(canvas, p)
        }
        if (alternates.isNotEmpty()) drawAlternates(canvas)
    }

    private fun drawEmojiPage(canvas: Canvas, cat: Int, page: Int, offsetX: Float) {
        val emojis = cats()[cat].second
        val cell = width / emojiCols.toFloat()
        textPaint.typeface = font
        textPaint.textSize = 26f * density
        textPaint.color = fg
        emojis.drop(page * perPage).take(perPage).forEachIndexed { i, e ->
            val r = i / emojiCols
            val c = i % emojiCols
            val cx = offsetX + c * cell + cell / 2
            val cy = stripHeight + r * keyHeight + keyHeight / 2
            if (!pageDrag && pressed?.key?.label == e && cat == emojiCategory && page == emojiPage) {
                keyPaint.color = accent
                canvas.drawRect(cx - cell / 2, cy - keyHeight / 2, cx + cell / 2, cy + keyHeight / 2, keyPaint)
            }
            canvas.drawText(e, cx, cy + textPaint.textSize * 0.35f, textPaint)
        }
    }

    private fun drawStrip(canvas: Canvas) {
        textPaint.typeface = fontLight
        textPaint.textSize = 19f * density
        if (emojiMode) {
            // Category tabs (current one on an accent block) and one dot per page below them.
            val categories = cats()
            val w = width / categories.size.toFloat()
            categories.forEachIndexed { i, (icon, _) ->
                if (i == emojiCategory) {
                    keyPaint.color = accent
                    canvas.drawRect(i * w + gap / 2, gap, (i + 1) * w - gap / 2, stripHeight - 8 * density, keyPaint)
                }
                text(canvas, icon, i * w + w / 2, (stripHeight - 8 * density) / 2 + gap / 2, 18f, fg)
            }
            val pages = pagesOf(emojiCategory)
            if (pages > 1) {
                val r = 2f * density
                val step = 9f * density
                var x = width / 2f - (pages - 1) * step / 2
                for (i in 0 until pages) {
                    keyPaint.color = if (i == emojiPage) accent else subtle
                    canvas.drawCircle(x, stripHeight - 4 * density, r, keyPaint)
                    x += step
                }
            }
            return
        }
        if (suggestions.isEmpty()) {
            // The tool strip: evenly spaced glyphs.
            if (toolbar.isEmpty()) return
            val tw = width / toolbar.size.toFloat()
            toolbar.forEachIndexed { i, (glyph, _) -> text(canvas, glyph, tw * i + tw / 2, stripHeight / 2, 19f, subtle) }
            return
        }
        val w = width / suggestions.size.toFloat()
        suggestions.forEachIndexed { i, s ->
            textPaint.color = if (i == highlighted) accent else fg
            val label = TextUtilsEllipsize(s, w - 12 * density)
            canvas.drawText(label, w * i + w / 2, stripHeight * 0.66f, textPaint)
        }
    }

    private fun TextUtilsEllipsize(s: String, max: Float): String {
        if (textPaint.measureText(s) <= max) return s
        var t = s
        while (t.length > 1 && textPaint.measureText("$t…") > max) t = t.dropLast(1)
        return "$t…"
    }

    private fun drawKey(canvas: Canvas, p: Placed) {
        val k = p.key
        val isPressed = pressed == p && !swiping
        val special = k.type != KeyType.Char && k.type != KeyType.Space && k.type != KeyType.Period && k.type != KeyType.Comma
        keyPaint.color = when {
            isPressed -> accent
            emojiMode && k.type == KeyType.Char -> bg
            k.type == KeyType.SymbolsPage && emojiMode && k.text == "cat:$emojiCategory" -> accent
            k.type == KeyType.Shift && shiftState > 0 -> if (shiftState == 2) accent else keyBg
            special -> specialBg
            else -> keyBg
        }
        canvas.drawRect(p.rect, keyPaint)
        val color = if (isPressed) Color.WHITE else fg
        val cx = p.rect.centerX()
        val cy = p.rect.centerY()
        textPaint.color = color
        textPaint.typeface = font
        when (k.type) {
            KeyType.Backspace -> drawBackspace(canvas, p.rect, color)
            KeyType.Shift -> drawShift(canvas, p.rect, color, shiftState)
            KeyType.Enter -> {
                val label = enterLabel
                if (label != null) text(canvas, label, cx, cy, 15f, color) else drawEnter(canvas, p.rect, color)
            }
            KeyType.Space -> {
                textPaint.typeface = fontLight
                text(canvas, spaceLabel, cx, cy, 14f, if (isPressed) Color.WHITE else subtle)
            }
            KeyType.Emoji -> text(canvas, "☺", cx, cy, 22f, color)
            KeyType.Language -> drawGlobe(canvas, p.rect, color)
            KeyType.SymbolsPage -> text(canvas, k.label, cx, cy, if (emojiMode) 20f else 15f, color)
            else -> {
                val size = if (emojiMode) 26f else if (k.label.length > 1) 16f else 25f
                text(canvas, k.label, cx, cy, size, color)
                // Hint that a key has accents/alternates (small dot, like the WP long-press cue).
                if (!emojiMode && k.type == KeyType.Char && k.label.length == 1 && KeyboardLayouts.alternatesFor(language, k.label[0]).isNotEmpty()) {
                    keyPaint.color = subtle
                    canvas.drawCircle(p.rect.right - 6 * density, p.rect.top + 6 * density, 1.5f * density, keyPaint)
                }
            }
        }
    }

    private fun text(canvas: Canvas, s: String, cx: Float, cy: Float, sizeSp: Float, color: Int) {
        textPaint.textSize = sizeSp * density
        textPaint.color = color
        val fm = textPaint.fontMetrics
        canvas.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2, textPaint)
    }

    private fun drawPreview(canvas: Canvas, p: Placed) {
        val w = max(p.rect.width() * 1.3f, 44 * density)
        val h = keyHeight * 1.15f
        val left = (p.rect.centerX() - w / 2).coerceIn(0f, width - w)
        val top = max(0f, p.rect.top - h - 4 * density)
        keyPaint.color = accent
        canvas.drawRect(left, top, left + w, top + h, keyPaint)
        text(canvas, p.key.label, left + w / 2, top + h / 2, 28f, Color.WHITE)
    }

    private fun showAlternates(p: Placed) {
        val alt = KeyboardLayouts.alternatesFor(language, p.key.label.first())
        if (alt.isEmpty()) return
        alternates = listOf(p.key.label) + alt.map { it.toString() }
        val cell = 40f * density
        val w = cell * alternates.size
        val left = (p.rect.centerX() - cell / 2).coerceIn(0f, max(0f, width - w))
        val top = max(0f, p.rect.top - keyHeight - 6 * density)
        altRect = RectF(left, top, left + w, top + keyHeight)
        altSelected = 0
        feedback(this)
        invalidate()
    }

    private fun drawAlternates(canvas: Canvas) {
        keyPaint.color = if (dark) 0xFF3A3A3A.toInt() else 0xFFF2F2F2.toInt()
        canvas.drawRect(altRect, keyPaint)
        val cell = altRect.width() / alternates.size
        alternates.forEachIndexed { i, s ->
            if (i == altSelected) {
                keyPaint.color = accent
                canvas.drawRect(altRect.left + i * cell, altRect.top, altRect.left + (i + 1) * cell, altRect.bottom, keyPaint)
            }
            text(canvas, s, altRect.left + i * cell + cell / 2, altRect.centerY(), 22f, if (i == altSelected) Color.WHITE else fg)
        }
    }

    private fun drawBackspace(canvas: Canvas, r: RectF, color: Int) {
        iconPaint.color = color
        val s = 10f * density
        val cx = r.centerX(); val cy = r.centerY()
        val path = Path().apply {
            moveTo(cx - s * 1.3f, cy); lineTo(cx - s * 0.6f, cy - s * 0.8f); lineTo(cx + s * 1.3f, cy - s * 0.8f)
            lineTo(cx + s * 1.3f, cy + s * 0.8f); lineTo(cx - s * 0.6f, cy + s * 0.8f); close()
        }
        canvas.drawPath(path, iconPaint)
        canvas.drawLine(cx - s * 0.1f, cy - s * 0.35f, cx + s * 0.7f, cy + s * 0.35f, iconPaint)
        canvas.drawLine(cx + s * 0.7f, cy - s * 0.35f, cx - s * 0.1f, cy + s * 0.35f, iconPaint)
    }

    private fun drawShift(canvas: Canvas, r: RectF, color: Int, state: Int) {
        iconPaint.color = color
        val s = 10f * density
        val cx = r.centerX(); val cy = r.centerY()
        val path = Path().apply {
            moveTo(cx, cy - s); lineTo(cx + s, cy); lineTo(cx + s * 0.45f, cy); lineTo(cx + s * 0.45f, cy + s * 0.8f)
            lineTo(cx - s * 0.45f, cy + s * 0.8f); lineTo(cx - s * 0.45f, cy); lineTo(cx - s, cy); close()
        }
        if (state > 0) {
            keyPaint.color = color
            canvas.drawPath(path, keyPaint)
        } else {
            canvas.drawPath(path, iconPaint)
        }
        if (state == 2) canvas.drawLine(cx - s * 0.6f, cy + s * 1.2f, cx + s * 0.6f, cy + s * 1.2f, iconPaint)
    }

    private fun drawEnter(canvas: Canvas, r: RectF, color: Int) {
        iconPaint.color = color
        val s = 10f * density
        val cx = r.centerX(); val cy = r.centerY()
        val path = Path().apply {
            moveTo(cx + s, cy - s * 0.8f); lineTo(cx + s, cy + s * 0.3f); lineTo(cx - s, cy + s * 0.3f)
            moveTo(cx - s * 0.5f, cy - s * 0.2f); lineTo(cx - s, cy + s * 0.3f); lineTo(cx - s * 0.5f, cy + s * 0.8f)
        }
        canvas.drawPath(path, iconPaint)
    }

    private fun drawGlobe(canvas: Canvas, r: RectF, color: Int) {
        iconPaint.color = color
        val s = 9f * density
        val cx = r.centerX(); val cy = r.centerY()
        canvas.drawCircle(cx, cy, s, iconPaint)
        canvas.drawLine(cx - s, cy, cx + s, cy, iconPaint)
        canvas.drawOval(cx - s * 0.45f, cy - s, cx + s * 0.45f, cy + s, iconPaint)
    }

    // --- Touch ------------------------------------------------------------------------------
    private fun keyAt(x: Float, y: Float): Placed? =
        placed.firstOrNull { it.rect.contains(x, y) } ?: placed.minByOrNull {
            val dx = max(0f, max(it.rect.left - x, x - it.rect.right))
            val dy = max(0f, max(it.rect.top - y, y - it.rect.bottom))
            dx * dx + dy * dy
        }?.takeIf { y >= stripHeight }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.getX(e.actionIndex)
        val y = e.getY(e.actionIndex)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // A second finger while the first is still down: finish the first key (fast typing).
                if (e.actionMasked == MotionEvent.ACTION_POINTER_DOWN) release(commit = true)
                if (y < stripHeight) {
                    stripTap(x)
                    return true
                }
                val k = keyAt(x, y) ?: return true
                pressed = k
                downX = x; downY = y
                swiping = false; spaceDragging = false; spaceSteps = 0
                swipePath.reset(); swipePath.moveTo(x, y)
                swipeKeys.setLength(0)
                if (k.key.type == KeyType.Char && k.key.label.length == 1) swipeKeys.append(k.key.label)
                feedback(this)
                handler.postDelayed(longPress, 380)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val p = pressed ?: return true
                val mx = e.getX(0)
                val my = e.getY(0)
                if (alternates.isNotEmpty()) {
                    val cell = altRect.width() / alternates.size
                    altSelected = ((mx - altRect.left) / cell).toInt().coerceIn(0, alternates.lastIndex)
                    invalidate()
                    return true
                }
                val dist = hypot(mx - downX, my - downY)
                // Emoji grid: a sideways drag moves the pages.
                if (emojiMode && p.key.type == KeyType.Char && (pageDrag || (abs(mx - downX) > 14 * density && abs(mx - downX) > abs(my - downY)))) {
                    handler.removeCallbacks(longPress)
                    pageDrag = true
                    val raw = mx - downX
                    val hasNext = neighbour(emojiCategory, emojiPage, true) != null
                    val hasPrev = neighbour(emojiCategory, emojiPage, false) != null
                    // Resist at the first and last page.
                    dragX = if ((raw < 0 && !hasNext) || (raw > 0 && !hasPrev)) raw * 0.25f else raw
                    invalidate()
                    return true
                }
                when {
                    p.key.type == KeyType.Space && (spaceDragging || abs(mx - downX) > 16 * density) -> {
                        handler.removeCallbacks(longPress)
                        spaceDragging = true
                        val steps = ((mx - downX) / (12 * density)).toInt()
                        if (steps != spaceSteps) {
                            listener.onCursor(steps - spaceSteps)
                            spaceSteps = steps
                        }
                    }
                    p.key.type == KeyType.Backspace && mx - downX < -40 * density -> {
                        handler.removeCallbacks(longPress)
                        stopRepeat()
                    }
                    swipeEnabled && lettersPage && !emojiMode && p.key.type == KeyType.Char && (swiping || dist > 22 * density) -> {
                        handler.removeCallbacks(longPress)
                        swiping = true
                        for (h in 0 until e.historySize) swipeTo(e.getHistoricalX(0, h), e.getHistoricalY(0, h))
                        swipeTo(mx, my)
                        invalidate()
                    }
                    !swiping && keyAt(mx, my) != p && p.key.type == KeyType.Char && !swipeEnabled -> {
                        // Slide to a neighbouring key before lifting.
                        pressed = keyAt(mx, my)
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> release(commit = true, upX = x)
            MotionEvent.ACTION_CANCEL -> release(commit = false)
        }
        return true
    }

    private fun swipeTo(x: Float, y: Float) {
        swipePath.lineTo(x, y)
        val k = keyAt(x, y) ?: return
        if (k.key.type != KeyType.Char || k.key.label.length != 1) return
        // Only count a key when the finger is near its centre, to avoid grazing neighbours.
        val near = abs(x - k.rect.centerX()) < k.rect.width() * 0.42f && abs(y - k.rect.centerY()) < k.rect.height() * 0.42f
        if (near && (swipeKeys.isEmpty() || swipeKeys.last().toString() != k.key.label)) swipeKeys.append(k.key.label)
    }

    private fun release(commit: Boolean, upX: Float = downX) {
        handler.removeCallbacks(longPress)
        val p = pressed
        pressed = null
        if (repeating) {
            stopRepeat()
            invalidate()
            return
        }
        if (pageDrag) {
            pageDrag = false
            settlePages(upX - downX)
            alternates = emptyList()
            invalidate()
            return
        }
        if (commit && p != null) {
            when {
                alternates.isNotEmpty() -> {
                    alternates.getOrNull(altSelected)?.let { listener.onText(it) }
                }
                swiping -> listener.onSwipe(swipeKeys.toString())
                spaceDragging -> {}
                p.key.type == KeyType.Backspace && upX - downX < -40 * density -> listener.onDeleteWord()
                p.key.type == KeyType.SymbolsPage && emojiMode -> {
                    settle?.cancel()
                    dragX = 0f
                    emojiCategory = p.key.text.removePrefix("cat:").toIntOrNull() ?: 0
                    emojiPage = 0
                    layoutKeys()
                }
                emojiMode && p.key.type == KeyType.Char -> {
                    listener.onText(p.key.text)
                    recents.add(p.key.text)
                }
                else -> listener.onKey(p.key)
            }
        }
        alternates = emptyList()
        swiping = false
        spaceDragging = false
        swipePath.reset()
        invalidate()
    }

    /** Finishes a page drag: snaps to the next/previous page (or back) with a short slide. */
    private fun settlePages(dx: Float) {
        val forward = dx < 0
        val target = neighbour(emojiCategory, emojiPage, forward)?.takeIf { abs(dx) > width * 0.18f }
        val end = if (target == null) 0f else if (forward) -width.toFloat() else width.toFloat()
        settle?.cancel()
        settle = android.animation.ValueAnimator.ofFloat(dragX, end).apply {
            duration = 180
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { dragX = it.animatedValue as Float; invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (target != null) {
                        emojiCategory = target.first
                        emojiPage = target.second
                    }
                    dragX = 0f
                    layoutKeys()
                    invalidate()
                }
            })
            start()
        }
    }

    private fun stripTap(x: Float) {
        if (emojiMode) {
            // The strip holds the category tabs.
            settle?.cancel()
            dragX = 0f
            emojiCategory = (x / (width / cats().size.toFloat())).toInt().coerceIn(0, cats().lastIndex)
            emojiPage = 0
            feedback(this)
            layoutKeys()
            invalidate()
            return
        }
        if (suggestions.isEmpty()) {
            if (toolbar.isEmpty()) return
            val t = (x / (width / toolbar.size.toFloat())).toInt().coerceIn(0, toolbar.lastIndex)
            feedback(this)
            listener.onToolbar(toolbar[t].second)
            return
        }
        val i = (x / (width / suggestions.size.toFloat())).toInt().coerceIn(0, suggestions.lastIndex)
        feedback(this)
        listener.onSuggestion(i, suggestions[i])
    }

    private fun startRepeat() {
        repeating = true
        handler.post(repeat)
    }

    private fun stopRepeat() {
        repeating = false
        handler.removeCallbacks(repeat)
    }

    fun haptic() {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    @Suppress("unused")
    private fun clamp(v: Float, lo: Float, hi: Float) = min(max(v, lo), hi)
}
