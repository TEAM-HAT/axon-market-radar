package inc.axon.radar

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import inc.axon.radar.data.Radar
import inc.axon.radar.data.Store
import inc.axon.radar.data.Text
import inc.axon.radar.ui.Ink
import inc.axon.radar.ui.Link
import inc.axon.radar.ui.Palette

/** Widget size in dp, portrait. */
data class Dims(val w: Int, val h: Int)

/**
 * Shared behaviour for the widgets: each is drawn from the app's copy of the radar at the size the launcher
 * reports, asks for a fresh copy now and then, and checks for news on a tap of ↻.
 */
abstract class RadarWidget : AppWidgetProvider() {
    abstract val layout: Int
    /** Size used until the launcher reports the real one. */
    abstract val fallback: Dims
    /** Base request code; each widget owns the hundred codes above it. */
    abstract val code: Int
    /** The widget's list or grid, if it has one, for launchers before Android 12 to reload. */
    open val listId: Int? = null

    /** The widget placed as [id], [d] in size, drawn from [r]; with no radar yet, a word on what is coming. */
    abstract fun views(ctx: Context, d: Dims, id: Int, r: Radar?): RemoteViews

    /** The widget from the app's copy of the radar. */
    fun build(ctx: Context, d: Dims, id: Int = AppWidgetManager.INVALID_APPWIDGET_ID): RemoteViews =
        views(ctx, d, id, Store.cachedFast(ctx))

    fun dims(mgr: AppWidgetManager, id: Int): Dims {
        val o = runCatching { mgr.getAppWidgetOptions(id) }.getOrNull()
        val w = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
        val h = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0
        return if (w > 0 && h > 0) Dims(w, h) else fallback
    }

    open fun draw(ctx: Context, mgr: AppWidgetManager, id: Int) {
        val d = dims(mgr, id)
        val sent = runCatching { mgr.updateAppWidget(id, build(ctx, d, id)) }.isSuccess
        if (!sent) runCatching { mgr.updateAppWidget(id, views(ctx, d, id, null)) }
        val list = listId
        if (list != null && Build.VERSION.SDK_INT < 31) runCatching { mgr.notifyAppWidgetViewDataChanged(id, list) }
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { draw(ctx, mgr, it) }
        Refresh.schedule(ctx)
        Refresh.now(ctx)
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, options: Bundle) = draw(ctx, mgr, id)

