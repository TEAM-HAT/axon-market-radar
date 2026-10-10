package inc.axon.radar

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.annotation.RequiresApi
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
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The app's cards as a home-screen list. The list scrolls and flings like any list; each card is the app's
 * card made shorter: the name huge on the move's colour, then the black block with the kind, the figure, the
 * title and the date. Names and figures are set in the app's typeface (CardArt). Under the app's top line,
 * with the app's up, grid and down at the bottom left: up and down scroll one card, the grid opens the app's
 * lists, and a tap on a card reads it in the app.
 *
 * From Android 12 the cards travel with the widget itself, so the list never waits for a card to load as
 * it scrolls; before that, CarouselService hands them to the launcher.
 */
class CarouselWidget : RadarWidget() {
    override val layout = R.layout.widget_carousel
    override val monoLayout = R.layout.widget_carousel // every colour on it is set from code
    override val fallback = Dims(360, 420)
    override val code = 700

    override fun build(ctx: Context, brief: JSONObject?, d: Dims): RemoteViews =
        buildFor(ctx, brief, d, AppWidgetManager.INVALID_APPWIDGET_ID)

    override fun buildFor(ctx: Context, brief: JSONObject?, d: Dims, id: Int): RemoteViews = frame(ctx, d, id, MAX_CARDS)

