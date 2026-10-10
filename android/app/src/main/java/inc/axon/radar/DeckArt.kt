package inc.axon.radar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.LineHeightSpan
import android.util.TypedValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import inc.axon.radar.data.Move
import inc.axon.radar.data.Text
import inc.axon.radar.ui.Ink
import inc.axon.radar.ui.Palette
import inc.axon.radar.ui.Shape
import inc.axon.radar.ui.Tones
import inc.axon.radar.ui.glyphOf
import inc.axon.radar.ui.typeColor
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The app's deck as a picture, for the carousel widget. A widget cannot use the app's typeface, so the deck
 * is drawn here the way the app draws it (ui/Deck.kt): the same cards, type, marks and spacing, sized from
 * this phone's screen as the app sizes them and set the way Compose sets them, then scaled as a whole to
 * fit the widget. On a widget as big as the app's screen the picture is the app's deck, pixel for pixel.
 */
object DeckArt {
    /** The app's top line is 44dp tall; the deck's first card opens 14dp under it. */
    const val TOP = 44f
    private const val MARK_ASPECT = 2f / 1.8660254f
    private val BLACK = Palette.Black.toArgb()
    private val WHITE = Palette.White.toArgb()

    /**
     * Where the deck is drawn. Lengths are in dp. The deck is the app's deck on a phone [phone] dp wide, drawn at
     * [scale] on a screen [screenW] dp wide: the cards are centred on it and the dots hang from its right edge.
     * The picture starts [left] dp across and at the top of the deck box, under the top line ([top] tall); [boxH]
     * of it shows, and the dots keep [inset] clear at the bottom (the app's tab bar). [chrome] scales the top line
     * and the controls.
     */
    class Plan(
        val den: Float, val phone: Float, val scale: Float, val screenW: Float, val left: Float,
        val boxH: Float, val inset: Float, val top: Float, val chrome: Float,
        val widthPx: Int, val heightPx: Int,
    ) {
        /** The app's card: 0.68 of the screen wide and 1.36 times as tall, in the app's pixels. */
        val cardWdp = phone * 0.68f
        val cardHdp = cardWdp * 1.36f
        val cardWpx = (cardWdp * den).roundToInt()
        val cardHpx = (cardHdp * den).roundToInt()
        /** A folded card above the open one shows this much; a waiting card below shows [medium]. */
        val small = cardHdp * 0.075f
        val medium = cardHdp * 0.29f
        /** The cards' left edge in the picture, px. */
        val cardX = (((screenW - cardWdp * scale) / 2f - left) * den).roundToInt()

        fun px(dp: Float) = (dp * den).roundToInt()
        /** A height in the app's deck box, in dp, as px down the picture. */
        fun y(boxDp: Float) = (boxDp * scale * den).roundToInt()
    }

    /**
     * The deck in widget [d]: the app's deck as it is on this phone, scaled so the widget holds what the app
     * shows under its top line at rest: three folded cards, the open card and the name of the card after it.
     * From Android 12 the top line and the controls take the same scale, so the widget is the app in small.
     */
    fun forWidget(ctx: Context, d: Dims): Plan {
        val dm = ctx.resources.displayMetrics
        val den = dm.density
        val phone = (min(dm.widthPixels, dm.heightPixels) / den).coerceIn(320f, 480f)
        val cardW = phone * 0.68f
        val cardH = cardW * 1.36f
        val live = Build.VERSION.SDK_INT >= 31
        val byHeight = if (live) d.h / (TOP + 14f + 1.395f * cardH) else (d.h - 42f) / (14f + 1.395f * cardH)
        val byWidth = d.w / (cardW + 88f) // the controls take 44dp at each side
        val s = minOf(1f, byHeight, byWidth).coerceAtLeast(0.25f)
        val top = if (live) TOP * s else 42f
        // The picture starts at the stack card's corner, 40dp across and under the top line, and runs to the
        // stack's right edge, 4dp in from the widget's; it is as tall as the card's top 7/8, a little past
        // the bottom of the widget.
        val (_, ah) = CarouselWidget.area(d, top)
        return Plan(den, phone, s, d.w.toFloat(), 40f, d.h - top, 0f, top, if (live) s else 1f,
            ((d.w - 44f) * den).roundToInt().coerceAtLeast(1), (ah * den).roundToInt().coerceAtLeast(1))
    }

