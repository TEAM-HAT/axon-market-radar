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
import inc.axon.radar.data.Move
import inc.axon.radar.data.Store
import inc.axon.radar.data.Text
import inc.axon.radar.ui.Ink
import inc.axon.radar.ui.Link
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * The app's deck as a home-screen widget. The deck is an Android card stack that the launcher swipes one
 * card at a time: swipe up for the next card and down for the previous one, as in the app. Each card of
 * the stack is a picture of the app's deck at that card (DeckArt), drawn with the app's own type and card
 * shapes: the cards before it folded into strips above, the open card with its figure, and the next cards
 * waiting below. The top line and the controls round it are the app's too.
 *
 * A tap on the open card reads it in the app; a tap on another card opens the app's deck at that card, as a
 * tap does in the app. The arrows step one card, and the grid opens the app's lists.
 */
class CarouselWidget : RadarWidget() {
    override val layout = R.layout.widget_carousel
    override val monoLayout = R.layout.widget_carousel // every colour on it is drawn from code
    override val fallback = Dims(360, 420)
    override val code = 700

    override fun build(ctx: Context, brief: JSONObject?, d: Dims): RemoteViews =
        buildFor(ctx, brief, d, AppWidgetManager.INVALID_APPWIDGET_ID)

    override fun buildFor(ctx: Context, brief: JSONObject?, d: Dims, id: Int): RemoteViews {
        val v = RemoteViews(ctx.packageName, layout)
        val radar = Store.cachedFast(ctx)
        val deck = radar?.deck.orEmpty()
        val plan = DeckArt.forWidget(ctx, d)
        v.setOnClickPendingIntent(R.id.top, app(ctx, "home", null, code))
        v.setOnClickPendingIntent(R.id.grid, app(ctx, "moves", null, code + 2))

        // The stack: CarouselService draws each card for this widget, and a tap on a card opens it in the app.
        val svc = Intent(ctx, CarouselService::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        svc.data = Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME))
        v.setRemoteAdapter(R.id.stack, svc)
        v.setPendingIntentTemplate(R.id.stack, openTemplate(ctx))
        v.setEmptyView(R.id.stack, R.id.empty)
        v.setOnClickPendingIntent(R.id.empty, app(ctx, "home", null, code + 3))
        v.setOnClickPendingIntent(R.id.back, step(ctx, id, -1))
        v.setOnClickPendingIntent(R.id.up, step(ctx, id, -1))
        v.setOnClickPendingIntent(R.id.down, step(ctx, id, 1))
        if (Build.VERSION.SDK_INT >= 31) frame(v, plan)
        // The app's left arrow sits halfway down; on a short widget it would meet the arrows below, so it goes.
        val c = plan.chrome
        v.setViewVisibility(R.id.back, if (d.h / 2f + 17f * c > d.h - 120f * c - 4f) View.GONE else View.VISIBLE)