    /** The widget with at most [most] cards in its list. */
    fun frame(ctx: Context, d: Dims, id: Int, most: Int): RemoteViews {
        val v = RemoteViews(ctx.packageName, layout)
        val radar = Store.cachedFast(ctx)
        val deck = radar?.deck.orEmpty()
        v.setOnClickPendingIntent(R.id.top, app(ctx, "home", null, code))
        v.setOnClickPendingIntent(R.id.grid, app(ctx, "moves", null, code + 2))
        v.setOnClickPendingIntent(R.id.up, step(ctx, id, -1))
        v.setOnClickPendingIntent(R.id.down, step(ctx, id, 1))
        v.setPendingIntentTemplate(R.id.list, openTemplate(ctx))
        v.setEmptyView(R.id.list, R.id.empty)
        v.setOnClickPendingIntent(R.id.empty, app(ctx, "home", null, code + 3))

        val r = radar?.takeIf { deck.isNotEmpty() }
        val line = if (r != null) "${Text.dowDay(r.windowEnd)} · ${Text.plural(r.count, "move", "moves")} in 7 days" else "Market Radar"
        val fresh = r?.newToday ?: 0
        v.setImageViewBitmap(R.id.topline, CardArt.topLine(ctx, line, fresh, d.w.toFloat()))
        v.setContentDescription(R.id.topline, if (fresh > 0) "$line, $fresh new" else line)

        if (Build.VERSION.SDK_INT >= 31) {
            v.setRemoteAdapter(R.id.list, items(ctx, deck, Ink.of(Store.look(ctx)), d, most))
        } else {
            val svc = Intent(ctx, CarouselService::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            svc.data = Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME))
            @Suppress("DEPRECATION")
            v.setRemoteAdapter(R.id.list, svc)
        }
        if (r == null) {
            v.setViewVisibility(R.id.controls, View.INVISIBLE)
            v.setTextViewText(R.id.empty_text, if (radar == null) "Loading the latest moves…" else "Nothing on the radar yet.")
            return v
        }
        v.setViewVisibility(R.id.controls, View.VISIBLE)
        // A new deck starts again from its newest card; otherwise the list stays where it was scrolled to.
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID && newDeck(ctx, id, deck.first().id)) v.setScrollPosition(R.id.list, 0)
        return v
    }

    /**
     * Sends the widget with its cards. Android refuses a widget whose pictures take more than about one and a
     * half screens of memory; the cards stay well under that, and if a launcher is stricter, the list is cut
     * down until it is taken.
     */
    override fun draw(ctx: Context, mgr: AppWidgetManager, id: Int, brief: JSONObject?) {
        val d = dims(mgr, id)
        var most = MAX_CARDS
        while (true) {
            val sent = runCatching { mgr.updateAppWidget(id, frame(ctx, d, id, most)) }.isSuccess
            if (sent || most <= 3) break
            most /= 2
        }
        if (Build.VERSION.SDK_INT < 31) runCatching { mgr.notifyAppWidgetViewDataChanged(id, R.id.list) }
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, options: Bundle) {
        draw(ctx, mgr, id, Brief.cached(ctx))
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == ACTION_STEP) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                // The list scrolls itself, one card up or down; nothing else is redrawn.
                val v = RemoteViews(ctx.packageName, layout)
                v.setRelativeScrollPosition(R.id.list, if (intent.getIntExtra(EXTRA_DIR, 1) > 0) 1 else -1)
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
        /** The most cards the list holds; the app has the rest. */
        const val MAX_CARDS = 30
        /** Room at each side of a card: the app's controls sit in the left one. */
        private const val SIDE = 48f
        /** The figure's largest size on the list's shorter cards (64sp on the app's). */
        const val FIGURE_SP = 52f

        /** True, once, when widget [id] first sees a deck that starts with [head]. */
        fun newDeck(ctx: Context, id: Int, head: String): Boolean {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (p.getString("carousel_${id}_head", null) == head) return false
            p.edit().putString("carousel_${id}_head", head).apply()
            return true
        }

        /** One card down (+1) or up (-1) in widget [id]'s list: for the arrows. */
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

        /** What a card's tap fills into the template: read this move. */
        fun fillIn(dest: String, id: String?): Intent =
            Intent().putExtra(Link.EXTRA_DEST, dest).apply { if (id != null) putExtra(Link.EXTRA_ID, id) }

        /**
         * A card's width in dp in widget [d]: the app's card width on this phone (0.68 of the screen), or less to
         * leave 48dp at each side. Before Android 12 the sides are fixed at 48dp.
         */
        fun cardWidth(ctx: Context, d: Dims): Float {
            val room = d.w - 2 * SIDE
            if (Build.VERSION.SDK_INT < 31) return room
            val dm = ctx.resources.displayMetrics
            val phone = min(dm.widthPixels, dm.heightPixels) / dm.density
            return min(room, phone * 0.68f)
        }

        /** The list's cards, as many as fit in half of Android's picture allowance for a widget. */
        @RequiresApi(31)
        fun items(ctx: Context, deck: List<Move>, ink: Ink, d: Dims, most: Int): RemoteViews.RemoteCollectionItems {
            val dm = ctx.resources.displayMetrics
            val allowance = 3L * dm.widthPixels * dm.heightPixels // half of 1.5 screens at 4 bytes a pixel
            val b = RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(1)
            var bytes = 0L
            for (m in deck.take(most)) {
                val (v, cost) = card(ctx, m, ink, d)
                if (bytes + cost > allowance) break
                bytes += cost
                b.addItem(m.id.hashCode().toLong(), v)
            }
            return b.build()
        }

        /** One card of the list, and what its pictures cost in bytes. */
        fun card(ctx: Context, m: Move, ink: Ink, d: Dims): Pair<RemoteViews, Long> {
            val v = RemoteViews(ctx.packageName, R.layout.widget_carousel_card)
            val bg = ink.page(m.type)
            val lined = ink.lined(m.type)
            val wDp = cardWidth(ctx, d)
            val wPx = (wDp * ctx.resources.displayMetrics.density).roundToInt()
            if (Build.VERSION.SDK_INT >= 31) {
                val side = (d.w - wDp) / 2f
                v.setViewLayoutMargin(R.id.card, RemoteViews.MARGIN_START, side, TypedValue.COMPLEX_UNIT_DIP)
                v.setViewLayoutMargin(R.id.card, RemoteViews.MARGIN_END, side, TypedValue.COMPLEX_UNIT_DIP)
            }
            v.setInt(R.id.card, "setBackgroundResource", cardRes(ink, m.type))
            v.setInt(R.id.panel, "setBackgroundResource", panelRes(ink, bg, lined))
            val name = CardArt.name(ctx, m, wPx, ink.display(bg).toArgb())
            v.setImageViewBitmap(R.id.name, name)
            v.setInt(R.id.name, "setColorFilter", ink.display(bg).toArgb())
            v.setImageViewResource(R.id.glyph, glyphRes(m.type, lined))
            v.setInt(R.id.glyph, "setColorFilter", bg.toArgb())
            v.setTextViewText(R.id.label, listOfNotNull(m.typeLabel, m.regulator?.takeIf { Text.money(m.amount) != null }).joinToString(" · "))
            v.setTextColor(R.id.label, bg.toArgb())
            val figure = CardArt.figure(ctx, m, wPx, bg.toArgb(), FIGURE_SP)
            v.setImageViewBitmap(R.id.figure, figure)
            v.setInt(R.id.figure, "setColorFilter", bg.toArgb())
            v.setTextViewText(R.id.title, m.title)
            v.setTextColor(R.id.title, ink.onPanel(bg).toArgb())
            v.setTextViewText(R.id.date, Text.dayYear(m.date))
            v.setTextColor(R.id.date, ink.onPanel(bg).copy(alpha = 0.5f).toArgb())
            v.setOnClickFillInIntent(R.id.card, fillIn("move", m.id))
            v.setContentDescription(R.id.card, CardArt.describe(m))
            return v to (name.allocationByteCount + figure.allocationByteCount).toLong()
        }

        fun glyphRes(type: String?, hollow: Boolean): Int = when (glyphOf(type)) {
            Shape.Square -> if (hollow) R.drawable.g_sq_o else R.drawable.g_sq
            Shape.Dot -> if (hollow) R.drawable.g_dot_o else R.drawable.g_dot
            Shape.Diamond -> if (hollow) R.drawable.g_dia_o else R.drawable.g_dia
        }

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

        /** The black block, paper on a black card in black and white, with fine lines for the second kind of a family. */
        fun panelRes(ink: Ink, bg: Color, lined: Boolean): Int =
            if (ink.dark(bg)) (if (lined) R.drawable.panel_paper_lined else R.drawable.panel_paper)
            else (if (lined) R.drawable.panel_black_lined else R.drawable.panel_black)
    }
}

/** Hands the launcher the list's cards before Android 12: one per move of the deck, newest first. */
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
        deck = Store.cachedFast(ctx)?.deck.orEmpty().take(CarouselWidget.MAX_CARDS)
        ink = Ink.of(Store.look(ctx))
        dims = size ?: if (id == AppWidgetManager.INVALID_APPWIDGET_ID) CarouselWidget().fallback
        else CarouselWidget().dims(AppWidgetManager.getInstance(ctx), id)
    }

    override fun getCount(): Int = deck.size
    override fun getViewAt(position: Int): RemoteViews = CarouselWidget.card(ctx, deck[position.coerceIn(0, deck.lastIndex)], ink, dims).first
    override fun getLoadingView(): RemoteViews = RemoteViews(ctx.packageName, R.layout.widget_carousel_loading)
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = deck.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()
    override fun hasStableIds(): Boolean = true
}
