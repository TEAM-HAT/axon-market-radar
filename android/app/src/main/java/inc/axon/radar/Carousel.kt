package inc.axon.radar

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import inc.axon.radar.data.Move
import inc.axon.radar.data.Store
import inc.axon.radar.data.Text
import inc.axon.radar.ui.Ink
import inc.axon.radar.ui.Shape
import inc.axon.radar.ui.glyphOf
import org.json.JSONObject

/**
 * The app's deck as a home-screen widget: the cards above fold into strips, the open card shows the
 * move's figure, and the next cards wait below with their names showing. Tap the arrows, or the card
 * above or below, to flip; tap the open card to read it in the app. Colours follow the app's look.
 */
class CarouselWidget : RadarWidget() {
    override val layout = R.layout.widget_carousel
    override val monoLayout = R.layout.widget_carousel // every colour on it is set from code
    override val fallback = Dims(360, 420)
    override val code = 700

    override fun build(ctx: Context, brief: JSONObject?, d: Dims): RemoteViews =
        buildFor(ctx, brief, d, AppWidgetManager.INVALID_APPWIDGET_ID)

    override fun buildFor(ctx: Context, brief: JSONObject?, d: Dims, id: Int): RemoteViews {
        val v = RemoteViews(ctx.packageName, layout)
        val ink = Ink.of(Store.look(ctx))
        val radar = Store.cachedFast(ctx)
        val deck = radar?.deck.orEmpty()
        v.setOnClickPendingIntent(R.id.top, app(ctx, "home", null, code))

        // What fits around the open card: folded cards above (23dp each) and waiting ones below (70dp
        // each), dropped one at a time as the widget gets shorter, so the open card keeps its room.
        val (nPrev, nNext) = when {
            d.h >= 470 -> 2 to 2
            d.h >= 400 -> 2 to 1
            d.h >= 340 -> 1 to 1
            d.h >= 290 -> 0 to 1
            else -> 0 to 0
        }
        val cardW = (d.w - 40 - 40 - 18).toFloat()
        val openH = d.h - 42 - 12 - nPrev * 23 - nNext * 70

        if (radar == null || deck.isEmpty()) {
            v.setTextViewText(R.id.eyebrow, "Market Radar")
            v.setViewVisibility(R.id.chip, View.GONE)
            v.setViewVisibility(R.id.prev, View.GONE)
            v.setViewVisibility(R.id.next, View.GONE)
            v.setViewVisibility(R.id.controls, View.INVISIBLE)
            v.setTextViewText(R.id.name, "Market Radar")
            v.setTextViewText(R.id.label, "")
            v.setTextViewText(R.id.figure, "…")
            v.setTextViewText(R.id.title, if (radar == null) "Loading the latest moves…" else "Nothing on the radar yet.")
            v.setTextViewText(R.id.date, "")
            v.setTextViewText(R.id.pos, "")
            v.setTextViewText(R.id.of, "")
            v.setOnClickPendingIntent(R.id.open, app(ctx, "home", null, code + 1))
            return v
        }

        val i = position(ctx, id, deck.map { it.id })
        val m = deck[i]
        v.setTextViewText(R.id.eyebrow, "${Text.dowDay(radar.windowEnd)} · ${Text.plural(radar.count, "move", "moves")} in 7 days")
        v.setViewVisibility(R.id.chip, if (radar.newToday > 0) View.VISIBLE else View.GONE)
        v.setTextViewText(R.id.chip, "+${radar.newToday} new")

        // The open card, as the app draws it.
        val bg = ink.page(m.type)
        val lined = ink.lined(m.type)
        v.setInt(R.id.open, "setBackgroundResource", cardRes(ink, m.type))
        v.setTextViewText(R.id.name, m.subject)
        v.setTextColor(R.id.name, ink.display(bg).toArgb())
        // The name takes two lines only on a tall card; the block below gets what is left.
        val nameLines = if (openH >= 250) 2 else 1
        val nameSp = fit(ctx, m.subject, 38f, 20f, cardW, nameLines)
        v.setTextViewTextSize(R.id.name, TypedValue.COMPLEX_UNIT_SP, nameSp)
        v.setInt(R.id.name, "setMaxLines", nameLines)
        v.setInt(R.id.panel, "setBackgroundResource", panelRes(ink, bg, lined))
        glyph(v, R.id.glyph, m.type, bg, lined)
        v.setTextViewText(R.id.label, listOfNotNull(m.typeLabel, m.regulator?.takeIf { Text.money(m.amount) != null }).joinToString(" · "))
        v.setTextColor(R.id.label, bg.toArgb())
        v.setTextViewText(R.id.figure, m.figure)
        v.setTextColor(R.id.figure, bg.toArgb())
        v.setTextViewText(R.id.title, m.title)
        v.setTextColor(R.id.title, ink.onPanel(bg).toArgb())
        v.setTextViewText(R.id.date, Text.dayYear(m.date))
        v.setTextColor(R.id.date, ink.onPanel(bg).copy(alpha = 0.5f).toArgb())
        // Fit the block: the kind at the top, then the figure, the title and the date at the bottom.
        // Give up the third title line, then the date, then the second line, then shrink the figure.
        val scale = ctx.resources.configuration.fontScale
        val inner = openH - 18 - lineHeight(nameSp, scale) * linesOf(ctx, m.subject, nameSp, cardW).coerceIn(1, nameLines) - 8 - 20
        var figSp = fit(ctx, m.figure, 54f, 20f, cardW - 20, 1)
        var titleLines = 3
        var showDate = true
        fun need() = 13 + 8 + lineHeight(figSp, scale) + (if (titleLines > 0) 6 + titleLines * 15.5f * scale else 0f) + (if (showDate) 17 * scale else 0f)
        while (need() > inner) {
            when {
                titleLines > 2 -> titleLines--
                showDate -> showDate = false
                titleLines > 1 -> titleLines--
                figSp > 28f -> figSp -= 2f
                titleLines > 0 -> titleLines = 0
                figSp > 18f -> figSp -= 2f
                else -> break
            }
        }
        v.setTextViewTextSize(R.id.figure, TypedValue.COMPLEX_UNIT_SP, figSp)
        v.setInt(R.id.title, "setMaxLines", titleLines.coerceAtLeast(1))
        v.setViewVisibility(R.id.title, if (titleLines > 0) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.date, if (showDate) View.VISIBLE else View.GONE)
        v.setOnClickPendingIntent(R.id.open, app(ctx, "move", m.id, code + 1))

        // Folded above, waiting below; a tap on either brings it to the front. Slots that don't fit go;
        // slots that fit but have no card (at the ends of the deck) stay as empty space, so the open
        // card keeps its place while you flip.
        v.setViewVisibility(R.id.prev, if (nPrev > 0) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.next, if (nNext > 0) View.VISIBLE else View.GONE)
        if (nPrev >= 1) strip(ctx, v, ink, id, R.id.p1, R.id.p1_name, deck.getOrNull(i - 1)) else v.setViewVisibility(R.id.p1, View.GONE)
        if (nPrev >= 2) strip(ctx, v, ink, id, R.id.p2, R.id.p2_name, deck.getOrNull(i - 2)) else v.setViewVisibility(R.id.p2, View.GONE)
        val n1 = Slot(R.id.n1, R.id.n1_name, R.id.n1_panel, R.id.n1_glyph, R.id.n1_label)
        val n2 = Slot(R.id.n2, R.id.n2_name, R.id.n2_panel, R.id.n2_glyph, R.id.n2_label)
        if (nNext >= 1) waiting(ctx, v, ink, id, cardW, n1, deck.getOrNull(i + 1)) else v.setViewVisibility(R.id.n1, View.GONE)
        if (nNext >= 2) waiting(ctx, v, ink, id, cardW, n2, deck.getOrNull(i + 2)) else v.setViewVisibility(R.id.n2, View.GONE)

        // Up and down, the way into the app, and where in the deck this card sits.
        control(v, R.id.up, deck.getOrNull(i - 1)?.let { flip(ctx, id, it.id) })
        control(v, R.id.down, deck.getOrNull(i + 1)?.let { flip(ctx, id, it.id) })
        v.setOnClickPendingIntent(R.id.grid, app(ctx, "deck", m.id, code + 2))
        v.setTextViewText(R.id.pos, (i + 1).toString().padStart(2, '0'))
        v.setTextViewText(R.id.of, "/${deck.size}")
        return v
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == ACTION_FLIP) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val target = intent.getStringExtra(EXTRA_TARGET)
            val deck = Store.cachedFast(ctx)?.deck.orEmpty()
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID && target != null && deck.isNotEmpty()) {
                keep(ctx, id, deck.first().id, target)
                // A tap can arrive after the widget was removed; there is nothing to redraw then.
                runCatching { draw(ctx, AppWidgetManager.getInstance(ctx), id, Brief.cached(ctx)) }
            }
            return
        }
        super.onReceive(ctx, intent)
    }

    override fun onDeleted(ctx: Context, ids: IntArray) {
        val e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        ids.forEach { e.remove("carousel_$it").remove("carousel_${it}_head") }
        e.apply()
    }

    private class Slot(val card: Int, val name: Int, val panel: Int, val glyph: Int, val label: Int)

    private fun strip(ctx: Context, v: RemoteViews, ink: Ink, id: Int, card: Int, name: Int, m: Move?) {
        if (m == null) { v.setViewVisibility(card, View.INVISIBLE); return }
        val bg = ink.page(m.type)
        v.setViewVisibility(card, View.VISIBLE)
        v.setInt(card, "setBackgroundResource", stripRes(ink, m.type))
        v.setTextViewText(name, m.subject)
        v.setTextColor(name, ink.display(bg).toArgb())
        v.setOnClickPendingIntent(card, flip(ctx, id, m.id))
    }

    private fun waiting(ctx: Context, v: RemoteViews, ink: Ink, id: Int, cardW: Float, s: Slot, m: Move?) {
        if (m == null) { v.setViewVisibility(s.card, View.INVISIBLE); return }
        val bg = ink.page(m.type)
        val lined = ink.lined(m.type)
        v.setViewVisibility(s.card, View.VISIBLE)
        v.setInt(s.card, "setBackgroundResource", stripRes(ink, m.type))
        v.setTextViewText(s.name, m.subject)
        v.setTextColor(s.name, ink.display(bg).toArgb())
        v.setTextViewTextSize(s.name, TypedValue.COMPLEX_UNIT_SP, fit(ctx, m.subject, 28f, 18f, cardW, 1))
        v.setInt(s.panel, "setBackgroundResource", panelRes(ink, bg, lined))
        glyph(v, s.glyph, m.type, bg, lined)
        v.setTextViewText(s.label, m.typeLabel)
        v.setTextColor(s.label, bg.toArgb())
        v.setOnClickPendingIntent(s.card, flip(ctx, id, m.id))
    }

    private fun glyph(v: RemoteViews, view: Int, type: String?, color: Color, hollow: Boolean) {
        v.setImageViewResource(view, when (glyphOf(type)) {
            Shape.Square -> if (hollow) R.drawable.g_sq_o else R.drawable.g_sq
            Shape.Dot -> if (hollow) R.drawable.g_dot_o else R.drawable.g_dot
            Shape.Diamond -> if (hollow) R.drawable.g_dia_o else R.drawable.g_dia
        })
        v.setInt(view, "setColorFilter", color.toArgb())
    }

    private fun control(v: RemoteViews, view: Int, target: PendingIntent?) {
        v.setInt(view, "setImageAlpha", if (target != null) 220 else 70)
        if (target != null) v.setOnClickPendingIntent(view, target)
    }

    companion object {
        const val ACTION_FLIP = "inc.axon.radar.action.FLIP"
        const val EXTRA_TARGET = "inc.axon.radar.TARGET"
        private const val PREFS = "radar"
        private val BLACK_FACE: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)

        /** The card each widget shows: kept by move id, and back to the top when a new deck arrives. */
        fun position(ctx: Context, id: Int, ids: List<String>): Int {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (ids.isEmpty() || p.getString("carousel_${id}_head", null) != ids.first()) return 0
            return ids.indexOf(p.getString("carousel_$id", null)).coerceAtLeast(0)
        }

        fun keep(ctx: Context, id: Int, head: String, target: String) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("carousel_$id", target).putString("carousel_${id}_head", head).apply()
        }

        fun flip(ctx: Context, id: Int, target: String): PendingIntent {
            val intent = Intent(ctx, CarouselWidget::class.java).setAction(ACTION_FLIP)
                .setData(Uri.parse("radar://flip/$id/" + Uri.encode(target)))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra(EXTRA_TARGET, target)
            return PendingIntent.getBroadcast(ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        /** How many lines [text] takes at [sp] in [widthDp], in the black display face. */
        fun linesOf(ctx: Context, text: String, sp: Float, widthDp: Float): Int {
            val dm = ctx.resources.displayMetrics
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = BLACK_FACE; letterSpacing = -0.04f
                textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, dm)
            }
            return StaticLayout.Builder.obtain(text, 0, text.length, paint, (widthDp * dm.density).toInt().coerceAtLeast(1))
                .setIncludePad(false).build().lineCount
        }

        /** Height in dp of one line of the black display face at [sp], without font padding. */
        fun lineHeight(sp: Float, fontScale: Float): Float = sp * fontScale * 1.18f

        /** The page colour as a card: the colour itself, or in black and white its tone with a hairline edge. */
        fun cardRes(ink: Ink, type: String?): Int = if (!ink.mono) when (type) {
            "License" -> R.drawable.card_yellow
            "Regulation" -> R.drawable.card_royal
            "Funding" -> R.drawable.card_crimson
            "M&A" -> R.drawable.card_violet
            "Launch" -> R.drawable.card_sage
            else -> R.drawable.card_slate
        } else when (glyphOf(type)) {
            Shape.Diamond -> R.drawable.card_paper
            Shape.Square -> R.drawable.card_black
            Shape.Dot -> R.drawable.card_silver
        }

        /** The same, as the slice of a folded or waiting card: rounded only at the top. */
        fun stripRes(ink: Ink, type: String?): Int = if (!ink.mono) when (type) {
            "License" -> R.drawable.strip_yellow
            "Regulation" -> R.drawable.strip_royal
            "Funding" -> R.drawable.strip_crimson
            "M&A" -> R.drawable.strip_violet
            "Launch" -> R.drawable.strip_sage
            else -> R.drawable.strip_slate
        } else when (glyphOf(type)) {
            Shape.Diamond -> R.drawable.strip_paper
            Shape.Square -> R.drawable.strip_black
            Shape.Dot -> R.drawable.strip_silver
        }

        fun panelRes(ink: Ink, bg: Color, lined: Boolean): Int =
            if (ink.dark(bg)) (if (lined) R.drawable.panel_paper_lined else R.drawable.panel_paper)
            else (if (lined) R.drawable.panel_black_lined else R.drawable.panel_black)

        /**
         * The largest size, in sp, at which [text] fits [widthDp] in [maxLines] lines without breaking a word,
         * measured in the same system face the widget draws with, the way the app sizes its names.
         */
        fun fit(ctx: Context, text: String, maxSp: Float, minSp: Float, widthDp: Float, maxLines: Int): Float {
            val dm = ctx.resources.displayMetrics
            val width = (widthDp * dm.density).toInt().coerceAtLeast(1)
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = BLACK_FACE; letterSpacing = -0.04f }
            val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
            var sp = maxSp
            while (sp > minSp) {
                paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, dm)
                val widest = words.maxOfOrNull { paint.measureText(it) } ?: 0f
                val lines = StaticLayout.Builder.obtain(text, 0, text.length, paint, width).setIncludePad(false).build().lineCount
                if (widest <= width && lines <= maxLines) break
                sp -= 1f
            }
            return sp
        }
    }
}