        val r = radar?.takeIf { deck.isNotEmpty() }
        val line = if (r != null) "${Text.dowDay(r.windowEnd)} · ${Text.plural(r.count, "move", "moves")} in 7 days" else "Market Radar"
        val fresh = r?.newToday ?: 0
        v.setImageViewBitmap(R.id.topline, DeckArt.topLine(ctx, line, fresh, d.w.toFloat(), plan))
        v.setContentDescription(R.id.topline, if (fresh > 0) "$line, $fresh new" else line)
        if (r == null) {
            v.setViewVisibility(R.id.controls, View.INVISIBLE)
            v.setTextViewText(R.id.empty_text, if (radar == null) "Loading the latest moves…" else "Nothing on the radar yet.")
            return v
        }
        v.setViewVisibility(R.id.controls, View.VISIBLE)
        // A new deck starts again from its newest card; otherwise the stack stays where it was swiped to.
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID && newDeck(ctx, id, deck.first().id)) v.setDisplayedChild(R.id.stack, 0)
        return v
    }

    /** From Android 12 the top line and the controls take the deck's scale, so the widget is the app in small. */
    @RequiresApi(31)
    private fun frame(v: RemoteViews, p: DeckArt.Plan) {
        val px = TypedValue.COMPLEX_UNIT_PX
        fun c(dp: Float) = (dp * p.chrome * p.den).roundToInt()
        val top = p.px(p.top).toFloat()
        v.setViewLayoutHeight(R.id.top, top, px)
        v.setViewLayoutMargin(R.id.deckrow, RemoteViews.MARGIN_TOP, top, px)
        listOf(R.id.up, R.id.grid, R.id.down).forEach { b ->
            v.setViewLayoutWidth(b, c(34f).toFloat(), px)
            v.setViewLayoutHeight(b, c(34f).toFloat(), px)
            v.setViewPadding(b, c(8f), c(8f), c(8f), c(8f))
        }
        v.setViewLayoutMargin(R.id.cluster, RemoteViews.MARGIN_START, c(10f).toFloat(), px)
        v.setViewLayoutMargin(R.id.cluster, RemoteViews.MARGIN_BOTTOM, c(18f).toFloat(), px)
        v.setViewLayoutWidth(R.id.back, c(34f).toFloat(), px)
        v.setViewLayoutHeight(R.id.back, c(34f).toFloat(), px)
        v.setViewPadding(R.id.back, c(7f), c(7f), c(7f), c(7f))
        v.setViewLayoutMargin(R.id.back, RemoteViews.MARGIN_START, c(10f).toFloat(), px)
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
        /** The taps over a card of the stack, one per card that shows, top to bottom. */
        val ZONES = intArrayOf(R.id.z0, R.id.z1, R.id.z2, R.id.z3, R.id.z4, R.id.z5, R.id.z6, R.id.z7, R.id.z8, R.id.z9)

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

        /** Opens the app; each card fills in where. Mutable, so it can be filled in. */
        fun openTemplate(ctx: Context): PendingIntent {
            val intent = Intent(ctx, MainActivity::class.java).setData(Uri.parse("radar://open/card"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return PendingIntent.getActivity(ctx, 750, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        }

        /** What a card's tap fills into the template: read this move, or open the app's deck at it. */
        fun fillIn(dest: String, id: String?): Intent =
            Intent().putExtra(Link.EXTRA_DEST, dest).apply { if (id != null) putExtra(Link.EXTRA_ID, id) }

        /**
         * The area a card of the stack draws in, in dp, under a top line [top] dp tall: the stack is 1.25 times
         * the deck area (the widget less the 40dp at the left and the top line, plus the 4dp and 16dp it reaches
         * past them); its front card is 0.9 of that less 8dp of frame, and the deck is drawn in the top-left 7/8
         * of the card. The area starts 40dp across and [top] down.
         */
        fun area(d: Dims, top: Float = 42f): Pair<Float, Float> =
            (0.875f * (1.125f * (d.w - 40) - 8f)) to (0.875f * (1.125f * (d.h - top + 20f) - 8f))

        /** Card [i] of the stack: the app's deck with card [i] open, and a tap for each card that shows. */
        fun card(ctx: Context, deck: List<Move>, i: Int, ink: Ink, d: Dims): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_carousel_card)
            val p = DeckArt.forWidget(ctx, d)
            val m = deck[i]
            v.setImageViewBitmap(R.id.art, DeckArt.deck(ctx, deck, i, ink, p))
            v.setContentDescription(R.id.art, DeckArt.describe(m))
            if (Build.VERSION.SDK_INT >= 31) taps(v, deck, i, p)
            else {
                // Before Android 12 a card cannot be cut into taps: the whole card reads the open one.
                v.setOnClickFillInIntent(R.id.z0, fillIn("move", m.id))
                v.setContentDescription(R.id.z0, "Read ${m.subject}")
            }
            return v
        }

        @RequiresApi(31)
        private fun taps(v: RemoteViews, deck: List<Move>, i: Int, p: DeckArt.Plan) {
            val px = TypedValue.COMPLEX_UNIT_PX
            val zones = DeckArt.zones(deck.size, i, p)
            v.setViewLayoutMargin(R.id.zones, RemoteViews.MARGIN_START, p.cardX.toFloat(), px)
            v.setViewLayoutWidth(R.id.zones, (p.cardWpx * p.scale).roundToInt().toFloat(), px)
            v.setViewLayoutHeight(R.id.zgap, (zones.firstOrNull()?.top ?: 0).toFloat(), px)
            ZONES.forEachIndexed { z, id ->
                val zone = zones.getOrNull(z)
                if (zone == null) {
                    v.setViewVisibility(id, View.GONE)
                    return@forEachIndexed
                }
                val m = deck[zone.index]
                val open = zone.index == i
                v.setViewVisibility(id, View.VISIBLE)
                v.setViewLayoutHeight(id, (zone.bottom - zone.top).toFloat(), px)
                v.setOnClickFillInIntent(id, fillIn(if (open) "move" else "deck", m.id))
                v.setContentDescription(id, if (open) "Read ${m.subject}" else "Show ${m.subject}")
            }
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
