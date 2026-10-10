package inc.axon.radar

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import inc.axon.radar.data.Company
import inc.axon.radar.data.Move
import inc.axon.radar.data.Radar
import inc.axon.radar.data.Regulator
import inc.axon.radar.data.Store
import inc.axon.radar.data.Text
import inc.axon.radar.ui.Ink
import inc.axon.radar.ui.Palette
import inc.axon.radar.ui.Tones
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The widgets' parts, in the app's look: its grounds (black like the deck, the directory's light grey, and a
 * move's own colour), its type as pictures, the kind swatches, rows, tiles and boxes, the week's mix bar and the
 * month chart, and the lists that scroll.
 */
internal object Kit {
    val BLACK = Palette.Black.toArgb()
    val WHITE = Palette.White.toArgb()
    val MUTED = Palette.Muted.toArgb()
    val INK2 = Palette.Ink2.toArgb()

    /** What a widget, or a part of one, stands on. PAGE is a move's colour, as on the licences widget. */
    enum class Ground { BLACK, PAPER, PAGE }

    /** The kinds of move in the app's order, with their names one and many. */
    val KINDS = listOf("License", "Regulation", "Funding", "M&A", "Launch", "Partnership")
    private val ONE = mapOf("License" to "Licence", "Regulation" to "Rule", "Funding" to "Funding", "M&A" to "M&A", "Launch" to "Launch", "Partnership" to "Partnership")
    private val MANY = mapOf("License" to "Licences", "Regulation" to "Rules", "Funding" to "Funding", "M&A" to "M&A", "Launch" to "Launches", "Partnership" to "Partnerships")
    fun kindName(type: String, n: Int) = if (n == 1) ONE[type] ?: type else MANY[type] ?: type

    fun px(ctx: Context, dp: Float) = (dp * ctx.resources.displayMetrics.density).roundToInt()

    // ---- Type, as pictures -------------------------------------------------------------------------

    /** Huge display type on one line, as a mask, such as a count. */
    fun word(ctx: Context, text: String, sizeSp: Float, color: Int): Bitmap =
        Letters.mask(ctx, Letters.text(text, Letters.display(ctx, sizeSp, Letters.opaque(color)), Int.MAX_VALUE, 1))

    /** The app's FitTitle as a mask: the largest size from [maxSp] down at which [text] fits [lines] lines. */
    fun fit(ctx: Context, text: String, color: Int, maxSp: Float, minSp: Float, lines: Int, widthPx: Int): Bitmap =
        Letters.mask(ctx, Letters.fit(ctx, text, Letters.opaque(color), maxSp, minSp, lines, widthPx.coerceAtLeast(1)))

    /** A directory's title, the app's huge heading, with a count raised after it at half strength. */
    fun heading(ctx: Context, word: String, count: String?, color: Int, sizeSp: Float = 36f): Bitmap {
        val c = Letters.opaque(color)
        val w = Letters.text(word, Letters.display(ctx, sizeSp, c), Int.MAX_VALUE, 1)
        val n = count?.let { Letters.text(it, Letters.sup(ctx, sizeSp * 0.34f, c), Int.MAX_VALUE, 1) }
        val gap = px(ctx, 3f)
        val width = w.width + (n?.let { gap + it.width } ?: 0) + 4
        val bmp = Bitmap.createBitmap(width, w.height, Bitmap.Config.ALPHA_8)
        bmp.density = ctx.resources.displayMetrics.densityDpi
        val canvas = Canvas(bmp)
        Letters.draw(canvas, w, 0, 0)
        if (n != null) {
            // The count's capitals start where the title's do.
            val capTop = w.getLineBaseline(0) - (0.7275f * Letters.display(ctx, sizeSp, c).size)
            val nCap = n.getLineBaseline(0) - 0.7275f * Letters.sup(ctx, sizeSp * 0.34f, c).size
            canvas.saveLayerAlpha(0f, 0f, width.toFloat(), w.height.toFloat(), 128)
            Letters.draw(canvas, n, w.width + gap, (capTop - nCap).roundToInt())
            canvas.restore()
        }
        return bmp
    }

    /** A headline with its **key phrases** set bold and bright, the rest a little quieter. */
    fun headline(raw: String, key: Int): Spanned {
        val b = SpannableStringBuilder()
        Text.runs(raw).forEach { (s, bold) ->
            val at = b.length
            b.append(s)
            if (bold) {
                b.setSpan(StyleSpan(Typeface.BOLD), at, b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                b.setSpan(ForegroundColorSpan(key), at, b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return b
    }

    // ---- The week's mix and the month chart ---------------------------------------------------------

    /** The 7-day window's moves by kind, in the app's order. */
    fun week(r: Radar): List<Pair<String, Int>> {
        val inWindow = r.moves.filter { r.windowStart != null && r.windowEnd != null && it.date >= r.windowStart && it.date <= r.windowEnd }
        return KINDS.map { k -> k to inWindow.count { it.type == k } }
    }

    private fun fill(ink: Ink, type: String) = ink.page(type).toArgb()

    /**
     * A kind's block of colour, with its fine lines in black and white. On a black [ground] a black block keeps
     * the hairline edge the app's black cards have on the deck.
     */
    private fun block(c: Canvas, ctx: Context, ink: Ink, type: String, l: Float, t: Float, r: Float, b: Float, gapDp: Float = 3.5f, ground: Int? = null) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill(ink, type) }
        c.drawRect(l, t, r, b, p)
        if (ground != null && Color(fill(ink, type)).luminance() < 0.2f && Color(ground).luminance() < 0.2f) {
            val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = px(ctx, 1f).toFloat(); color = Tones.Edge.toArgb() }
            val half = edge.strokeWidth / 2
            c.drawRect(l + half, t + half, r - half, b - half, edge)
        }
        if (ink.lined(type)) {
            val lines = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink.lines(ink.page(type)).toArgb(); strokeWidth = px(ctx, 1f).toFloat() }
            val gap = gapDp * ctx.resources.displayMetrics.density
            c.save()
            c.clipRect(l, t, r, b)
            val first = floor((l + t) / gap).toInt()
            val last = ceil((r + b) / gap).toInt()
            for (k in first..last) {
                val x = k * gap
                c.drawLine(x - b, b, x - t, t, lines)
            }
            c.restore()
        }
    }

    /**
     * The week's moves as one bar in the kinds' colours, with the biggest kinds named under it, as many as fit
     * [widthPx]. [ink] colours the kinds; [text] colours the names, on a ground of [ground].
     */
    fun mix(ctx: Context, counts: List<Pair<String, Int>>, ink: Ink, widthPx: Int, ground: Int, text: Int, legend: Boolean = true): Bitmap {
        val barH = px(ctx, 6f)
        val gap = px(ctx, 2f)
        val keyRowH = if (legend) px(ctx, 10f) + px(ctx, 14f) else 0
        val bmp = Bitmap.createBitmap(widthPx.coerceAtLeast(1), barH + keyRowH, Bitmap.Config.ARGB_8888)
        bmp.density = ctx.resources.displayMetrics.densityDpi
        val c = Canvas(bmp)
        c.drawColor(ground)
        val total = counts.sumOf { it.second }.coerceAtLeast(1)
        val shown = counts.filter { it.second > 0 }
        val room = widthPx - gap * (shown.size - 1).coerceAtLeast(0)
        var x = 0f
        shown.forEach { (k, n) ->
            val w = room * n / total.toFloat()
            block(c, ctx, ink, k, x, 0f, x + w, barH.toFloat(), 2.5f, ground)
            x += w + gap
        }
        if (legend) {
            // The kinds named in order of size: a key in the kind's colour and the count with the kind's name.
            var at = 0
            val y = barH + px(ctx, 10f)
            val key = px(ctx, 8f)
            for ((k, n) in shown.sortedByDescending { it.second }) {
                val l = Letters.text("$n ${kindName(k, n)}".uppercase(), Letters.caps(ctx, text), Int.MAX_VALUE, 1)
                val need = key + px(ctx, 5f) + l.width
                if (at + need > widthPx) break
                block(c, ctx, ink, k, at.toFloat(), (y + Letters.center(l.height, key)).toFloat(), (at + key).toFloat(), (y + Letters.center(l.height, key) + key).toFloat(), 2.5f, ground)
                Letters.draw(c, l, at + key + px(ctx, 5f), y)
                at += need + px(ctx, 12f)
            }
        }
        return bmp
    }

    /** The months the chart covers: the trends' months, else the last thirteen. */
    private fun months(r: Radar): List<String> = r.trends.months.map { it.ym }.ifEmpty {
        val end = LocalDate.now(); (12 downTo 0).map { end.minusMonths(it.toLong()) }.map { "%04d-%02d".format(it.year, it.monthValue) }
    }

    /**
     * The app's month chart: moves per month for the last thirteen months, each bar stacked in the colours of its
     * kinds, the current month outlined while it runs, its total and the busiest month's over their bars.
     */
    fun chart(ctx: Context, r: Radar, ink: Ink, widthPx: Int, heightPx: Int, ground: Int): Bitmap {
        val den = ctx.resources.displayMetrics.density
        val bmp = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        bmp.density = ctx.resources.displayMetrics.densityDpi
        val c = Canvas(bmp)
        c.drawColor(ground)
        val ms = months(r)
        val stacks = ms.map { ym -> KINDS.map { k -> r.moves.count { it.date.startsWith(ym) && it.type == k } } }
        val totals = stacks.map { it.sum() }
        val most = (totals.maxOrNull() ?: 0).coerceAtLeast(3)
        val labelH = 18 * den
        val topPad = 18 * den
        val base = heightPx - labelH
        val slot = widthPx / ms.size.toFloat()
        val bw = (slot * 0.62f).coerceAtMost(20 * den)
        c.drawLine(0f, base, widthPx.toFloat(), base, Paint().apply { color = Palette.Rule.toArgb(); strokeWidth = den })
        ms.forEachIndexed { i, ym ->
            val x = slot * i + (slot - bw) / 2
            var y = base
            stacks[i].forEachIndexed { j, n ->
                if (n > 0) {
                    val h = n / most.toFloat() * (base - topPad)
                    val gap = if (ink.mono && y < base) den else 0f
                    block(c, ctx, ink, KINDS[j], x, y - h, x + bw, y - gap)
                    y -= h
                }
            }
            if (i == ms.lastIndex && r.trends.partialLast && totals[i] > 0) {
                c.drawRect(x, y, x + bw, base, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = 1.5f * den; color = BLACK; pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
                })
            }
            if (totals[i] > 0 && (totals[i] == totals.maxOrNull() || i == ms.lastIndex)) {
                val t = Letters.text(totals[i].toString(), Letters.caps(ctx, BLACK), Int.MAX_VALUE, 1)
                Letters.draw(c, t, (x + bw / 2 - t.width / 2f).roundToInt(), (y - t.height - 3 * den).roundToInt())
            }
            val lab = Letters.text(Text.monthYear(ym).take(1), Letters.small(ctx, if (i == ms.lastIndex) BLACK else MUTED), Int.MAX_VALUE, 1)
            Letters.draw(c, lab, (x + bw / 2 - lab.width / 2f).roundToInt(), (base + 4 * den).roundToInt())
        }
        return bmp
    }

    /** The chart's key: a square of each kind's colour and its name, on as many rows as [widthPx] needs. */
    fun legend(ctx: Context, ink: Ink, widthPx: Int, ground: Int): Bitmap {
        val key = px(ctx, if (ink.mono) 11f else 9f)
        val labels = KINDS.map { k -> k to Letters.text(MANY[k] ?: k, Letters.small(ctx, INK2), Int.MAX_VALUE, 1) }
        val rowH = maxOf(key, labels.maxOf { it.second.height })
        val rowGap = px(ctx, 4f)
        // Each kind goes where the last one ended, or starts a new row when it would not fit.
        val at = ArrayList<Pair<Int, Int>>()
        var x = 0
        var row = 0
        for ((_, l) in labels) {
            val need = key + px(ctx, 4f) + l.width
            if (x > 0 && x + need > widthPx) { row++; x = 0 }
            at += x to row
            x += need + px(ctx, 11f)
        }
        val bmp = Bitmap.createBitmap(widthPx.coerceAtLeast(1), (row + 1) * rowH + row * rowGap, Bitmap.Config.ARGB_8888)
        bmp.density = ctx.resources.displayMetrics.densityDpi
        val c = Canvas(bmp)
        c.drawColor(ground)
        labels.forEachIndexed { i, (k, l) ->
            val (lx, r) = at[i]
            val top = r * (rowH + rowGap)
            val y = top + Letters.center(rowH, key)
            block(c, ctx, ink, k, lx.toFloat(), y.toFloat(), (lx + key).toFloat(), (y + key).toFloat())
            Letters.draw(c, l, lx + key + px(ctx, 4f), top + Letters.center(rowH, l.height))
        }
        return bmp
    }

    /** The size, in sp and at most [sp], at which [text] in the widgets' [face] fits [widthPx] on one line. */
    fun fitSp(ctx: Context, text: String, sp: Float, face: Typeface, widthPx: Int): Float {
        val paint = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face }
        var size = sp
        while (size > 8f) {
            paint.textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, size, ctx.resources.displayMetrics)
            if (paint.measureText(text) <= widthPx) break
            size -= 0.5f
        }
        return size
    }

    // ---- Swatches, rows, tiles and boxes ------------------------------------------------------------

    /** A move's swatch on a row of colour [ground]: its kind's colour, or a step away when that is the row's own, as the app's. */
    fun swatchRes(ink: Ink, type: String?, ground: Color): Int {
        val own = ink.page(type)
        val lined = ink.lined(type)
        if (own != ground) return when (own) {
            Palette.Yellow -> R.drawable.sw_yellow
            Palette.Royal -> R.drawable.sw_royal
            Palette.Crimson -> R.drawable.sw_crimson
            Palette.Violet -> R.drawable.sw_violet
            Palette.Sage -> R.drawable.sw_sage
            Palette.Slate -> R.drawable.sw_slate
            Tones.Paper -> if (lined) R.drawable.sw_paper_lined else R.drawable.sw_paper
            Tones.Silver -> if (lined) R.drawable.sw_silver_lined else R.drawable.sw_silver
            else -> if (lined) R.drawable.sw_black_lined else R.drawable.sw_black
        }
        if (!ink.mono) return R.drawable.sw_yellowdeep // only the licence page shares a move's colour
        return if (ink.dark(ground)) (if (lined) R.drawable.sw_blackstep_lined else R.drawable.sw_blackstep)
        else (if (lined) R.drawable.sw_paperstep_lined else R.drawable.sw_paperstep)
    }

    /** The glyph's colour in a swatch: black on colours; in black and white, the tone's reading colour. */
    private fun glyphColor(ink: Ink, type: String?, ground: Color): Int {
        if (!ink.mono) return BLACK
        val own = ink.page(type)
        val fill = if (own != ground) own else ink.tile(ground)
        return ink.reading(fill).toArgb()
    }

    /** The ids of one move row: a list's, or one of the dashboard's three. */
    class RowIds(val item: Int, val row: Int, val sw: Int, val glyph: Int, val title: Int, val meta: Int, val date: Int)
    val ROW = RowIds(R.id.row_item, R.id.row, R.id.sw, R.id.sw_glyph, R.id.title, R.id.meta, R.id.date)
    val ROWS = listOf(
        RowIds(R.id.r1_row_item, R.id.r1_row, R.id.r1_sw, R.id.r1_sw_glyph, R.id.r1_title, R.id.r1_meta, R.id.r1_date),
        RowIds(R.id.r2_row_item, R.id.r2_row, R.id.r2_sw, R.id.r2_sw_glyph, R.id.r2_title, R.id.r2_meta, R.id.r2_date),
        RowIds(R.id.r3_row_item, R.id.r3_row, R.id.r3_sw, R.id.r3_sw_glyph, R.id.r3_title, R.id.r3_meta, R.id.r3_date),
    )

    /** A move as a row, as the app lists it: its swatch, the title, what and who and where, and the day. */
    fun bindRow(v: RemoteViews, ids: RowIds, m: Move, ink: Ink, ground: Ground, page: Color = Palette.Paper) {
        val row = rowColor(ink, ground)
        val (rowRes, strong, quiet) = when (ground) {
            Ground.BLACK -> Triple(R.drawable.row_night, WHITE, Palette.White.copy(alpha = 0.5f).toArgb())
            Ground.PAPER -> Triple(R.drawable.row_paper, BLACK, MUTED)
            Ground.PAGE -> Triple(if (ink.mono) R.drawable.row_paperstep else R.drawable.row_yellow, ink.reading(page).toArgb(), ink.secondary(page).toArgb())
        }
        v.setViewVisibility(ids.item, View.VISIBLE)
        v.setInt(ids.row, "setBackgroundResource", rowRes)
        v.setInt(ids.sw, "setBackgroundResource", swatchRes(ink, m.type, row))
        v.setImageViewResource(ids.glyph, CarouselWidget.glyphRes(m.type, false))
        v.setInt(ids.glyph, "setColorFilter", glyphColor(ink, m.type, row))
        v.setTextViewText(ids.title, m.title)
        v.setTextColor(ids.title, strong)
        v.setTextViewText(ids.meta, listOfNotNull(m.typeLabel, m.regulator ?: m.companyName, m.region).joinToString(" · "))
        v.setTextColor(ids.meta, quiet)
        v.setTextViewText(ids.date, Text.day(m.date))
        v.setTextColor(ids.date, quiet)
    }

    /** A row's own colour: the app's Row on light grey, the tab bar's black on black, the page's tile on a page. */
    private fun rowColor(ink: Ink, ground: Ground): Color = when (ground) {
        Ground.BLACK -> Palette.Night
        Ground.PAPER -> Palette.Row
        Ground.PAGE -> if (ink.mono) Tones.PaperStep else Color(0xFFD1C500)
    }

    /** One row of a list, opening its move. */
    fun rowItem(ctx: Context, m: Move, ink: Ink, ground: Ground, page: Color = Palette.Paper): Pair<Long, RemoteViews> {
        val v = RemoteViews(ctx.packageName, R.layout.widget_row)
        bindRow(v, ROW, m, ink, ground, page)
        v.setOnClickFillInIntent(R.id.row, CarouselWidget.fillIn("move", m.id))
        return m.id.hashCode().toLong() to v
    }

    /** A month's header in a list, as the app's: the month in small capitals and how many moves it had. */
    fun monthItem(ctx: Context, ym: String, n: Int, color: Int): Pair<Long, RemoteViews> {
        val v = RemoteViews(ctx.packageName, R.layout.widget_month)
        v.setTextViewText(R.id.month_label, Text.monthFull("$ym-01"))
        v.setTextColor(R.id.month_label, color)
        v.setTextViewText(R.id.month_n, n.toString())
        v.setTextColor(R.id.month_n, color)
        return "month-$ym".hashCode().toLong() to v
    }

    /** [moves] as rows under their months' headers, as the app lists them; [all] gives each month's count. */
    fun byMonth(ctx: Context, moves: List<Move>, all: List<Move>, ink: Ink, ground: Ground, page: Color = Palette.Paper): List<Pair<Long, RemoteViews>> {
        val counts = all.groupingBy { it.date.take(7) }.eachCount()
        val header = when (ground) {
            Ground.BLACK -> WHITE
            Ground.PAPER -> BLACK
            Ground.PAGE -> ink.reading(page).toArgb()
        }
        val out = ArrayList<Pair<Long, RemoteViews>>()
        var month: String? = null
        for (m in moves) {
            val ym = m.date.take(7)
            if (ym != month) {
                month = ym
                out += monthItem(ctx, ym, counts[ym] ?: 0, header)
            }
            out += rowItem(ctx, m, ink, ground, page)
        }
        return out
    }

    /** The ids of one of the three move tiles. */
    class TileIds(val tile: Int, val name: Int, val panel: Int, val fig: Int)
    val TILES = listOf(
        TileIds(R.id.t1, R.id.t1_name, R.id.t1_panel, R.id.t1_fig),
        TileIds(R.id.t2, R.id.t2_name, R.id.t2_panel, R.id.t2_fig),
        TileIds(R.id.t3, R.id.t3_name, R.id.t3_panel, R.id.t3_fig),
    )

    /** One of the newest moves as the app's tile, [widthPx] wide: its name on its colour, the figure in a black block. */
    fun bindTile(ctx: Context, v: RemoteViews, ids: TileIds, m: Move, ink: Ink, widthPx: Int) {
        val bg = ink.page(m.type)
        val lined = ink.lined(m.type)
        val inner = widthPx - 2 * px(ctx, 8f)
        v.setViewVisibility(ids.tile, View.VISIBLE)
        v.setInt(ids.tile, "setBackgroundResource", CarouselWidget.cardRes(ink, m.type))
        v.setImageViewBitmap(ids.name, fit(ctx, m.subject, ink.display(bg).toArgb(), 24f, 13f, 2, inner))
        v.setInt(ids.name, "setColorFilter", ink.display(bg).toArgb())
        v.setInt(ids.panel, "setBackgroundResource", CarouselWidget.panelRes(ink, bg, lined))
        v.setImageViewBitmap(ids.fig, fit(ctx, m.figure, bg.toArgb(), 24f, 11f, 1, inner - 2 * px(ctx, 6f)))
        v.setInt(ids.fig, "setColorFilter", bg.toArgb())
        v.setContentDescription(ids.tile, CardArt.describe(m))
    }

    /** The ids of one of the three regulator tiles. */
    class RegIds(val tile: Int, val n: Int, val name: Int, val where: Int)
    val REGS = listOf(
        RegIds(R.id.g1, R.id.g1_n, R.id.g1_name, R.id.g1_where),
        RegIds(R.id.g2, R.id.g2_n, R.id.g2_name, R.id.g2_where),
        RegIds(R.id.g3, R.id.g3_n, R.id.g3_name, R.id.g3_where),
    )

    /**
     * A regulator as the app's tile, [widthPx] wide: its moves, huge, then its name and where. Tiles alternate
     * black and paper, as in the app; on a paper page the light ones go a step darker.
     */
    fun bindReg(ctx: Context, v: RemoteViews, ids: RegIds, r: Regulator, i: Int, ink: Ink, widthPx: Int, numberSp: Float, page: Color? = null) {
        val dark = i % 2 == 0
        val light = if (page == Tones.Paper) R.drawable.tile_paperstep else R.drawable.tile_paper
        val fg = if (dark) WHITE else BLACK
        val inner = widthPx - 2 * px(ctx, 8f)
        v.setViewVisibility(ids.tile, View.VISIBLE)
        v.setInt(ids.tile, "setBackgroundResource", if (dark) R.drawable.tile_black else light)
        v.setImageViewBitmap(ids.n, word(ctx, r.total.toString().padStart(2, '0'), numberSp, fg))
        v.setInt(ids.n, "setColorFilter", fg)
        v.setImageViewBitmap(ids.name, fit(ctx, r.name, fg, 20f, 11f, 1, inner))
        v.setInt(ids.name, "setColorFilter", fg)
        v.setTextViewText(ids.where, r.where.ifEmpty { "Regulator" })
        v.setTextColor(ids.where, if (dark) Palette.White.copy(alpha = 0.6f).toArgb() else Palette.Black.copy(alpha = 0.55f).toArgb())
        v.setContentDescription(ids.tile, "${r.name}, ${Text.plural(r.total, "move", "moves")}")
    }

    /** The ids of a company box: a grid's, or one of the dashboard's three. */
    class BoxIds(val box: Int, val name: Int, val sub: Int, val initials: Int, val glyph: Int, val latest: Int)
    val BOX = BoxIds(R.id.box, R.id.box_name, R.id.box_sub, R.id.box_initials, R.id.box_glyph, R.id.box_latest)
    val BOXES = listOf(
        BoxIds(R.id.c1_box, R.id.c1_box_name, R.id.c1_box_sub, R.id.c1_box_initials, R.id.c1_box_glyph, R.id.c1_box_latest),
        BoxIds(R.id.c2_box, R.id.c2_box_name, R.id.c2_box_sub, R.id.c2_box_initials, R.id.c2_box_glyph, R.id.c2_box_latest),
        BoxIds(R.id.c3_box, R.id.c3_box_name, R.id.c3_box_sub, R.id.c3_box_initials, R.id.c3_box_glyph, R.id.c3_box_latest),
    )

    /** A company as the app's Watching box: in its latest move's colour, its name, its initials huge, and that move. */
    fun bindBox(ctx: Context, v: RemoteViews, ids: BoxIds, c: Company, latest: Move?, ink: Ink, initialsSp: Float, small: Boolean = false) {
        val bg = ink.page(latest?.type ?: "Partnership")
        val fg = ink.display(bg).toArgb()
        val quiet = ink.secondary(bg).toArgb()
        v.setInt(ids.box, "setBackgroundResource", CarouselWidget.cardRes(ink, latest?.type ?: "Partnership"))
        v.setTextViewText(ids.name, c.name)
        v.setTextColor(ids.name, fg)
        v.setTextViewText(ids.sub, listOfNotNull(c.segment, c.hqCountry).joinToString(" · "))
        v.setTextColor(ids.sub, quiet)
        v.setViewVisibility(ids.sub, if (small) View.GONE else View.VISIBLE)
        v.setImageViewBitmap(ids.initials, word(ctx, Text.initials(c.name), initialsSp, fg))
        v.setInt(ids.initials, "setColorFilter", fg)
        if (latest != null) {
            v.setImageViewResource(ids.glyph, CarouselWidget.glyphRes(latest.type, ink.lined(latest.type)))
            v.setInt(ids.glyph, "setColorFilter", fg)
            v.setViewVisibility(ids.glyph, View.VISIBLE)
        } else v.setViewVisibility(ids.glyph, View.GONE)
        v.setTextViewText(ids.latest, latest?.let { if (small) Text.day(it.date) else "${it.typeLabel} · ${Text.day(it.date)}" } ?: "No moves yet")
        v.setTextColor(ids.latest, quiet)
        v.setContentDescription(ids.box, "${c.name}${latest?.let { ", latest: ${it.typeLabel}, ${Text.day(it.date)}" } ?: ""}")
    }

    /** One box of the companies grid, opening the company. */
    fun boxItem(ctx: Context, c: Company, latest: Move?, ink: Ink): Pair<Long, RemoteViews> {
        val v = RemoteViews(ctx.packageName, R.layout.widget_box)
        bindBox(ctx, v, BOX, c, latest, ink, 44f)
        v.setOnClickFillInIntent(R.id.box, CarouselWidget.fillIn("company", c.id))
        return c.id.hashCode().toLong() to v
    }

    /** The most active companies: by moves in the last 30 days, then by moves in all. */
    fun active(r: Radar): List<Pair<Company, Move?>> {
        val since = LocalDate.parse((r.windowEnd ?: LocalDate.now().toString()).take(10)).minusDays(30).toString()
        return r.companies.map { c -> c to r.movesFor(c.id) }
            .sortedWith(compareByDescending<Pair<Company, List<Move>>> { (_, ms) -> ms.count { it.date >= since } }.thenByDescending { it.second.size })
            .map { (c, ms) -> c to ms.firstOrNull() }
    }

    // ---- Lists that scroll ------------------------------------------------------------------------

    /** Opens the app; each row or box fills in where. Mutable, so it can be filled in. */
    fun template(ctx: Context, code: Int): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).setData(Uri.parse("radar://open/list/$code"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(ctx, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }

    /**
     * Fills a widget's list. From Android 12 the items travel with the widget, so it scrolls without waiting for
     * any to load; before that, ListsService hands the same items to the launcher.
     */
    fun list(ctx: Context, v: RemoteViews, listId: Int, kind: String, widgetId: Int, code: Int, items: List<Pair<Long, RemoteViews>>) {
        v.setPendingIntentTemplate(listId, template(ctx, code))
        if (Build.VERSION.SDK_INT >= 31) {
            val b = RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(VIEW_TYPES)
            items.forEach { (id, rv) -> b.addItem(id, rv) }
            v.setRemoteAdapter(listId, b.build())
        } else {
            val svc = Intent(ctx, ListsService::class.java).putExtra(EXTRA_KIND, kind).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            svc.data = Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME))
            @Suppress("DEPRECATION")
            v.setRemoteAdapter(listId, svc)
        }
    }

    const val EXTRA_KIND = "inc.axon.radar.LIST"
    /** Lists hold rows and month headers; the companies grid, boxes. */
    const val VIEW_TYPES = 2

    /** A list's items by kind of widget, for the update and for the service alike. */
    fun items(ctx: Context, kind: String): List<Pair<Long, RemoteViews>> {
        val r = Store.cachedFast(ctx) ?: return emptyList()
        val ink = Ink.of(Store.look(ctx))
        return when (kind) {
            "brief" -> BriefWidget.items(ctx, r, ink)
            "moves" -> MovesWidget.items(ctx, r, ink)
            "licences" -> LicencesWidget.items(ctx, r, ink)
            "companies" -> CompaniesWidget.items(ctx, r, ink)
            else -> emptyList()
        }
    }

    /**
     * The widget as the launcher will lay it out at [d], measured here: the width is the widget's, and the
     * height its own ([d].h) when [exact], or as tall as its parts want when not.
     */
    fun measure(ctx: Context, v: RemoteViews, d: Dims, exact: Boolean): View {
        val root = v.apply(ctx, android.widget.FrameLayout(ctx))
        val w = px(ctx, d.w.toFloat())
        val h = px(ctx, d.h.toFloat())
        root.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            if (exact) View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY) else View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        return root
    }

    /** How tall a part of a measured widget is, with its margins, in px; 0 when it is not shown. */
    fun tall(root: View, id: Int): Int {
        val x = root.findViewById<View>(id) ?: return 0
        if (x.visibility == View.GONE) return 0
        val lp = x.layoutParams as? android.view.ViewGroup.MarginLayoutParams
        return x.measuredHeight + (lp?.topMargin ?: 0) + (lp?.bottomMargin ?: 0)
    }
}

/** Hands the launcher a widget's list before Android 12. */
class ListsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = ListsFactory(
        applicationContext, intent.getStringExtra(Kit.EXTRA_KIND) ?: "",
        intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID),
    )
}

class ListsFactory(private val ctx: Context, private val kind: String, private val id: Int) : RemoteViewsService.RemoteViewsFactory {
    private var items: List<Pair<Long, RemoteViews>> = emptyList()

    override fun onCreate() = load()
    override fun onDataSetChanged() = load()
    override fun onDestroy() {}
    private fun load() { items = Kit.items(ctx, kind) }

    override fun getCount(): Int = items.size
    override fun getViewAt(position: Int): RemoteViews = items[position.coerceIn(0, items.lastIndex)].second
    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = Kit.VIEW_TYPES
    override fun getItemId(position: Int): Long = items.getOrNull(position)?.first ?: position.toLong()
    override fun hasStableIds(): Boolean = true
}
