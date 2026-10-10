package inc.axon.radar

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
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
 * The app's deck as a home-screen widget: the cards before the open one folded into strips above it,
 * the open card with its figure, and the waiting cards stacked below with their names showing. The
 * stack scrolls under your finger; a tap on a stacked or folded card brings it to the front, as in
 * the app, and a tap on the open card reads it in the app. Colours follow the app's look.
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

        // The stack below the open card: a list the launcher scrolls, filled by CarouselService for this
        // widget. Taps on its cards come back here to bring that card to the front.
        val svc = Intent(ctx, CarouselService::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        svc.data = Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME))
        v.setRemoteAdapter(R.id.list, svc)
        v.setPendingIntentTemplate(R.id.list, focusTemplate(ctx, id))
        v.setScrollPosition(R.id.list, 0)
        // After the last card the stack is empty; it says so and points to older moves in the app.
        v.setEmptyView(R.id.list, R.id.end)
        v.setOnClickPendingIntent(R.id.end, app(ctx, "moves", null, code + 3))

        if (radar == null || deck.isEmpty()) {
            v.setTextViewText(R.id.eyebrow, "Market Radar")
            v.setViewVisibility(R.id.chip, View.GONE)
            v.setViewVisibility(R.id.prev, View.GONE)
            v.setViewVisibility(R.id.controls, View.INVISIBLE)
            v.setTextViewText(R.id.name, "Market Radar")
            v.setTextViewText(R.id.label, "")
            v.setTextViewText(R.id.pos, "")
            v.setTextViewText(R.id.figure, "…")
            v.setTextViewText(R.id.title, if (radar == null) "Loading the latest moves…" else "Nothing on the radar yet.")
            v.setTextViewText(R.id.date, "")
            v.setOnClickPendingIntent(R.id.open, app(ctx, "home", null, code + 1))
            return v
        }

        val i = position(ctx, id, deck.map { it.id })
        val m = deck[i]
        v.setTextViewText(R.id.eyebrow, "${Text.dowDay(radar.windowEnd)} · ${Text.plural(radar.count, "move", "moves")} in 7 days")
        v.setViewVisibility(R.id.chip, if (radar.newToday > 0) View.VISIBLE else View.GONE)
        v.setTextViewText(R.id.chip, "+${radar.newToday} new")

        // Folded above: the cards before the open one, as many as fit, each a tap away from the front.
        // At the top of the deck there are none and the open card sits right under the header, as in the app.
        val room = when {
            d.h >= 400 -> 2
            d.h >= 340 -> 1
            else -> 0
        }
        val shown = minOf(room, i)
        v.setViewVisibility(R.id.prev, if (shown > 0) View.VISIBLE else View.GONE)
        folded(ctx, v, ink, id, R.id.p1, R.id.p1_name, deck.getOrNull(i - 1), shown >= 1)
        folded(ctx, v, ink, id, R.id.p2, R.id.p2_name, deck.getOrNull(i - 2), shown >= 2)

        // The open card takes 58 parts of what is left, the stack 42, as the app's deck divides it.
        val cardW = (d.w - 40 - 12 - 18).toFloat()
        val openH = ((d.h - 42 - shown * 23) * 0.58f).toInt()
        openCard(ctx, v, ink, deck, i, openH, cardW)
        v.setOnClickPendingIntent(R.id.open, app(ctx, "move", m.id, code + 1))

        // Up and down move the front card; the grid opens the app's deck on this card.
        control(v, R.id.up, deck.getOrNull(i - 1)?.let { focus(ctx, id, it.id) })
        control(v, R.id.down, deck.getOrNull(i + 1)?.let { focus(ctx, id, it.id) })
        v.setOnClickPendingIntent(R.id.grid, app(ctx, "deck", m.id, code + 2))
        return v
    }

    /** Redraws the frame and asks the launcher to reload the stack: a new front card, data, look or size. */
    override fun draw(ctx: Context, mgr: AppWidgetManager, id: Int, brief: JSONObject?) {
        super.draw(ctx, mgr, id, brief)
        runCatching { mgr.notifyAppWidgetViewDataChanged(id, R.id.list) }
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, options: Bundle) {
        draw(ctx, mgr, id, Brief.cached(ctx))
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == ACTION_FOCUS) {
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

    private fun folded(ctx: Context, v: RemoteViews, ink: Ink, id: Int, slot: Int, name: Int, m: Move?, show: Boolean) {
        if (!show || m == null) { v.setViewVisibility(slot, View.GONE); return }
        val bg = ink.page(m.type)
        v.setViewVisibility(slot, View.VISIBLE)
        v.setInt(slot, "setBackgroundResource", stripRes(ink, m.type))
        v.setTextViewText(name, m.subject)
        v.setTextColor(name, ink.display(bg).toArgb())
        v.setOnClickPendingIntent(slot, focus(ctx, id, m.id))
    }

    private fun control(v: RemoteViews, view: Int, target: PendingIntent?) {
        v.setInt(view, "setImageAlpha", if (target != null) 220 else 70)
        if (target != null) v.setOnClickPendingIntent(view, target)
    }

    companion object {
        const val ACTION_FOCUS = "inc.axon.radar.action.FOCUS"
        const val EXTRA_TARGET = "inc.axon.radar.TARGET"
        private const val PREFS = "radar"
        private val BLACK_FACE: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)

        /** The front card of each placed widget: kept by move id, and back to the newest when a new deck arrives. */
        fun position(ctx: Context, id: Int, ids: List<String>): Int {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (ids.isEmpty() || p.getString("carousel_${id}_head", null) != ids.first()) return 0
            return ids.indexOf(p.getString("carousel_$id", null)).coerceAtLeast(0)
        }

        fun keep(ctx: Context, id: Int, head: String, target: String) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("carousel_$id", target).putString("carousel_${id}_head", head).apply()
        }

        /** Brings [target] to the front of widget [id]: for the arrows and the folded cards. */
        fun focus(ctx: Context, id: Int, target: String): PendingIntent {
            val intent = Intent(ctx, CarouselWidget::class.java).setAction(ACTION_FOCUS)
                .setData(Uri.parse("radar://focus/$id/" + Uri.encode(target)))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra(EXTRA_TARGET, target)
            return PendingIntent.getBroadcast(ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        /** The same for the stacked cards: each card fills in which move. Mutable, so it can be filled in. */
        fun focusTemplate(ctx: Context, id: Int): PendingIntent {
            val intent = Intent(ctx, CarouselWidget::class.java).setAction(ACTION_FOCUS)
                .setData(Uri.parse("radar://focus/$id"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            return PendingIntent.getBroadcast(ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        }

        /** What a stacked card's tap fills into the template: bring this move to the front. */
        fun fillIn(m: Move): Intent = Intent().putExtra(EXTRA_TARGET, m.id)

        /** The open card, as the app draws it, with its figure and text fitted to [openH] x [cardW] dp. */
        fun openCard(ctx: Context, v: RemoteViews, ink: Ink, deck: List<Move>, i: Int, openH: Int, cardW: Float) {
            val m = deck[i]
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
            v.setImageViewResource(R.id.glyph, glyphRes(m.type, lined))
            v.setInt(R.id.glyph, "setColorFilter", bg.toArgb())
            v.setTextViewText(R.id.label, listOfNotNull(m.typeLabel, m.regulator?.takeIf { Text.money(m.amount) != null }).joinToString(" · "))
            v.setTextColor(R.id.label, bg.toArgb())
            v.setTextViewText(R.id.pos, "${(i + 1).toString().padStart(2, '0')}/${deck.size}")
            v.setTextColor(R.id.pos, ink.onPanel(bg).copy(alpha = 0.5f).toArgb())
            v.setTextViewText(R.id.figure, m.figure)
            v.setTextColor(R.id.figure, bg.toArgb())
            v.setTextViewText(R.id.title, m.title)
            v.setTextColor(R.id.title, ink.onPanel(bg).toArgb())
            v.setTextViewText(R.id.date, Text.dayYear(m.date))
            v.setTextColor(R.id.date, ink.onPanel(bg).copy(alpha = 0.5f).toArgb())

            // Fit the block: the kind at the top, then the figure, the title and the date at the bottom.
            // Give up the third title line and the date, then ease the figure down a little, before the
            // title's second line goes; only then shrink the figure further.
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
                    figSp > 40f -> figSp -= 2f
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
        }

        /** One waiting card in the stack: its name and the top of its block with the kind. */
        fun strip(ctx: Context, m: Move, ink: Ink, d: Dims): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_carousel_strip)
            val bg = ink.page(m.type)
            val lined = ink.lined(m.type)
            v.setInt(R.id.strip, "setBackgroundResource", stripRes(ink, m.type))
            v.setTextViewText(R.id.s_name, m.subject)
            v.setTextColor(R.id.s_name, ink.display(bg).toArgb())
            v.setTextViewTextSize(R.id.s_name, TypedValue.COMPLEX_UNIT_SP, fit(ctx, m.subject, 30f, 18f, (d.w - 40 - 12 - 18).toFloat(), 1))
            v.setInt(R.id.s_panel, "setBackgroundResource", panelRes(ink, bg, lined))
            v.setImageViewResource(R.id.s_glyph, glyphRes(m.type, lined))
            v.setInt(R.id.s_glyph, "setColorFilter", bg.toArgb())
            v.setTextViewText(R.id.s_label, m.typeLabel)
            v.setTextColor(R.id.s_label, bg.toArgb())
            v.setOnClickFillInIntent(R.id.strip, fillIn(m))
            return v
        }

        fun glyphRes(type: String?, hollow: Boolean): Int = when (glyphOf(type)) {
            Shape.Square -> if (hollow) R.drawable.g_sq_o else R.drawable.g_sq
            Shape.Dot -> if (hollow) R.drawable.g_dot_o else R.drawable.g_dot
            Shape.Diamond -> if (hollow) R.drawable.g_dia_o else R.drawable.g_dia
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

/** Hands the launcher the stack below a carousel's open card: every card after it, in order. */
class CarouselService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        CarouselFactory(applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID))
}

class CarouselFactory(private val ctx: Context, private val id: Int) : RemoteViewsService.RemoteViewsFactory {
    private var stack: List<Move> = emptyList()
    private var ink: Ink = Ink.Colour
    private var dims = Dims(360, 420)

    override fun onCreate() = load()
    override fun onDataSetChanged() = load()
    override fun onDestroy() {}

    private fun load() {
        val deck = Store.cachedFast(ctx)?.deck.orEmpty()
        val front = CarouselWidget.position(ctx, id, deck.map { it.id })
        stack = deck.drop(front + 1)
        ink = Ink.of(Store.look(ctx))
        dims = if (id == AppWidgetManager.INVALID_APPWIDGET_ID) CarouselWidget().fallback
        else CarouselWidget().dims(AppWidgetManager.getInstance(ctx), id)
    }

    override fun getCount(): Int = stack.size
    override fun getViewAt(position: Int): RemoteViews = CarouselWidget.strip(ctx, stack[position.coerceIn(0, stack.lastIndex)], ink, dims)
    override fun getLoadingView(): RemoteViews = RemoteViews(ctx.packageName, R.layout.widget_carousel_loading)
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = stack.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()
    override fun hasStableIds(): Boolean = true
}