    /** The deck as the app lays it out on a phone screen [w] x [h] dp, for checking the two against each other. */
    fun forScreen(den: Float, w: Float, h: Float, inset: Float): Plan =
        Plan(den, w, 1f, w, 0f, h - TOP, inset, TOP, 1f, (w * den).roundToInt(), ((h - TOP) * den).roundToInt())

    /** Top edge of each card in the deck box, in the app's dp, with card [focus] open, as Deck() places them at rest. */
    fun tops(n: Int, focus: Int, p: Plan): FloatArray {
        val ys = FloatArray(n)
        if (n == 0) return ys
        val k = focus.coerceIn(0, n - 1)
        ys[k] = 14f + p.small * min(k, 3)
        for (i in k + 1 until n) ys[i] = ys[i - 1] + if (i - 1 == k) p.cardHdp else p.medium
        for (i in k - 1 downTo 0) ys[i] = ys[i + 1] - p.small
        return ys
    }

    /** The deck with card [focus] open: folded cards above it, the cards still to come below, and the dots. */
    fun deck(ctx: Context, deck: List<Move>, focus: Int, ink: Ink, p: Plan): Bitmap {
        val bmp = Bitmap.createBitmap(p.widthPx, p.heightPx, Bitmap.Config.ARGB_8888)
        bmp.density = ctx.resources.displayMetrics.densityDpi
        val c = Canvas(bmp)
        c.drawColor(BLACK)
        if (deck.isEmpty()) return bmp
        val t = Typo(ctx)
        val k = focus.coerceIn(0, deck.lastIndex)
        val ys = tops(deck.size, k, p)
        // Later cards lie on earlier ones, as in the app; a card wholly under the next one is skipped.
        deck.forEachIndexed { i, m ->
            val y = p.y(ys[i])
            val hidden = i + 1 < deck.size && (ys[i + 1] * p.scale) < -4f
            if (!hidden && y < p.heightPx && y + p.cardHpx * p.scale > 0) {
                c.save()
                c.translate(p.cardX.toFloat(), y.toFloat())
                c.scale(p.scale, p.scale)
                card(c, m, ink, t, p)
                c.restore()
            }
        }
        dots(c, deck, k, ys, ink, p)
        return bmp
    }

    /**
     * The app's top line across a widget [widthDp] wide: the mark, the date and count in small capitals, and the
     * white chip with what is new today.
     */
    fun topLine(ctx: Context, line: String, fresh: Int, widthDp: Float, p: Plan): Bitmap {
        val w = (widthDp * p.den).roundToInt().coerceAtLeast(1)
        val h = p.px(p.top).coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.density = ctx.resources.displayMetrics.densityDpi
        val c = Canvas(bmp)
        c.drawColor(BLACK)
        val t = Typo(ctx)
        c.scale(p.chrome, p.chrome)
        // The app's Row: 18dp in from each side and 14dp down, on a screen widthDp / chrome wide.
        val full = (widthDp / p.chrome * p.den).roundToInt()
        val start = p.px(18f)
        val rowW = full - 2 * start
        val rowTop = p.px(14f)
        val markH = p.px(18f)
        val markW = (18f * MARK_ASPECT * p.den).roundToInt()
        val chip = if (fresh > 0) t.text("+$fresh NEW", t.caps(BLACK), Int.MAX_VALUE, 1) else null
        val chipW = chip?.let { it.width + 2 * p.px(6f) } ?: 0
        val chipH = chip?.let { it.height + 2 * p.px(2f) } ?: 0
        val label = t.layout(line.uppercase(), t.caps(Palette.White.copy(alpha = 0.75f).toArgb()), (rowW - markW - p.px(10f) - chipW).coerceAtLeast(1), 1)
        val rowH = maxOf(markH, label.height, chipH)
        ctx.getDrawable(R.drawable.logo_mark)?.mutate()?.let { mark ->
            mark.setTint(WHITE)
            val y = rowTop + center(rowH, markH)
            mark.setBounds(start, y, start + markW, y + markH)
            mark.draw(c)
        }
        draw(c, label, start + markW + p.px(10f), rowTop + center(rowH, label.height))
        if (chip != null) {
            val x = start + rowW - chipW
            val y = rowTop + center(rowH, chipH)
            val r = 2f * p.den
            c.drawRoundRect(x.toFloat(), y.toFloat(), (x + chipW).toFloat(), (y + chipH).toFloat(), r, r, fill(WHITE))
            draw(c, chip, x + p.px(6f), y + p.px(2f))
        }
        return bmp
    }

