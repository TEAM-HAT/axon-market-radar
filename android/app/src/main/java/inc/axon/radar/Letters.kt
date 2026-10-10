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
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The app's typeface (Inter Tight) and type styles (Type in ui/Theme.kt), set the way Compose sets text on
 * Android: the same sizes, letter spacing and line heights. Widgets cannot use the typeface, so the widgets
 * show it as pictures drawn with these.
 */
internal object Letters {
    /** A style resolved to pixels. [topRatio] is LineHeightStyle's alignment: 0.5 centre, -1 proportional. */
    class Style(val face: Typeface, val size: Float, val lineHeight: Float, val spacing: Float, val topRatio: Float, val color: Int)

    private fun sp(ctx: Context, v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, ctx.resources.displayMetrics)

    /** Huge names and headings. */
    fun display(ctx: Context, size: Float, color: Int) =
        Style(faces(ctx).extraBold, sp(ctx, size), sp(ctx, size * 0.94f), -0.035f, 0.5f, color)

    /** Small capitals. */
    fun caps(ctx: Context, color: Int) = Style(faces(ctx).bold, sp(ctx, 11f), sp(ctx, 14f), 0.08f, -1f, color)

    /** Captions and the small text under names. */
    fun small(ctx: Context, color: Int) = Style(faces(ctx).medium, sp(ctx, 12f), sp(ctx, 15f), 0f, -1f, color)

    /** A count raised after a label. */
    fun sup(ctx: Context, size: Float, color: Int) = Style(faces(ctx).bold, sp(ctx, size), sp(ctx, size * 1.1f), 0f, -1f, color)

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
    fun fit(ctx: Context, text: String, color: Int, maxSize: Float, minSize: Float, maxLines: Int, width: Int): StaticLayout {
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        var size = maxSize
        while (size > minSize) {
            val st = display(ctx, size, color)
            val widest = words.maxOfOrNull { intrinsic(it, st) } ?: 0
            val lines = layout(text, st, min(intrinsic(text, st), width), Int.MAX_VALUE).lineCount
            if (widest <= width && lines <= maxLines) break
            size -= 2f
        }
        return text(text, display(ctx, size, color), width, maxLines)
    }

    /**
     * Text as an alpha mask the width of its longest line (and a little more for the last letter's overhang), for a
     * widget to tint. Text is shaded by its colour's lightness, so it is drawn in the colour it will be tinted.
     */
    fun mask(ctx: Context, l: StaticLayout): Bitmap {
        var widest = 0f
        for (i in 0 until l.lineCount) widest = maxOf(widest, l.getLineWidth(i))
        val w = (ceil(widest).toInt() + 4).coerceIn(1, l.width + 4)
        val bmp = Bitmap.createBitmap(w, l.height.coerceAtLeast(1), Bitmap.Config.ALPHA_8)
        bmp.density = ctx.resources.displayMetrics.densityDpi
        l.draw(Canvas(bmp))
        return bmp
    }

    fun draw(c: Canvas, l: StaticLayout, x: Int, y: Int) {
        c.save()
        c.translate(x.toFloat(), y.toFloat())
        l.draw(c)
        c.restore()
    }

    /** Compose's Alignment.CenterVertically. */
    fun center(space: Int, size: Int) = ((space - size) / 2f).roundToInt()

    fun opaque(color: Int) = color or (0xFF shl 24)

    /** The app's Inter Tight, loaded once. */
    class Faces(val extraBold: Typeface, val bold: Typeface, val medium: Typeface)

    @Volatile private var cached: Faces? = null
    fun faces(ctx: Context): Faces = cached ?: Faces(
        ctx.resources.getFont(R.font.intertight_extrabold),
        ctx.resources.getFont(R.font.intertight_bold),
        ctx.resources.getFont(R.font.intertight_medium),
    ).also { cached = it }

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
