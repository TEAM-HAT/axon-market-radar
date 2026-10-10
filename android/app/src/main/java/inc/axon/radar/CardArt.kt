package inc.axon.radar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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
import androidx.compose.ui.graphics.toArgb
import inc.axon.radar.data.Move
import inc.axon.radar.data.Text
import inc.axon.radar.ui.Palette
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The app's type for the carousel widget. Widgets cannot use the app's typeface, so the parts that carry it,
 * the top line and each card's name and figure, are drawn here as pictures, set the way the app's Compose
 * text is set: the same sizes, spacing and line heights, and the same fitting of names to the card.
 * A card's name and figure come as alpha masks the widget tints, so each costs one byte a pixel.
 */
object CardArt {
    /** The app's top line is 44dp tall. */
    const val TOP = 44f
    private const val MARK_ASPECT = 2f / 1.8660254f
    private val BLACK = Palette.Black.toArgb()
    private val WHITE = Palette.White.toArgb()

    /**
     * The app's top line across a widget [widthDp] wide: the mark, the date and count in small capitals, and the
     * white chip with what is new today.
     */
    fun topLine(ctx: Context, line: String, fresh: Int, widthDp: Float): Bitmap {
        val den = ctx.resources.displayMetrics.density
        fun px(dp: Float) = (dp * den).roundToInt()
        val w = px(widthDp).coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, px(TOP), Bitmap.Config.ARGB_8888)
        bmp.density = ctx.resources.displayMetrics.densityDpi
        val c = Canvas(bmp)
        c.drawColor(BLACK)
        val t = Typo(ctx)
        // The app's Row: 18dp in from each side and 14dp down.
        val start = px(18f)
        val rowW = w - 2 * start
        val rowTop = px(14f)
        val markH = px(18f)
        val markW = (18f * MARK_ASPECT * den).roundToInt()
        val chip = if (fresh > 0) t.text("+$fresh NEW", t.caps(BLACK), Int.MAX_VALUE, 1) else null
        val chipW = chip?.let { it.width + 2 * px(6f) } ?: 0
        val chipH = chip?.let { it.height + 2 * px(2f) } ?: 0
        val label = t.layout(line.uppercase(), t.caps(Palette.White.copy(alpha = 0.75f).toArgb()), (rowW - markW - px(10f) - chipW).coerceAtLeast(1), 1)
        val rowH = maxOf(markH, label.height, chipH)
        ctx.getDrawable(R.drawable.logo_mark)?.mutate()?.let { mark ->
            mark.setTint(WHITE)
            val y = rowTop + center(rowH, markH)
            mark.setBounds(start, y, start + markW, y + markH)
            mark.draw(c)
        }
        draw(c, label, start + markW + px(10f), rowTop + center(rowH, label.height))
        if (chip != null) {
            val x = start + rowW - chipW
            val y = rowTop + center(rowH, chipH)
            val r = 2f * den
            c.drawRoundRect(x.toFloat(), y.toFloat(), (x + chipW).toFloat(), (y + chipH).toFloat(), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WHITE })
            draw(c, chip, x + px(6f), y + px(2f))
        }
        return bmp
    }

    /**
     * A card's name as the app sets it on a card [cardPx] wide (inside its 10dp padding): up to 46sp, on two lines.
     * Text is shaded by its colour's lightness, so the mask is drawn in [color], the colour it will be tinted.
     */
    fun name(ctx: Context, m: Move, cardPx: Int, color: Int): Bitmap {
        val inner = cardPx - 2 * px(ctx, 10f)
        return mask(ctx, Typo(ctx).fit(m.subject, opaque(color), 46f, 24f, 2, inner))
    }

    /**
     * A card's figure as the app sets it in the black block (12dp in from the card's padding), on one line: up to
     * [maxSp], which is 64sp in the app; the widget's shorter cards take it a little smaller.
     */
    fun figure(ctx: Context, m: Move, cardPx: Int, color: Int, maxSp: Float = 64f): Bitmap {
        val inner = cardPx - 2 * px(ctx, 10f) - 2 * px(ctx, 12f)
        return mask(ctx, Typo(ctx).fit(m.figure, opaque(color), maxSp, 26f, 1, inner))
    }

    private fun opaque(color: Int) = color or (0xFF shl 24)

    /** What a card says, for screen readers. */
    fun describe(m: Move): String = "${m.subject}. ${m.typeLabel}, ${m.figure}. ${m.title}. ${Text.dayYear(m.date)}"

    private fun px(ctx: Context, dp: Float) = (dp * ctx.resources.displayMetrics.density).roundToInt()

    /** Text as an alpha mask the width of its longest line (and a little more for the last letter's overhang). */
    private fun mask(ctx: Context, l: StaticLayout): Bitmap {
        var widest = 0f
        for (i in 0 until l.lineCount) widest = maxOf(widest, l.getLineWidth(i))
        val w = (ceil(widest).toInt() + 4).coerceIn(1, l.width + 4)
        val bmp = Bitmap.createBitmap(w, l.height.coerceAtLeast(1), Bitmap.Config.ALPHA_8)
        bmp.density = ctx.resources.displayMetrics.densityDpi
        l.draw(Canvas(bmp))
        return bmp
    }

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
    private class Faces(val extraBold: Typeface, val bold: Typeface) {
        companion object {
            @Volatile private var cached: Faces? = null
            fun of(ctx: Context): Faces = cached ?: Faces(
                ctx.resources.getFont(R.font.intertight_extrabold),
                ctx.resources.getFont(R.font.intertight_bold),
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