    /** One card's share of the picture: the stretch of it that shows, from its top edge to the next card's. */
    class Zone(val index: Int, val top: Int, val bottom: Int)

    fun zones(n: Int, focus: Int, p: Plan): List<Zone> {
        if (n == 0) return emptyList()
        val ys = tops(n, focus, p)
        val shown = min(p.px(p.boxH), p.heightPx)
        val out = ArrayList<Zone>()
        for (i in 0 until n) {
            val a = p.y(ys[i]).coerceAtLeast(0)
            val b = (if (i + 1 < n) p.y(ys[i + 1]) else p.y(ys[i] + p.cardHdp)).coerceAtMost(shown)
            if (b > a) out += Zone(i, a, b)
        }
        return out
    }

    /** What a card says, for screen readers. */
    fun describe(m: Move): String = "${m.subject}. ${m.typeLabel}, ${m.figure}. ${m.title}. ${Text.dayYear(m.date)}"

    // The card, as Card() in ui/Deck.kt draws it, in the app's pixels with its top-left at the origin.
    private fun card(c: Canvas, m: Move, ink: Ink, t: Typo, p: Plan) {
        val w = p.cardWpx.toFloat()
        val h = p.cardHpx.toFloat()
        val bg = ink.page(m.type)
        c.save()
        c.clipPath(rounded(w, h, 3f * p.den))
        c.drawColor(bg.toArgb())
        val pad = p.px(10f)
        val name = t.fit(m.subject, ink.display(bg).toArgb(), 46f, 24f, 2, p.cardWpx - 2 * pad)
        draw(c, name, pad, pad)
        val panelTop = pad + name.height + p.px(10f)
        c.save()
        c.translate(pad.toFloat(), panelTop.toFloat())
        panel(c, m, ink, t, p, bg, p.cardWpx - 2 * pad, p.cardHpx - pad - panelTop)
        c.restore()
        if (ink.mono) {
            // Compose's border: a stroke of whole pixels, inside the edge, its corners eased by half of it.
            val sw = ceil(p.den)
            val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = sw
                color = (if (ink.dark(bg)) Tones.Edge else Palette.Black).toArgb()
            }
            val r = 3f * p.den - sw / 2
            c.drawRoundRect(sw / 2, sw / 2, w - sw / 2, h - sw / 2, r, r, edge)
        }
        c.restore()
    }

    // The block under the name: the kind at the top, and the figure, title and date at the bottom.
    private fun panel(c: Canvas, m: Move, ink: Ink, t: Typo, p: Plan, bg: Color, w: Int, h: Int) {
        val lined = ink.lined(m.type)
        c.save()
        c.clipPath(rounded(w.toFloat(), h.toFloat(), 2f * p.den))
        c.drawColor(ink.panel(bg).toArgb())
        if (lined) hatch(c, ink.panelLines(bg).toArgb(), 6f * p.den, p.den, w.toFloat(), h.toFloat())
        val pad = p.px(12f)
        val innerW = w - 2 * pad
        val innerH = h - 2 * pad
        val g = p.px(8f)
        val gap = p.px(6f)
        val kind = listOfNotNull(m.typeLabel, m.regulator?.takeIf { Text.money(m.amount) != null }).joinToString(" · ").uppercase()
        val label = t.text(kind, t.caps(bg.toArgb()), innerW - g - gap, 1)
        val rowH = max(g, label.height)
        glyph(c, glyphOf(m.type), bg.toArgb(), pad.toFloat(), (pad + center(rowH, g)).toFloat(), g.toFloat(), lined, p.den)
        draw(c, label, pad + g + gap, pad + center(rowH, label.height))

        val on = ink.onPanel(bg)
        val figure = t.fit(m.figure, bg.toArgb(), 64f, 26f, 1, innerW)
        val title = t.text(m.title, t.small13(on.toArgb()), innerW, 3)
        val date = t.text(Text.dayYear(m.date).uppercase(), t.caps(on.copy(alpha = 0.5f).toArgb()), innerW, 1)
        val gap8 = p.px(8f)
        val gap6 = p.px(6f)
        var y = pad + innerH - (figure.height + gap8 + title.height + gap6 + date.height)
        draw(c, figure, pad, y)
        y += figure.height + gap8
        draw(c, title, pad, y)
        y += title.height + gap6
        draw(c, date, pad, y)
        c.restore()
    }

    // One mark per card on the right, level with the card's top: a dot in the move's colour (ringed in white for
    // the open card), or in black and white the move's glyph.
    private fun dots(c: Canvas, deck: List<Move>, k: Int, ys: FloatArray, ink: Ink, p: Plan) {
        val s = p.scale
        val lowest = max(6f * s, p.boxH - (p.inset + 24f) * s)
        deck.forEachIndexed { i, m ->
            val on = i == k
            val x = ((p.screenW - (22f + if (on) 2f else 0f) * s - p.left) * p.den).roundToInt()
            val y = (((ys[i] + 16f) * s).coerceIn(6f * s, lowest) * p.den).roundToInt()
            c.save()
            c.translate(x.toFloat(), y.toFloat())
            c.scale(s, s)
            if (ink.mono) {
                val w = p.px(if (on) 11f else 7f).toFloat()
                glyph(c, glyphOf(m.type), if (on) WHITE else Palette.White.copy(alpha = 0.72f).toArgb(), 0f, 0f, w, ink.lined(m.type), p.den)
            } else {
                // As the app: the colour clipped to a circle, and for the open card a white ring inside the same clip.
                val d = p.px(if (on) 10f else 6f).toFloat()
                c.clipPath(rounded(d, d, d / 2))
                c.drawRect(0f, 0f, d, d, fill(typeColor(m.type).toArgb()))
                if (on) {
                    val sw = ceil(1.5f * p.den)
                    val r = d / 2 - sw / 2
                    c.drawRoundRect(sw / 2, sw / 2, d - sw / 2, d - sw / 2, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE; strokeWidth = sw; color = WHITE
                    })
                }
            }
            c.restore()
        }
    }

    /** The app's Glyph(): diamond, square or dot, filled, or [hollow] as an outline that keeps the same size. */
    private fun glyph(c: Canvas, shape: Shape, color: Int, x: Float, y: Float, w: Float, hollow: Boolean, onePx: Float) {
        val paint = fill(color)
        val cx = x + w / 2
        val cy = y + w / 2
        val stroke = max(w * 0.16f, onePx)
        val grow = if (hollow) -stroke / 2 else 0f
        if (hollow) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
        }
        val k = (w + 2 * grow) / w
        val half = w / 2 * k
        when (shape) {
            Shape.Square -> {
                val l = cx - half * 0.8f
                val tp = cy - half * 0.8f
                val side = w * 0.8f * k
                c.drawRect(l, tp, l + side, tp + side, paint)
            }
            Shape.Dot -> c.drawCircle(cx, cy, half * 0.9f, paint)
            Shape.Diamond -> c.drawPath(Path().apply {
                moveTo(cx, cy - half); lineTo(cx + half, cy); lineTo(cx, cy + half); lineTo(cx - half, cy); close()
            }, paint)
        }
    }

    /** The app's hatch(): lines x + y = k * gap over the block, for the second kind of each family in black and white. */
    private fun hatch(c: Canvas, color: Int, gap: Float, width: Float, right: Float, bottom: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = width }
        c.save()
        c.clipRect(0f, 0f, right, bottom)
        val last = ceil((right + bottom) / gap).toInt()
        for (k in 0..last) {
            val x = k * gap
            c.drawLine(x - bottom, bottom, x, 0f, paint)
        }
        c.restore()
    }

    private fun rounded(w: Float, h: Float, r: Float) = Path().apply { addRoundRect(0f, 0f, w, h, r, r, Path.Direction.CW) }
    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private fun draw(c: Canvas, l: StaticLayout, x: Int, y: Int) {
        c.save()
        c.translate(x.toFloat(), y.toFloat())
        l.draw(c)
        c.restore()
    }

    /** Compose's Alignment.CenterVertically. */
    private fun center(space: Int, size: Int) = ((space - size) / 2f).roundToInt()

    /** One of the app's text styles, resolved to pixels. [topRatio] is LineHeightStyle's alignment: 0.5 centre, -1 proportional. */
    private class Style(val face: Typeface, val size: Float, val lineHeight: Float, val spacing: Float, val topRatio: Float, val color: Int)

    /** The app's type (Type in ui/Theme.kt), set the way Compose sets text on Android. */
    private class Typo(ctx: Context) {
        private val dm = ctx.resources.displayMetrics
        private val faces = Faces.of(ctx)

        private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, dm)
        fun display(size: Float, color: Int) = Style(faces.extraBold, sp(size), sp(size * 0.94f), -0.035f, 0.5f, color)
        fun caps(color: Int) = Style(faces.bold, sp(11f), sp(14f), 0.08f, -1f, color)
        fun small13(color: Int) = Style(faces.medium, sp(13f), sp(15f), 0f, -1f, color)

        private fun paint(st: Style) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = st.face; textSize = st.size; letterSpacing = st.spacing; color = st.color
        }

        private fun spanned(text: String, st: Style): Spanned = SpannableString(text).apply {
            setSpan(LineHeight(st.lineHeight, text.length, st.topRatio), 0, text.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        }

        /** Compose's max intrinsic width, as a text measures it: rounded up, and half a pixel more when letter-spaced. */
        fun intrinsic(text: String, st: Style): Int {
            val sp = spanned(text, st)
            var w = ceil(Layout.getDesiredWidth(sp, 0, sp.length, paint(st)))
            if (w != 0f && st.spacing != 0f) w += 0.5f
            return ceil(w).toInt()
        }

        /** Text laid out [width] px wide, at most [maxLines] lines, ending in an ellipsis if it runs over. */
        fun layout(text: String, st: Style, width: Int, maxLines: Int): StaticLayout {
            val sp = spanned(text, st)
            val w = width.coerceAtLeast(1)
            val b = StaticLayout.Builder.obtain(sp, 0, sp.length, paint(st), w)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .setJustificationMode(Layout.JUSTIFICATION_MODE_NONE)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setEllipsizedWidth(w)
            if (Build.VERSION.SDK_INT >= 28) b.setUseLineSpacingFromFallbacks(true)
            return b.build()
        }

        /** A text as BasicText sets it: at its own width when it fits on a line, else at the width it is given. */
        fun text(text: String, st: Style, maxWidth: Int, maxLines: Int): StaticLayout =
            layout(text, st, min(intrinsic(text, st), maxWidth), maxLines)

        /** The app's FitTitle(): the largest display size, in steps of 2, at which no word breaks and the lines fit. */
        fun fit(text: String, color: Int, maxSize: Float, minSize: Float, maxLines: Int, width: Int): StaticLayout {
            val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
            var size = maxSize
            while (size > minSize) {
                val st = display(size, color)
                val widest = words.maxOfOrNull { intrinsic(it, st) } ?: 0
                val lines = layout(text, st, min(intrinsic(text, st), width), Int.MAX_VALUE).lineCount
                if (widest <= width && lines <= maxLines) break
                size -= 2f
            }
            return text(text, display(size, color), width, maxLines)
        }
    }

    /** The app's Inter Tight, loaded once. */
    private class Faces(val extraBold: Typeface, val bold: Typeface, val medium: Typeface) {
        companion object {
            @Volatile private var cached: Faces? = null
            fun of(ctx: Context): Faces = cached ?: Faces(
                ctx.resources.getFont(R.font.intertight_extrabold),
                ctx.resources.getFont(R.font.intertight_bold),
                ctx.resources.getFont(R.font.intertight_medium),
            ).also { cached = it }
        }
    }

    /**
     * Compose's LineHeightStyleSpan with Trim.Both: every line [lineHeight] apart (rounded up), the first line
     * keeping its natural top and the last its natural bottom; a single line keeps its natural height.
     */
    private class LineHeight(private val lineHeight: Float, private val endIndex: Int, private val topRatio: Float) : LineHeightSpan {
        private var firstAscent = Int.MIN_VALUE
        private var ascent = 0
        private var descent = 0
        private var lastDescent = 0

        override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int, lineHeight: Int, fm: Paint.FontMetricsInt) {
            val current = fm.descent - fm.ascent
            if (current <= 0) return
            val first = start == 0
            val last = end == endIndex
            if (first && last) return
            if (firstAscent == Int.MIN_VALUE) {
                val target = ceil(this.lineHeight).toInt()
                val diff = target - current
                val ratio = if (topRatio == -1f) abs(fm.ascent.toFloat()) / current else topRatio
                val descentDiff = if (diff <= 0) ceil(diff * ratio).toInt() else ceil(diff * (1f - ratio)).toInt()
                descent = fm.descent + descentDiff
                ascent = descent - target
                firstAscent = fm.ascent
                lastDescent = fm.descent
            }
            fm.ascent = if (first) firstAscent else ascent
            fm.descent = if (last) lastDescent else descent
        }
    }
}
