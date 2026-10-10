package inc.axon.radar

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
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
 * The app's deck as a home-screen widget. The cards sit in a list the launcher scrolls under your
 * finger, each drawn as the app draws its open card and sized so the next one peeks in below.
 * The arrows step one card, a tap on a card opens that move, and the grid opens the app's deck.
 * Colours follow the app's look.
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
        v.setOnClickPendingIntent(R.id.top, app(ctx, "home", null, code))
        if (radar != null) {
            v.setTextViewText(R.id.eyebrow, "${Text.dowDay(radar.windowEnd)} · ${Text.plural(radar.count, "move", "moves")} in 7 days")
            v.setViewVisibility(R.id.chip, if (radar.newToday > 0) View.VISIBLE else View.GONE)
            v.setTextViewText(R.id.chip, "+${radar.newToday} new")
        } else {
            v.setTextViewText(R.id.eyebrow, "Market Radar")
            v.setViewVisibility(R.id.chip, View.GONE)
        }

        // The cards: a list filled by CarouselService. One service intent per placed widget, so each
        // gets its own cards at its own size.
        val svc = Intent(ctx, CarouselService::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        svc.data = Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME))
        v.setRemoteAdapter(R.id.list, svc)
        v.setEmptyView(R.id.list, R.id.empty)
        v.setTextViewText(R.id.empty, if (radar == null) "Loading the latest moves…" else "Nothing on the radar yet.")
        v.setPendingIntentTemplate(R.id.list, template(ctx))

        v.setOnClickPendingIntent(R.id.up, scroll(ctx, id, -1))
        v.setOnClickPendingIntent(R.id.down, scroll(ctx, id, 1))
        v.setOnClickPendingIntent(R.id.grid, app(ctx, "home", null, code + 2))
        return v
    }

    /** Redraws the frame and asks the launcher to reload the cards: new data, a new look or a new size. */
    override fun draw(ctx: Context, mgr: AppWidgetManager, id: Int, brief: JSONObject?) {
        super.draw(ctx, mgr, id, brief)
        runCatching { mgr.notifyAppWidgetViewDataChanged(id, R.id.list) }
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, options: Bundle) {
        draw(ctx, mgr, id, Brief.cached(ctx))
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == ACTION_SCROLL) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val delta = intent.getIntExtra(EXTRA_DELTA, 1)
            // Scroll the list by one card from wherever the finger left it. A tap can arrive after the
            // widget was removed; there is nothing to scroll then.
            runCatching {
                val rv = RemoteViews(ctx.packageName, layout).apply { setRelativeScrollPosition(R.id.list, delta) }
                AppWidgetManager.getInstance(ctx).partiallyUpdateAppWidget(id, rv)
            }
            return
        }
        super.onReceive(ctx, intent)
    }

    companion object {
        const val ACTION_SCROLL = "inc.axon.radar.action.SCROLL"
        const val EXTRA_DELTA = "inc.axon.radar.DELTA"
        private val BLACK_FACE: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)

        fun scroll(ctx: Context, id: Int, delta: Int): PendingIntent {
            val intent = Intent(ctx, CarouselWidget::class.java).setAction(ACTION_SCROLL)
                .setData(Uri.parse("radar://scroll/$id/$delta"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra(EXTRA_DELTA, delta)
            return PendingIntent.getBroadcast(ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        /** Opens the app; each card fills in which move. Mutable, so the card's details can be filled in. */
        fun template(ctx: Context): PendingIntent {
            val intent = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return PendingIntent.getActivity(ctx, 777, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        }

        /** What a card's tap fills into the template: open this move in the app. */
        fun fillIn(m: Move): Intent = Intent()
            .setData(Uri.parse("radar://open/move/" + Uri.encode(m.id)))
            .putExtra(Link.EXTRA_DEST, "move").putExtra(Link.EXTRA_ID, m.id)

        /** A card's height in dp for a widget [d]: tall enough to read, short enough for the next to peek in. */
        fun cardHeight(d: Dims): Int {
            val list = d.h - 42 - 12
            return (list - 66).coerceAtLeast(196).coerceAtMost(list - 8)
        }

        /** One card of the list, drawn as the app draws its open card. */
        fun card(ctx: Context, deck: List<Move>, i: Int, ink: Ink, d: Dims): RemoteViews {
            val m = deck[i]
            val v = RemoteViews(ctx.packageName, R.layout.widget_carousel_card)
            val h = cardHeight(d)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) v.setViewLayoutHeight(R.id.card, h.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
            val openH = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) h else 300
            val cardW = (d.w - 40 - 12 - 18).toFloat()

            val bg = ink.page(m.type)
            val lined = ink.lined(m.type)
            v.setInt(R.id.card, "setBackgroundResource", cardRes(ink, m.type))
            v.setTextViewText(R.id.name, m.subject)
            v.setTextColor(R.id.name, ink.display(bg).toArgb())
            // The name takes two lines only on a tall card; the block below gets what is left.
            val nameLines = if (openH >= 250) 2 else 1
            val nameSp = fit(ctx, m.subject, 38f, 20f, cardW, nameLines)
            v.setTextViewTextSize(R.id.name, TypedValue.COMPLEX_UNIT_SP, nameSp)
            v.setInt(R.id.name, "setMaxLines", nameLines)
            v.setInt(R.id.panel, "setBackgroundResource", panelRes(ink, bg, lined))
            v.setImageViewResource(R.id.glyph, when (glyphOf(m.type)) {
                Shape.Square -> if (lined) R.drawable.g_sq_o else R.drawable.g_sq
                Shape.Dot -> if (lined) R.drawable.g_dot_o else R.drawable.g_dot
                Shape.Diamond -> if (lined) R.drawable.g_dia_o else R.drawable.g_dia
            })
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
            v.setOnClickFillInIntent(R.id.card, fillIn(m))
            return v
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

/** Hands the launcher the carousel's cards, one per move in the deck. */
class CarouselService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        CarouselFactory(applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID))
}

class CarouselFactory(private val ctx: Context, private val id: Int) : RemoteViewsService.RemoteViewsFactory {
    private var deck: List<Move> = emptyList()
    private var ink: Ink = Ink.Colour
    private var dims = Dims(360, 420)

    override fun onCreate() = load()
    override fun onDataSetChanged() = load()
    override fun onDestroy() {}

    private fun load() {
        deck = Store.cachedFast(ctx)?.deck.orEmpty()
        ink = Ink.of(Store.look(ctx))
        dims = if (id == AppWidgetManager.INVALID_APPWIDGET_ID) CarouselWidget().fallback
        else CarouselWidget().dims(AppWidgetManager.getInstance(ctx), id)
    }

    override fun getCount(): Int = deck.size
    override fun getViewAt(position: Int): RemoteViews = CarouselWidget.card(ctx, deck, position.coerceIn(0, deck.lastIndex), ink, dims)
    override fun getLoadingView(): RemoteViews = RemoteViews(ctx.packageName, R.layout.widget_carousel_loading)
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = deck.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()
    override fun hasStableIds(): Boolean = true
}