    override fun onEnabled(ctx: Context) {
        Refresh.schedule(ctx)
        Refresh.now(ctx)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            // ↻ fades while the radar is checked; the redraw that follows brings it back.
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, javaClass))
            if (ids.isNotEmpty()) runCatching {
                mgr.partiallyUpdateAppWidget(ids, RemoteViews(ctx.packageName, layout).apply { setInt(R.id.refresh, "setImageAlpha", 70) })
            }
            Refresh.now(ctx, force = true)
            return
        }
        super.onReceive(ctx, intent)
    }

    /** ↻ at the top right, in [color]: checks for news now. */
    protected fun refreshButton(ctx: Context, v: RemoteViews, color: Int) {
        v.setOnClickPendingIntent(R.id.refresh, refresh(ctx, javaClass, code + 98))
        v.setInt(R.id.refresh, "setColorFilter", color)
        v.setInt(R.id.refresh, "setImageAlpha", 255)
    }

    /** A directory's heading in [color], opening its section of the app. */
    protected fun heading(ctx: Context, v: RemoteViews, word: String, count: Int?, color: Int, dest: String) {
        v.setImageViewBitmap(R.id.heading, Kit.heading(ctx, word, count?.toString(), color))
        v.setInt(R.id.heading, "setColorFilter", color)
        v.setContentDescription(R.id.head, if (count != null) "$word, $count" else word)
        v.setOnClickPendingIntent(R.id.head, app(ctx, dest, null, code))
    }

    /** The width in px of one of three tiles across a widget [d] wide, with 12dp sides and 7dp between. */
    protected fun third(ctx: Context, d: Dims) = Kit.px(ctx, (d.w - 24 - 14) / 3f)

    companion object {
        const val ACTION_REFRESH = "inc.axon.radar.action.REFRESH"
        /** ↻ on black. */
        val GREY = 0xFF8C8C8C.toInt()

        fun all(): List<RadarWidget> =
            listOf(BriefWidget(), MovesWidget(), CompaniesWidget(), LicencesWidget(), TrendsWidget(), DashboardWidget(), CarouselWidget())

        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            all().forEach { w -> mgr.getAppWidgetIds(ComponentName(ctx, w.javaClass)).forEach { w.draw(ctx, mgr, it) } }
        }

        /** Opens the app on a tab ("home", "moves", "companies", "licences", "trends"), a move or a company. */
        fun app(ctx: Context, dest: String, id: String?, requestCode: Int): PendingIntent {
            val intent = Intent(ctx, MainActivity::class.java)
                .setData(Uri.parse("radar://open/$dest/" + Uri.encode(id ?: "")))
                .putExtra(Link.EXTRA_DEST, dest)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (id != null) intent.putExtra(Link.EXTRA_ID, id)
            return PendingIntent.getActivity(ctx, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        fun refresh(ctx: Context, cls: Class<*>, requestCode: Int): PendingIntent {
            val intent = Intent(ctx, cls).setAction(ACTION_REFRESH)
            return PendingIntent.getBroadcast(ctx, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}

/**
 * The daily briefing, on black like the app's deck: the app's top line, the week's count and headline, the
 * week's mix by kind, and the latest ten moves to scroll through, each opening in the app.
 */
class BriefWidget : RadarWidget() {
    override val layout = R.layout.widget_brief
    override val fallback = Dims(360, 300)
    override val code = 100
    override val listId = R.id.list

    override fun views(ctx: Context, d: Dims, id: Int, r: Radar?): RemoteViews {
        val v = RemoteViews(ctx.packageName, layout)
        val ink = Ink.of(Store.look(ctx))
        refreshButton(ctx, v, GREY)
        cover(ctx, v, d, r, ink, if (d.h < 200) 52f else 64f, 6, legend = d.h >= 190, dates = true, code)
        if (r != null && r.moves.isNotEmpty()) {
            v.setTextViewText(R.id.latest_label, "Latest moves")
            Kit.list(ctx, v, R.id.list, "brief", id, code + 50, items(ctx, r, ink))
            // The latest moves scroll under the headline when at least one of them fits, measured as the
            // launcher will lay the widget out.
            val root = Kit.measure(ctx, v, d, exact = true)
            if (root.findViewById<View>(R.id.list).height >= Kit.px(ctx, MIN_LIST)) return v
            v.setViewVisibility(R.id.latest_label, View.GONE)
            v.setViewVisibility(R.id.list, View.GONE)
            // Otherwise the headline takes the room, and the mix shows if it fits under the count.
            var room = Kit.px(ctx, d.h.toFloat()) - Kit.tall(root, R.id.top) - Kit.px(ctx, 12f)
            val mix = Kit.tall(root, R.id.mix)
            if (room - mix >= Kit.tall(root, R.id.count_col)) room -= mix else v.setViewVisibility(R.id.mix, View.GONE)
            val line = root.findViewById<android.widget.TextView>(R.id.headline).lineHeight.coerceAtLeast(1)
            v.setInt(R.id.headline, "setMaxLines", ((room - Kit.px(ctx, 4f)) / line).coerceIn(2, 8))
        } else {
            v.setViewVisibility(R.id.latest_label, View.GONE)
            v.setViewVisibility(R.id.list, View.GONE)
        }
        return v
    }

    companion object {
        /** The least room for the list of latest moves, in dp: about one row. */
        const val MIN_LIST = 60f
        const val LATEST = 10

        /** The latest ten moves as rows on black. */
        fun items(ctx: Context, r: Radar, ink: Ink) = r.moves.take(LATEST).map { Kit.rowItem(ctx, it, ink, Kit.Ground.BLACK) }

        /**
         * The briefing's top on black, shared with the dashboard: the app's top line with ↻, the count huge with
         * its dates, the headline with its key phrases bright, and the week's mix by kind.
         */
        fun cover(ctx: Context, v: RemoteViews, d: Dims, r: Radar?, ink: Ink, countSp: Float, lines: Int, legend: Boolean, dates: Boolean, code: Int) {
            val home = RadarWidget.app(ctx, "home", null, code)
            v.setOnClickPendingIntent(R.id.top, home)
            v.setOnClickPendingIntent(R.id.hero, home)
            val line = if (r != null) "Daily briefing · ${Text.dowDay(r.windowEnd)}" else "Market Radar"
            val fresh = r?.newToday ?: 0
            val short = r?.let { Text.dowDay(it.windowEnd) }
            v.setImageViewBitmap(R.id.topline, CardArt.topLine(ctx, line, fresh, d.w - 30f, short))
            v.setContentDescription(R.id.topline, if (fresh > 0) "$line, $fresh new today" else line)
            val n = r?.count
            v.setImageViewBitmap(R.id.count, Kit.word(ctx, n?.toString() ?: "–", countSp, Kit.WHITE))
            v.setInt(R.id.count, "setColorFilter", Kit.WHITE)
            v.setTextViewText(R.id.count_label, if (n == 1) "Move\nin 7 days" else "Moves\nin 7 days")
            val range = r?.let { Text.range(it.windowStart, it.windowEnd) }.orEmpty()
            if (dates) v.setTextViewText(R.id.dates, range)
            val raw = when {
                r == null -> "Loading the daily brief…"
                r.headline.isBlank() -> "A quiet week. Nothing new passed the source check."
                else -> r.headline
            }
            v.setTextViewText(R.id.headline, Kit.headline(raw, Kit.WHITE))
            v.setTextColor(R.id.headline, Palette.White.copy(alpha = 0.72f).toArgb())
            v.setInt(R.id.headline, "setMaxLines", lines)
            v.setContentDescription(R.id.hero, listOfNotNull(n?.let { Text.plural(it, "move", "moves") + " in 7 days" }, range.ifEmpty { null }, raw.replace("**", "")).joinToString(". "))
            if (r == null || r.count == 0) {
                v.setViewVisibility(R.id.mix, View.GONE)
                return
            }
            v.setViewVisibility(R.id.mix, View.VISIBLE)
            val mix = Kit.mix(ctx, Kit.week(r), ink, Kit.px(ctx, d.w - 36f), Kit.BLACK, Palette.White.copy(alpha = 0.7f).toArgb(), legend)
            v.setImageViewBitmap(R.id.mix, mix)
            v.setContentDescription(R.id.mix, r.mixText)
        }
    }
}

/** The app's Moves screen: the newest three as its tiles, then every move under its month, to scroll. */
class MovesWidget : RadarWidget() {
    override val layout = R.layout.widget_moves
    override val fallback = Dims(360, 400)
    override val code = 200
    override val listId = R.id.list

    override fun views(ctx: Context, d: Dims, id: Int, r: Radar?): RemoteViews {
        val v = RemoteViews(ctx.packageName, layout)
        val ink = Ink.of(Store.look(ctx))
        refreshButton(ctx, v, Kit.BLACK)
        heading(ctx, v, "Moves", r?.moves?.size, Kit.BLACK, "moves")
        if (r == null || r.moves.isEmpty()) {
            v.setViewVisibility(R.id.tiles, View.GONE)
            v.setViewVisibility(R.id.list, View.GONE)
            v.setTextViewText(R.id.list_label, if (r == null) "Loading the latest moves…" else "Nothing on the radar yet.")
            return v
        }
        v.setViewVisibility(R.id.list_label, View.GONE)
        // The tiles leave the list room to scroll on a short widget.
        if (d.h >= 280) {
            val w = third(ctx, d)
            Kit.TILES.forEachIndexed { i, ids ->
                val m = r.moves.getOrNull(i)
                if (m == null) v.setViewVisibility(ids.tile, View.INVISIBLE)
                else {
                    Kit.bindTile(ctx, v, ids, m, ink, w)
                    v.setOnClickPendingIntent(ids.tile, app(ctx, "move", m.id, code + 10 + i))
                }
            }
        } else v.setViewVisibility(R.id.tiles, View.GONE)
        Kit.list(ctx, v, R.id.list, "moves", id, code + 50, items(ctx, r, ink))
        return v
    }

    companion object {
        /** The most moves the list holds; the app has the rest. */
        const val MOST = 50

        fun items(ctx: Context, r: Radar, ink: Ink) = Kit.byMonth(ctx, r.moves.take(MOST), r.moves, ink, Kit.Ground.PAPER)
    }
}

/** The app's Watching boxes: the most active companies of the last 30 days, each in its latest move's colour. */
class CompaniesWidget : RadarWidget() {
    override val layout = R.layout.widget_companies
    override val fallback = Dims(360, 400)
    override val code = 300
    override val listId = R.id.grid

    override fun views(ctx: Context, d: Dims, id: Int, r: Radar?): RemoteViews {
        val v = RemoteViews(ctx.packageName, layout)
        val ink = Ink.of(Store.look(ctx))
        refreshButton(ctx, v, Kit.BLACK)
        heading(ctx, v, "Companies", r?.companies?.size, Kit.BLACK, "companies")
        if (r == null || r.companies.isEmpty()) {
            v.setViewVisibility(R.id.grid, View.GONE)
            v.setTextViewText(R.id.sub, if (r == null) "Loading the companies…" else "No companies yet.")
            return v
        }
        v.setTextViewText(R.id.sub, "Most active · 30 days")
        Kit.list(ctx, v, R.id.grid, "companies", id, code + 50, items(ctx, r, ink))
        return v
    }

    companion object {
        /** The most companies the grid holds; the app has them all. */
        const val MOST = 30

        fun items(ctx: Context, r: Radar, ink: Ink) = Kit.active(r).take(MOST).map { (c, latest) -> Kit.boxItem(ctx, c, latest, ink) }
    }
}

/**
 * A licence's page, in the licence colour (light paper in black and white): the busiest regulators as the
 * app's black and white tiles, then the newest licences and rules under their months.
 */
class LicencesWidget : RadarWidget() {
    override val layout = R.layout.widget_licences
    override val fallback = Dims(360, 400)
    override val code = 400
    override val listId = R.id.list

    override fun views(ctx: Context, d: Dims, id: Int, r: Radar?): RemoteViews {
        val v = RemoteViews(ctx.packageName, layout)
        val ink = Ink.of(Store.look(ctx))
        val page = ink.page("License")
        val fg = ink.display(page).toArgb()
        v.setInt(android.R.id.background, "setBackgroundResource", if (ink.mono) R.drawable.bg_tonepaper else R.drawable.bg_yellow)
        refreshButton(ctx, v, fg)
        val rules = r?.let { rulesOf(it) }.orEmpty()
        heading(ctx, v, "Licences", r?.let { rules.size }, fg, "licences")
        if (r == null || rules.isEmpty()) {
            v.setViewVisibility(R.id.tiles, View.GONE)
            v.setViewVisibility(R.id.list, View.GONE)
            v.setTextViewText(R.id.list_label, if (r == null) "Loading licences and rules…" else "No licences or rules yet.")
            v.setTextColor(R.id.list_label, fg)
            return v
        }
        v.setViewVisibility(R.id.list_label, View.GONE)
        if (d.h >= 280) {
            val w = third(ctx, d)
            val regs = r.regulators.sortedByDescending { it.total }
            Kit.REGS.forEachIndexed { i, ids ->
                val g = regs.getOrNull(i)
                if (g == null) v.setViewVisibility(ids.tile, View.INVISIBLE)
                else {
                    Kit.bindReg(ctx, v, ids, g, i, ink, w, 34f, page)
                    val latest = r.movesForRegulator(g.name).firstOrNull()
                    v.setOnClickPendingIntent(ids.tile, if (latest != null) app(ctx, "move", latest.id, code + 10 + i) else app(ctx, "licences", null, code + 10 + i))
                }
            }
        } else v.setViewVisibility(R.id.tiles, View.GONE)
        Kit.list(ctx, v, R.id.list, "licences", id, code + 50, items(ctx, r, ink))
        return v
    }

    companion object {
        /** The most licences and rules the list holds. */
        const val MOST = 40

        fun rulesOf(r: Radar) = r.moves.filter { it.type == "License" || it.type == "Regulation" }

        fun items(ctx: Context, r: Radar, ink: Ink): List<Pair<Long, RemoteViews>> {
            val rules = rulesOf(r)
            return Kit.byMonth(ctx, rules.take(MOST), rules, ink, Kit.Ground.PAGE, ink.page("License"))
        }
    }
}

/** The app's Trends screen: moves since the radar began, its four totals, and moves per month by kind. */
class TrendsWidget : RadarWidget() {
    override val layout = R.layout.widget_trends
    override val fallback = Dims(360, 300)
    override val code = 500

    override fun views(ctx: Context, d: Dims, id: Int, r: Radar?): RemoteViews {
        val v = RemoteViews(ctx.packageName, layout)
        val ink = Ink.of(Store.look(ctx))
        val tab = app(ctx, "trends", null, code + 1)
        refreshButton(ctx, v, Kit.BLACK)
        heading(ctx, v, "Trends", null, Kit.BLACK, "trends")
        v.setOnClickPendingIntent(R.id.stats, tab)
        v.setOnClickPendingIntent(R.id.chart, tab)
        val inner = Kit.px(ctx, d.w - 28f)
        val t = r?.trends
        val since = if (r == null || t == null) "Loading the trends…" else "${t.total} moves since ${Text.monthYear(t.since)}"
        v.setImageViewBitmap(R.id.since, Kit.fit(ctx, since, Kit.BLACK, 26f, 14f, 1, inner))
        v.setInt(R.id.since, "setColorFilter", Kit.BLACK)
        v.setContentDescription(R.id.since, since)
        val stats = listOf(t?.licences to "Licences", t?.actions to "Rules", t?.commercial to "Commercial", t?.capital to "Capital")
        val column = (inner - 3 * Kit.px(ctx, 1f)) / 4 - Kit.px(ctx, 9f)
        // The labels share one size, the largest at which the longest fits its column.
        val labelSp = stats.minOf { (_, label) -> Kit.fitSp(ctx, label, 11f, MEDIUM, column) }
        STAT_N.forEachIndexed { i, nId ->
            val (n, label) = stats[i]
            v.setImageViewBitmap(nId, Kit.fit(ctx, n?.toString() ?: "–", Kit.BLACK, 34f, 16f, 1, column))
            v.setInt(nId, "setColorFilter", Kit.BLACK)
            v.setTextViewText(STAT_LABEL[i], label)
            v.setTextViewTextSize(STAT_LABEL[i], android.util.TypedValue.COMPLEX_UNIT_SP, labelSp)
            v.setContentDescription(STAT_LABEL[i], "${n ?: 0} $label")
        }
        if (r == null) {
            v.setViewVisibility(R.id.chart, View.GONE)
            v.setViewVisibility(R.id.legend, View.GONE)
            return v
        }
        v.setImageViewBitmap(R.id.legend, Kit.legend(ctx, ink, inner, PAPER))
        // The chart takes the room left: the widget is measured as the launcher will lay it out, then the chart
        // is drawn to fill exactly what remains. The chart comes before its key: on a short widget the key goes
        // first, and without room for the chart both go.
        var chart = Kit.measure(ctx, v, d, exact = true).findViewById<View>(R.id.chart)
        if (chart.height < Kit.px(ctx, CHART_WITH_KEY)) {
            v.setViewVisibility(R.id.legend, View.GONE)
            chart = Kit.measure(ctx, v, d, exact = true).findViewById(R.id.chart)
        }
        if (chart.height >= Kit.px(ctx, CHART_LEAST)) {
            v.setImageViewBitmap(R.id.chart, Kit.chart(ctx, r, ink, chart.width, chart.height, PAPER))
            v.setContentDescription(R.id.chart, "Moves per month, ${r.trends.months.joinToString(", ") { "${Text.monthYear(it.ym)}: ${it.n}" }}")
        } else v.setViewVisibility(R.id.chart, View.GONE)
        return v
    }

    companion object {
        /** The chart's least height in dp, and the least it keeps when its key shows under it. */
        const val CHART_LEAST = 64f
        const val CHART_WITH_KEY = 100f
        val PAPER = Palette.Paper.toArgb()
        /** The widgets' own face for small text, as the layout's sans-serif-medium. */
        private val MEDIUM = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        val STAT_N = intArrayOf(R.id.s1_n, R.id.s2_n, R.id.s3_n, R.id.s4_n)
        val STAT_LABEL = intArrayOf(R.id.s1_label, R.id.s2_label, R.id.s3_label, R.id.s4_label)
    }
}

/**
 * Everything at once: the briefing on black, then on light grey the latest moves, the most active companies as
 * Watching boxes, the busiest regulators as tiles, and moves per month, as many of them as fit.
 */
class DashboardWidget : RadarWidget() {
    override val layout = R.layout.widget_dashboard
    override val fallback = Dims(360, 620)
    override val code = 600

    override fun views(ctx: Context, d: Dims, id: Int, r: Radar?): RemoteViews {
        val v = RemoteViews(ctx.packageName, layout)
        val ink = Ink.of(Store.look(ctx))
        refreshButton(ctx, v, GREY)
        BriefWidget.cover(ctx, v, d, r, ink, 56f, 4, legend = true, dates = false, code)
        val parts = intArrayOf(R.id.latest_label, R.id.rows, R.id.co_label, R.id.boxes, R.id.reg_label, R.id.tiles, R.id.chart)
        if (r == null || r.moves.isEmpty()) {
            parts.forEach { v.setViewVisibility(it, View.GONE) }
            return v
        }
        v.setTextViewText(R.id.latest_label, "Latest moves")
        v.setOnClickPendingIntent(R.id.latest_label, app(ctx, "moves", null, code + 2))
        Kit.ROWS.forEachIndexed { i, ids ->
            val m = r.moves.getOrNull(i)
            if (m == null) v.setViewVisibility(ids.item, View.GONE)
            else {
                Kit.bindRow(v, ids, m, ink, Kit.Ground.PAPER)
                v.setOnClickPendingIntent(ids.row, app(ctx, "move", m.id, code + 10 + i))
            }
        }
        v.setTextViewText(R.id.co_label, "Most active companies")
        v.setOnClickPendingIntent(R.id.co_label, app(ctx, "companies", null, code + 3))
        val cos = Kit.active(r).take(3)
        Kit.BOXES.forEachIndexed { i, ids ->
            val c = cos.getOrNull(i)
            if (c == null) v.setViewVisibility(ids.box, View.INVISIBLE)
            else {
                Kit.bindBox(ctx, v, ids, c.first, c.second, ink, 30f, small = true)
                v.setOnClickPendingIntent(ids.box, app(ctx, "company", c.first.id, code + 20 + i))
            }
        }
        v.setTextViewText(R.id.reg_label, "Busiest regulators")
        v.setOnClickPendingIntent(R.id.reg_label, app(ctx, "licences", null, code + 4))
        val regs = r.regulators.sortedByDescending { it.total }
        val w = third(ctx, d)
        Kit.REGS.forEachIndexed { i, ids ->
            val g = regs.getOrNull(i)
            if (g == null) v.setViewVisibility(ids.tile, View.INVISIBLE)
            else {
                Kit.bindReg(ctx, v, ids, g, i, ink, w, 26f)
                val latest = r.movesForRegulator(g.name).firstOrNull()
                v.setOnClickPendingIntent(ids.tile, if (latest != null) app(ctx, "move", latest.id, code + 30 + i) else app(ctx, "licences", null, code + 30 + i))
            }
        }
        v.setOnClickPendingIntent(R.id.chart, app(ctx, "trends", null, code + 5))

        // What fits, measured as the launcher will lay it out: the briefing and two moves first, then the
        // companies, the regulators, the chart, and a third move. Each keeps 12dp clear under it, as the chart
        // does with its own margin.
        val natural = Kit.measure(ctx, v, d, exact = false)
        val rows = Kit.ROWS.map { Kit.tall(natural, it.item) }
        val co = Kit.tall(natural, R.id.co_label) + Kit.tall(natural, R.id.boxes)
        val reg = Kit.tall(natural, R.id.reg_label) + Kit.tall(natural, R.id.tiles)
        val pad = Kit.px(ctx, 12f)
        val chartMin = Kit.px(ctx, 96f) + Kit.tall(natural, R.id.chart)
        var room = Kit.px(ctx, d.h.toFloat()) - Kit.tall(natural, R.id.cover)
        val first = Kit.tall(natural, R.id.latest_label) + Kit.tall(natural, R.id.rows) - rows.sum() + rows[0]
        var shown = 0
        if (room - first >= pad) { shown = 1; room -= first }
        if (shown == 1 && room - rows[1] >= pad) { shown = 2; room -= rows[1] }
        val showCo = shown > 0 && room - co >= pad
        if (showCo) room -= co
        val showReg = showCo && room - reg >= pad
        if (showReg) room -= reg
        val showChart = shown > 0 && room >= chartMin
        if (shown == 2 && (if (showChart) room - chartMin >= rows[2] else room - rows[2] >= pad)) { shown = 3; room -= rows[2] }
        Kit.ROWS.forEachIndexed { i, ids -> if (i >= shown) v.setViewVisibility(ids.item, View.GONE) }
        if (shown == 0) {
            v.setViewVisibility(R.id.latest_label, View.GONE)
            v.setViewVisibility(R.id.rows, View.GONE)
        }
        v.setViewVisibility(R.id.co_label, if (showCo) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.boxes, if (showCo) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.reg_label, if (showReg) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.tiles, if (showReg) View.VISIBLE else View.GONE)
        if (!showChart) {
            v.setViewVisibility(R.id.chart, View.GONE)
            return v
        }
        val chart = Kit.measure(ctx, v, d, exact = true).findViewById<View>(R.id.chart)
        v.setImageViewBitmap(R.id.chart, Kit.chart(ctx, r, ink, chart.width, chart.height.coerceAtLeast(1), TrendsWidget.PAPER))
        v.setContentDescription(R.id.chart, "Moves per month")
        return v
    }
}
