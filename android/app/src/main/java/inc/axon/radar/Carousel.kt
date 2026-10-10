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
import inc.axon.radar.ui.Link
import inc.axon.radar.ui.Shape
import inc.axon.radar.ui.glyphOf
import org.json.JSONObject

/**
 * The app's deck as a home-screen widget. The deck is an Android card stack that the launcher swipes one
 * card at a time: swipe up for the next card and down for the previous one, as in the app. Each card of
 * the stack draws the whole deck at that card, the way the app shows it with that card open: the cards
 * before it folded into strips above, the open card with its figure, and the next cards waiting below.
 * A tap on any card reads it in the app; the arrows step one card; the grid opens the app's deck.
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
        val radar = Store.cachedFast(ctx)
        val deck = radar?.deck.orEmpty()
        v.setOnClickPendingIntent(R.id.top, app(ctx, "home", null, code))
        v.setOnClickPendingIntent(R.id.grid, app(ctx, "home", null, code + 2))

        // The stack: CarouselService draws each card for this widget, and a tap on a card reads its move.
        val svc = Intent(ctx, CarouselService::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        svc.data = Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME))
        v.setRemoteAdapter(R.id.stack, svc)
        v.setPendingIntentTemplate(R.id.stack, openTemplate(ctx))
        v.setEmptyView(R.id.stack, R.id.empty)
        v.setOnClickPendingIntent(R.id.empty, app(ctx, "home", null, code + 3))
        v.setOnClickPendingIntent(R.id.up, step(ctx, id, -1))
        v.setOnClickPendingIntent(R.id.down, step(ctx, id, 1))

        if (radar == null || deck.isEmpty()) {
            v.setTextViewText(R.id.eyebrow, "Market Radar")
            v.setViewVisibility(R.id.chip, View.GONE)
            v.setViewVisibility(R.id.controls, View.INVISIBLE)
            v.setTextViewText(R.id.empty_text, if (radar == null) "Loading the latest moves…" else "Nothing on the radar yet.")
            return v
        }
        v.setViewVisibility(R.id.controls, View.VISIBLE)
        v.setTextViewText(R.id.eyebrow, "${Text.dowDay(radar.windowEnd)} · ${Text.plural(radar.count, "move", "moves")} in 7 days")
        v.setViewVisibility(R.id.chip, if (radar.newToday > 0) View.VISIBLE else View.GONE)
        v.setTextViewText(R.id.chip, "+${radar.newToday} new")
        // A new deck starts again from its newest card; otherwise the stack stays where it was swiped to.
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID && newDeck(ctx, id, deck.first().id)) v.setDisplayedChild(R.id.stack, 0)
        return v
    }

    /** Redraws the frame and asks the launcher to redraw every card: new data, look or size. */
    override fun draw(ctx: Context, mgr: AppWidgetManager, id: Int, brief: JSONObject?) {
        super.draw(ctx, mgr, id, brief)
        runCatching { mgr.notifyAppWidgetViewDataChanged(id, R.id.stack) }
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, options: Bundle) {
        draw(ctx, mgr, id, Brief.cached(ctx))
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == ACTION_STEP) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                // The stack moves itself, with the same motion as a swipe; nothing else is redrawn.
                val v = RemoteViews(ctx.packageName, layout)
                if (intent.getIntExtra(EXTRA_DIR, 1) > 0) v.showNext(R.id.stack) else v.showPrevious(R.id.stack)
                runCatching { AppWidgetManager.getInstance(ctx).partiallyUpdateAppWidget(id, v) }
            }
            return
        }
        super.onReceive(ctx, intent)
    }

    override fun onDeleted(ctx: Context, ids: IntArray) {
        val e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        ids.forEach { e.remove("carousel_${it}_head").remove("carousel_$it") }
        e.apply()
    }

    companion object {
        const val ACTION_STEP = "inc.axon.radar.action.STEP"
        const val EXTRA_DIR = "inc.axon.radar.DIR"
        private const val PREFS = "radar"
        private val BLACK_FACE: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)

        /** True, once, when widget [id] first sees a deck that starts with [head]. */
        fun newDeck(ctx: Context, id: Int, head: String): Boolean {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (p.getString("carousel_${id}_head", null) == head) return false
            p.edit().putString("carousel_${id}_head", head).apply()
            return true
        }

        /** One card on (+1) or back (-1) in widget [id]'s stack: for the arrows. */
        fun step(ctx: Context, id: Int, dir: Int): PendingIntent {
            val intent = Intent(ctx, CarouselWidget::class.java).setAction(ACTION_STEP)
                .setData(Uri.parse("radar://step/$id/$dir"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra(EXTRA_DIR, dir)
            return PendingIntent.getBroadcast(ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        /** Opens the app; each card fills in which move. Mutable, so it can be filled in. */
        fun openTemplate(ctx: Context): PendingIntent {
            val intent = Intent(ctx, MainActivity::class.java).setData(Uri.parse("radar://open/card"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return PendingIntent.getActivity(ctx, 750, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        }

        /** What a card's tap fills into the template: read this move, or open a tab of the app. */
        fun fillIn(dest: String, id: String?): Intent =
            Intent().putExtra(Link.EXTRA_DEST, dest).apply { if (id != null) putExtra(Link.EXTRA_ID, id) }

        /**
         * The area a card of the stack draws in, in dp: the stack is 1.25 times the deck area (the widget less
         * the 40dp controls and, at the top, the 42dp line, plus the 4dp and 16dp it reaches past them); its
         * front card is 0.9 of that less 8dp of frame, and the deck is drawn in the top-left 7/8 of the card.
         */
        fun area(d: Dims): Pair<Float, Float> =
            (0.875f * (1.125f * (d.w - 40) - 8f)) to (0.875f * (1.125f * (d.h - 22) - 8f))

        /** Card [i] of the stack: the deck as the app shows it with card [i] open. */
        fun card(ctx: Context, deck: List<Move>, i: Int, ink: Ink, d: Dims): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_carousel_card)
            val m = deck[i]
            val (w, h) = area(d)

            // Folded above: the cards before this one, as many as fit; none at the top of the deck, so the
            // first card opens right under the top line, as in the app.
            val room = when {
                d.h >= 400 -> 2
                d.h >= 340 -> 1
                else -> 0
            }
            val shown = minOf(room, i)
            v.setViewVisibility(R.id.prev, if (shown > 0) View.VISIBLE else View.GONE)
            folded(v, ink, R.id.p1, R.id.p1_name, deck.getOrNull(i - 1), shown >= 1)
            folded(v, ink, R.id.p2, R.id.p2_name, deck.getOrNull(i - 2), shown >= 2)

            // The open card takes 58 parts of what is left and the waiting cards 42, as the app's deck does.
            val openH = ((h - shown * 23) * 0.58f).toInt()
            openCard(ctx, v, ink, deck, i, openH, w - 18)
            v.setOnClickFillInIntent(R.id.open, fillIn("move", m.id))

            // Waiting below: the next cards' names and kinds; after the last card, a pointer to older moves.
            val waiting = listOf(Triple(R.id.n1, R.id.n1_name, Triple(R.id.n1_panel, R.id.n1_glyph, R.id.n1_label)),
                Triple(R.id.n2, R.id.n2_name, Triple(R.id.n2_panel, R.id.n2_glyph, R.id.n2_label)),
                Triple(R.id.n3, R.id.n3_name, Triple(R.id.n3_panel, R.id.n3_glyph, R.id.n3_label)))
            waiting.forEachIndexed { k, (slot, name, parts) ->
                val n = deck.getOrNull(i + 1 + k)
                if (n == null) { v.setViewVisibility(slot, View.GONE); return@forEachIndexed }
                val bg = ink.page(n.type)
                val lined = ink.lined(n.type)
                v.setViewVisibility(slot, View.VISIBLE)
                v.setInt(slot, "setBackgroundResource", stripRes(ink, n.type))
                v.setTextViewText(name, n.subject)
                v.setTextColor(name, ink.display(bg).toArgb())
                v.setTextViewTextSize(name, TypedValue.COMPLEX_UNIT_SP, fit(ctx, n.subject, 30f, 18f, w - 18, 1))
                v.setInt(parts.first, "setBackgroundResource", panelRes(ink, bg, lined))
                v.setImageViewResource(parts.second, glyphRes(n.type, lined))
                v.setInt(parts.second, "setColorFilter", bg.toArgb())
                v.setTextViewText(parts.third, n.typeLabel)
                v.setTextColor(parts.third, bg.toArgb())
                v.setOnClickFillInIntent(slot, fillIn("move", n.id))
            }
            val last = i == deck.lastIndex
            v.setViewVisibility(R.id.nexts, if (last) View.GONE else View.VISIBLE)
            v.setViewVisibility(R.id.end, if (last) View.VISIBLE else View.GONE)
            v.setOnClickFillInIntent(R.id.end, fillIn("moves", null))
            return v
        }

        private fun folded(v: RemoteViews, ink: Ink, slot: Int, name: Int, m: Move?, show: Boolean) {
            if (!show || m == null) { v.setViewVisibility(slot, View.GONE); return }
            val bg = ink.page(m.type)
            v.setViewVisibility(slot, View.VISIBLE)
            v.setInt(slot, "setBackgroundResource", stripRes(ink, m.type))
            v.setTextViewText(name, m.subject)
            v.setTextColor(name, ink.display(bg).toArgb())
            v.setOnClickFillInIntent(slot, fillIn("move", m.id))
        }

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

/** Hands the launcher the carousel's cards: one per move of the deck, newest first. */
class CarouselService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        CarouselFactory(applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID))
}

/** [size] fixes the widget's size, for tests; otherwise it is read from the launcher. */
class CarouselFactory(private val ctx: Context, private val id: Int, private val size: Dims? = null) : RemoteViewsService.RemoteViewsFactory {
    private var deck: List<Move> = emptyList()
    private var ink: Ink = Ink.Colour
    private var dims = Dims(360, 420)

    override fun onCreate() = load()
    override fun onDataSetChanged() = load()
    override fun onDestroy() {}

    private fun load() {
        deck = Store.cachedFast(ctx)?.deck.orEmpty()
        ink = Ink.of(Store.look(ctx))
        dims = size ?: if (id == AppWidgetManager.INVALID_APPWIDGET_ID) CarouselWidget().fallback
        else CarouselWidget().dims(AppWidgetManager.getInstance(ctx), id)
    }

    override fun getCount(): Int = deck.size
    override fun getViewAt(position: Int): RemoteViews = CarouselWidget.card(ctx, deck, position.coerceIn(0, deck.lastIndex), ink, dims)
    override fun getLoadingView(): RemoteViews = RemoteViews(ctx.packageName, R.layout.widget_carousel_loading)
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = deck.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()
    override fun hasStableIds(): Boolean = true
}
