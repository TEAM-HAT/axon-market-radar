package inc.axon.radar

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import inc.axon.radar.data.Store
import inc.axon.radar.ui.Link
import inc.axon.radar.ui.Look
import org.json.JSONObject

/** Widget size in dp, portrait. */
data class Dims(val w: Int, val h: Int)

/**
 * Shared behaviour for the six widgets: draw from the cached brief at the size the launcher reports,
 * ask for a fresh copy, and refresh on a tap of the "Updated" label.
 */
abstract class RadarWidget : AppWidgetProvider() {
    abstract val layout: Int
    /** The same layout in black and white, with the same ids. */
    abstract val monoLayout: Int
    /** Size used until the launcher reports the real one. */
    abstract val fallback: Dims
    /** Base request code; each widget owns the hundred codes above it. */
    abstract val code: Int
    abstract fun build(ctx: Context, brief: JSONObject?, d: Dims): RemoteViews

    /** Builds for one placed widget; widgets that keep state per placement (the carousel) override this. */
    open fun buildFor(ctx: Context, brief: JSONObject?, d: Dims, id: Int): RemoteViews = build(ctx, brief, d)

    /** The layout for the look chosen in the app. */
    fun layoutFor(ctx: Context): Int = if (mono(ctx)) monoLayout else layout

    fun dims(mgr: AppWidgetManager, id: Int): Dims {
        val o = runCatching { mgr.getAppWidgetOptions(id) }.getOrNull()
        val w = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
        val h = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0
        return if (w > 0 && h > 0) Dims(w, h) else fallback
    }

    fun draw(ctx: Context, mgr: AppWidgetManager, id: Int, brief: JSONObject?) {
        val d = dims(mgr, id)
        val views = runCatching { buildFor(ctx, brief, d, id) }.getOrElse { build(ctx, null, d) }
        mgr.updateAppWidget(id, views)
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val brief = Brief.cached(ctx)
        ids.forEach { draw(ctx, mgr, it, brief) }
        Refresh.schedule(ctx)
        Refresh.now(ctx)
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, options: Bundle) {
        draw(ctx, mgr, id, Brief.cached(ctx))
    }

    override fun onEnabled(ctx: Context) {
        Refresh.schedule(ctx)
        Refresh.now(ctx)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, javaClass))
            if (ids.isNotEmpty()) {
                val note = RemoteViews(ctx.packageName, layoutFor(ctx)).apply { setTextViewText(R.id.updated, "↻  Updating…") }
                mgr.partiallyUpdateAppWidget(ids, note)
            }
            Refresh.now(ctx, force = true)
            return
        }
        super.onReceive(ctx, intent)
    }

    /** "Updated Fri 9 Oct" refreshes on tap; the right-hand link opens the radar on this widget's tab. */
    protected fun footer(ctx: Context, v: RemoteViews, brief: JSONObject?, hash: String) {
        val up = Fmt.updated(brief?.str("updated_at"))
        v.setTextViewText(R.id.updated, up.ifEmpty { "↻  Refresh" })
        v.setOnClickPendingIntent(R.id.updated, refresh(ctx, javaClass, code + 98))
        v.setOnClickPendingIntent(R.id.more, app(ctx, if (hash == "week") "home" else hash, null, code + 99))
    }

    /** Fills up to [n] move rows; each opens its source, or the radar when a move has none. */
    protected fun moveRows(ctx: Context, v: RemoteViews, brief: JSONObject?, items: List<JSONObject>, n: Int,
                           rows: Int, typeColor: String, firstCode: Int) {
        for (i in 0 until rows) {
            val m = items.getOrNull(i)?.takeIf { i < n }
            val show = if (m != null) View.VISIBLE else View.GONE
            v.setViewVisibility(ROW[i], show)
            if (i > 0) v.setViewVisibility(DIV[i], show)
            if (m == null) continue
            val type = m.str("type") ?: "Regulation"
            v.setImageViewResource(GLYPH[i], Fmt.glyph(type, mono(ctx)))
            v.setTextViewText(META[i], Fmt.meta(m.str("type_label") ?: type, m.str("regulator") ?: m.str("company"), m.str("date"), typeColor))
            v.setTextViewText(TITLE[i], m.str("title") ?: "")
            // Rows open the move in the app; a brief from before moves had ids falls back to the source.
            val target = m.str("id")?.let { app(ctx, "move", it, firstCode + i) }
                ?: m.str("source_url")?.takeIf { it.startsWith("https://") }?.let { open(ctx, it, firstCode + i) }
                ?: app(ctx, "moves", null, firstCode + i)
            v.setOnClickPendingIntent(ROW[i], target)
        }
    }

    /** One placeholder row while the brief loads, or when a list is empty. */
    protected fun placeholder(ctx: Context, v: RemoteViews, brief: JSONObject?, rows: Int, text: String, hash: String) {
        for (i in 0 until rows) {
            v.setViewVisibility(ROW[i], if (i == 0) View.VISIBLE else View.GONE)
            if (i > 0) v.setViewVisibility(DIV[i], View.GONE)
        }
        v.setImageViewResource(GLYPH[0], if (mono(ctx)) R.drawable.mono_g_dot else R.drawable.g_dot)
        v.setTextViewText(META[0], "")
        v.setTextViewText(TITLE[0], text)
        v.setOnClickPendingIntent(ROW[0], app(ctx, if (hash == "week") "home" else hash, null, code + 97))
    }

    companion object {
        const val ACTION_REFRESH = "inc.axon.radar.action.REFRESH"
        private const val BLUE = "#2852EA"
        private const val SKY = "#77A1FD"

        fun mono(ctx: Context): Boolean = Store.look(ctx) == Look.Mono

        /** The accent on white widgets: blue, or black in black and white. */
        fun accent(ctx: Context): String = if (mono(ctx)) "#000000" else BLUE
        /** The quieter accent after a name, such as "· 2 moves". */
        fun accentSoft(ctx: Context): String = if (mono(ctx)) "#6E6E6E" else BLUE
        /** The accent on the dark licences widget. */
        fun accentOnDark(ctx: Context): String = if (mono(ctx)) "#C9C9C9" else SKY

        val ROW = intArrayOf(R.id.row1, R.id.row2, R.id.row3, R.id.row4, R.id.row5, R.id.row6)
        val DIV = intArrayOf(0, R.id.div2, R.id.div3, R.id.div4, R.id.div5, R.id.div6)
        val GLYPH = intArrayOf(R.id.glyph1, R.id.glyph2, R.id.glyph3, R.id.glyph4, R.id.glyph5)
        val META = intArrayOf(R.id.meta1, R.id.meta2, R.id.meta3, R.id.meta4, R.id.meta5)
        val TITLE = intArrayOf(R.id.title1, R.id.title2, R.id.title3, R.id.title4, R.id.title5)

        fun all(): List<RadarWidget> =
            listOf(BriefWidget(), MovesWidget(), CompaniesWidget(), LicencesWidget(), TrendsWidget(), DashboardWidget(), CarouselWidget())

        fun updateAll(ctx: Context) {
            val brief = Brief.cached(ctx)
            val mgr = AppWidgetManager.getInstance(ctx)
            all().forEach { w -> mgr.getAppWidgetIds(ComponentName(ctx, w.javaClass)).forEach { w.draw(ctx, mgr, it, brief) } }
        }

        fun open(ctx: Context, url: String, requestCode: Int): PendingIntent {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return PendingIntent.getActivity(ctx, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
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

        fun pad2(n: Int) = n.toString().padStart(2, '0')
    }
}

/** Briefing tab, 4 x 2: moves in the last 7 days, what is new today, and the headline. */
class BriefWidget : RadarWidget() {
    override val layout = R.layout.widget_brief
    override val monoLayout = R.layout.widget_brief_mono
    override val fallback = Dims(360, 170)
    override val code = 100

    override fun build(ctx: Context, brief: JSONObject?, d: Dims): RemoteViews {
        val v = RemoteViews(ctx.packageName, layoutFor(ctx))
        v.setOnClickPendingIntent(R.id.tap, app(ctx, "home", null, code))
        footer(ctx, v, brief, "week")
        v.setInt(R.id.headline, "setMaxLines", ((d.h - (if (d.h >= 200) 110 else 72)) / 18).coerceIn(2, 8))
        if (brief == null) {
            v.setTextViewText(R.id.eyebrow, "Market Radar")
            v.setTextViewText(R.id.count, "–")
            v.setTextViewText(R.id.dates, "")
            v.setViewVisibility(R.id.chip, View.GONE)
            v.setViewVisibility(R.id.mix, View.GONE)
            v.setTextViewText(R.id.headline, "Loading the daily brief…")
            return v
        }
        val n = brief.optInt("count")
        val fresh = brief.optInt("new_today")
        v.setTextViewText(R.id.eyebrow, "Daily briefing · " + Fmt.dowDay(brief.str("window_end")))
        v.setTextViewText(R.id.count, n.toString())
        v.setTextViewText(R.id.count_label, if (n == 1) "Move\nin 7 days" else "Moves\nin 7 days")
        v.setTextViewText(R.id.dates, Fmt.range(brief.str("window_start"), brief.str("window_end")))
        v.setViewVisibility(R.id.chip, if (fresh > 0) View.VISIBLE else View.GONE)
        v.setTextViewText(R.id.chip, "+$fresh new")
        val head = brief.str("headline")
        v.setTextViewText(R.id.headline, if (head != null) Fmt.headline(head) else Fmt.html(Fmt.escape("A quiet week. Nothing new passed the source check.")))
        val mix = brief.str("mix_text")
        v.setViewVisibility(R.id.mix, if (mix != null && d.h >= 200) View.VISIBLE else View.GONE)
        // At most three kinds, numbers kept with their words, and line breaks after a dot rather than before it.
        val parts = mix?.split(" · ")?.take(3)?.map { it.replace(Regex("(\\d) "), "$1\u00A0") }.orEmpty()
        v.setTextViewText(R.id.mix, parts.joinToString("\u00A0· "))
        return v
    }
}

/** Moves tab, 4 x 4: the latest moves, each opening its source. */
class MovesWidget : RadarWidget() {
    override val layout = R.layout.widget_moves
    override val monoLayout = R.layout.widget_moves_mono
    override val fallback = Dims(360, 400)
    override val code = 200

    override fun build(ctx: Context, brief: JSONObject?, d: Dims): RemoteViews {
        val v = RemoteViews(ctx.packageName, layoutFor(ctx))
        v.setOnClickPendingIntent(R.id.head, app(ctx, "moves", null, code))
        footer(ctx, v, brief, "moves")
        if (brief == null) {
            v.setTextViewText(R.id.stat, "")
            placeholder(ctx, v, null, 5, "Loading the latest moves…", "moves")
            return v
        }
        val n = brief.optInt("count")
        v.setTextViewText(R.id.stat, "$n in 7 days")
        val items = brief.objects("latest").ifEmpty { brief.objects("moves") }
        if (items.isEmpty()) {
            placeholder(ctx, v, brief, 5, "Nothing on the radar yet.", "moves")
            return v
        }
        moveRows(ctx, v, brief, items, ((d.h - 117) / 71).coerceIn(1, 5), 5, accent(ctx), code + 10)
        return v
    }
}

/** Companies tab, 4 x 4: the most active companies over 30 days. */
class CompaniesWidget : RadarWidget() {
    override val layout = R.layout.widget_companies
    override val monoLayout = R.layout.widget_companies_mono
    override val fallback = Dims(360, 400)
    override val code = 300

    private val MONO = intArrayOf(R.id.mono1, R.id.mono2, R.id.mono3, R.id.mono4, R.id.mono5, R.id.mono6)
    private val NAME = intArrayOf(R.id.name1, R.id.name2, R.id.name3, R.id.name4, R.id.name5, R.id.name6)
    private val SUB = intArrayOf(R.id.sub1, R.id.sub2, R.id.sub3, R.id.sub4, R.id.sub5, R.id.sub6)
    private val LAST = intArrayOf(R.id.last1, R.id.last2, R.id.last3, R.id.last4, R.id.last5, R.id.last6)
    private val N = intArrayOf(R.id.n1, R.id.n2, R.id.n3, R.id.n4, R.id.n5, R.id.n6)

    override fun build(ctx: Context, brief: JSONObject?, d: Dims): RemoteViews {
        val v = RemoteViews(ctx.packageName, layoutFor(ctx))
        v.setOnClickPendingIntent(R.id.head, app(ctx, "companies", null, code))
        footer(ctx, v, brief, "companies")
        val cos = brief?.objects("companies").orEmpty()
        val fit = ((d.h - 117) / 56).coerceIn(1, 6)
        for (i in 0 until 6) {
            val c = cos.getOrNull(i)?.takeIf { i < fit }
            val show = if (c != null) View.VISIBLE else View.GONE
            v.setViewVisibility(ROW[i], show)
            if (i > 0) v.setViewVisibility(DIV[i], show)
            if (c == null) continue
            val moves = c.optInt("moves_30d")
            v.setTextViewText(MONO[i], Fmt.initials(c.str("name")))
            v.setTextViewText(NAME[i], c.str("name") ?: "")
            v.setTextViewText(SUB[i], listOfNotNull(c.str("segment"), c.str("country")).joinToString(" · "))
            v.setTextViewText(LAST[i], Fmt.day(c.str("last_date")))
            v.setTextViewText(N[i], if (moves > 0) Fmt.plural(moves, "move", "moves") else "Last move")
            v.setOnClickPendingIntent(ROW[i], c.str("id")?.let { app(ctx, "company", it, code + 10 + i) } ?: app(ctx, "companies", null, code + 10 + i))
        }
        if (cos.isEmpty()) {
            v.setViewVisibility(ROW[0], View.VISIBLE)
            v.setTextViewText(MONO[0], "·")
            v.setTextViewText(NAME[0], if (brief == null) "Loading…" else "No company moves yet")
            v.setTextViewText(SUB[0], "Open the radar for every company")
            v.setTextViewText(LAST[0], "")
            v.setTextViewText(N[0], "")
            v.setOnClickPendingIntent(ROW[0], app(ctx, "companies", null, code + 10))
        }
        return v
    }
}

/** Licences tab, 4 x 4, navy: the busiest regulators, then the newest licences and rules. */
class LicencesWidget : RadarWidget() {
    override val layout = R.layout.widget_licences
    override val monoLayout = R.layout.widget_licences_mono
    override val fallback = Dims(360, 400)
    override val code = 400

    private val TILE = intArrayOf(R.id.tile1, R.id.tile2, R.id.tile3)
    private val TSEP = intArrayOf(0, R.id.tsep2, R.id.tsep3)
    private val TN = intArrayOf(R.id.tile_n1, R.id.tile_n2, R.id.tile_n3)
    private val TNAME = intArrayOf(R.id.tile_name1, R.id.tile_name2, R.id.tile_name3)
    private val TSUB = intArrayOf(R.id.tile_sub1, R.id.tile_sub2, R.id.tile_sub3)

    override fun build(ctx: Context, brief: JSONObject?, d: Dims): RemoteViews {
        val v = RemoteViews(ctx.packageName, layoutFor(ctx))
        val tab = app(ctx, "licences", null, code)
        v.setOnClickPendingIntent(R.id.head, tab)
        v.setOnClickPendingIntent(R.id.tiles, tab)
        footer(ctx, v, brief, "licences")
        val regs = brief?.objects("regulators").orEmpty()
        for (i in 0 until 3) {
            val r = regs.getOrNull(i)
            val show = if (r != null) View.VISIBLE else View.INVISIBLE
            v.setViewVisibility(TILE[i], show)
            if (i > 0) v.setViewVisibility(TSEP[i], show)
            if (r == null) continue
            v.setTextViewText(TN[i], pad2(r.optInt("licences") + r.optInt("actions")))
            v.setTextViewText(TNAME[i], r.str("name") ?: "")
            v.setTextViewText(TSUB[i], r.str("where") ?: "")
        }
        val items = brief?.objects("licences").orEmpty()
        if (items.isEmpty()) {
            placeholder(ctx, v, brief, 4, if (brief == null) "Loading licences and rules…" else "No licences or rule changes yet.", "licences")
            return v
        }
        moveRows(ctx, v, brief, items, ((d.h - 221) / 72).coerceIn(1, 4), 4, accentOnDark(ctx), code + 10)
        return v
    }
}

/** Trends tab, 4 x 3: the year's totals and moves per month. */
class TrendsWidget : RadarWidget() {
    override val layout = R.layout.widget_trends
    override val monoLayout = R.layout.widget_trends_mono
    override val fallback = Dims(360, 300)
    override val code = 500

    override fun build(ctx: Context, brief: JSONObject?, d: Dims): RemoteViews {
        val v = RemoteViews(ctx.packageName, layoutFor(ctx))
        val tab = app(ctx, "trends", null, code)
        v.setOnClickPendingIntent(R.id.top, tab)
        v.setOnClickPendingIntent(R.id.bottom, tab)
        val t = brief?.optJSONObject("trends")
        if (t == null) {
            v.setTextViewText(R.id.title, if (brief == null) "Loading trends…" else "Refreshing soon")
            intArrayOf(R.id.s_n1, R.id.s_n2, R.id.s_n3, R.id.s_n4).forEach { v.setTextViewText(it, "–") }
            v.setTextViewText(R.id.cap, "")
            v.setViewVisibility(R.id.chart, View.INVISIBLE)
            return v
        }
        v.setTextViewText(R.id.title, "${t.optInt("total")} moves since ${Fmt.month(t.str("since"))}")
        val stats = listOf(
            Triple(t.optInt("licences"), "Licence", "Licences"),
            Triple(t.optInt("regulatory_actions"), "Regulatory action", "Regulatory actions"),
            Triple(t.optInt("commercial"), "Commercial move", "Commercial moves"),
            Triple(t.optInt("capital"), "Capital move", "Capital moves"),
        )
        val ns = intArrayOf(R.id.s_n1, R.id.s_n2, R.id.s_n3, R.id.s_n4)
        val ls = intArrayOf(R.id.s_l1, R.id.s_l2, R.id.s_l3, R.id.s_l4)
        stats.forEachIndexed { i, (n, one, many) ->
            v.setTextViewText(ns[i], n.toString())
            v.setTextViewText(ls[i], if (n == 1) one else many)
        }
        val months = t.objects("months")
        v.setTextViewText(R.id.cap, if (months.isEmpty()) "" else "${Fmt.month(months.first().str("m"))} – ${Fmt.month(months.last().str("m"))}")
        Chart.months(ctx, t, d.w - 32, (d.h - 191).coerceAtLeast(56))?.let { v.setImageViewBitmap(R.id.chart, it) }
        return v
    }
}

/** Everything at once, 4 x 5: briefing, regions, latest moves, companies, regulators and the trend. */
class DashboardWidget : RadarWidget() {
    override val layout = R.layout.widget_dashboard
    override val monoLayout = R.layout.widget_dashboard_mono
    override val fallback = Dims(360, 620)
    override val code = 600

    override fun build(ctx: Context, brief: JSONObject?, d: Dims): RemoteViews {
        val v = RemoteViews(ctx.packageName, layoutFor(ctx))
        v.setOnClickPendingIntent(R.id.top, app(ctx, "home", null, code))
        v.setOnClickPendingIntent(R.id.regions, app(ctx, "moves", null, code + 1))
        v.setOnClickPendingIntent(R.id.co_col, app(ctx, "companies", null, code + 2))
        v.setOnClickPendingIntent(R.id.reg_col, app(ctx, "licences", null, code + 3))
        v.setOnClickPendingIntent(R.id.trend, app(ctx, "trends", null, code + 4))
        footer(ctx, v, brief, "week")

        // Decide what fits: two moves first, then companies and regulators, a third move, then the chart.
        // Heights measured in the render test: cover 174dp, regions 61, list label 24, move row 69,
        // companies and regulators 80, chart label 22, footer 44.
        var room = d.h - 326
        var rows = if (room >= 138) 2 else 1
        room -= rows * 69
        val split = room >= 80 + 50
        if (split) room -= 80
        if (room >= 69 + 60) { rows = 3; room -= 69 }
        val chartH = room - 2
        showChart(v, split, chartH >= 44)

        if (brief == null) {
            v.setTextViewText(R.id.eyebrow, "Market Radar")
            v.setTextViewText(R.id.count, "–")
            v.setTextViewText(R.id.dates, "")
            v.setViewVisibility(R.id.chip, View.GONE)
            v.setTextViewText(R.id.headline, "Loading the daily brief…")
            intArrayOf(R.id.r_n1, R.id.r_n2, R.id.r_n3, R.id.r_n4).forEach { v.setTextViewText(it, "–") }
            placeholder(ctx, v, null, 3, "Loading the latest moves…", "moves")
            showChart(v, false, false)
            return v
        }

        val n = brief.optInt("count")
        val fresh = brief.optInt("new_today")
        v.setTextViewText(R.id.eyebrow, "Daily briefing · " + Fmt.dowDay(brief.str("window_end")))
        v.setTextViewText(R.id.count, n.toString())
        v.setTextViewText(R.id.count_label, if (n == 1) "Move in 7 days" else "Moves in 7 days")
        v.setTextViewText(R.id.dates, Fmt.range(brief.str("window_start"), brief.str("window_end"), year = true))
        v.setViewVisibility(R.id.chip, if (fresh > 0) View.VISIBLE else View.GONE)
        v.setTextViewText(R.id.chip, "+$fresh new")
        val head = brief.str("headline")
        v.setTextViewText(R.id.headline, if (head != null) Fmt.headline(head) else Fmt.html("A quiet week. Nothing new passed the source check."))

        val regions = brief.optJSONObject("regions")
        listOf("Europe", "Middle East", "North America", "Global").forEachIndexed { i, r ->
            v.setTextViewText(intArrayOf(R.id.r_n1, R.id.r_n2, R.id.r_n3, R.id.r_n4)[i], (regions?.optInt(r) ?: 0).toString())
        }

        val items = brief.objects("latest").ifEmpty { brief.objects("moves") }
        if (items.isEmpty()) placeholder(ctx, v, brief, 3, "Nothing on the radar yet.", "moves")
        else moveRows(ctx, v, brief, items, rows, 3, accent(ctx), code + 10)

        val cos = brief.objects("companies").take(3)
        v.setTextViewText(R.id.co_lines, Fmt.html(cos.joinToString("<br>") { c ->
            val m = c.optInt("moves_30d")
            val tail = if (m > 0) Fmt.plural(m, "move", "moves") else Fmt.day(c.str("last_date"))
            "${Fmt.escape(c.str("name") ?: "")} <font color=\"${accentSoft(ctx)}\">· ${Fmt.escape(tail)}</font>"
        }.ifEmpty { "No company moves yet" }))
        val regs = brief.objects("regulators").take(3)
        v.setTextViewText(R.id.reg_lines, Fmt.html(regs.joinToString("<br>") { r ->
            val total = r.optInt("licences") + r.optInt("actions")
            "${Fmt.escape(r.str("name") ?: "")} <font color=\"${accentSoft(ctx)}\">· ${Fmt.escape(Fmt.plural(total, "move", "moves"))}</font>"
        }.ifEmpty { "No regulator moves yet" }))

        val t = brief.optJSONObject("trends")
        if (t != null && chartH >= 44) {
            v.setTextViewText(R.id.cap, "${t.optInt("total")} since ${Fmt.month(t.str("since"))}")
            Chart.months(ctx, t, d.w - 32, chartH)?.let { v.setImageViewBitmap(R.id.chart, it) }
        } else {
            showChart(v, split, false)
        }
        return v
    }

    private fun showChart(v: RemoteViews, split: Boolean, chart: Boolean) {
        v.setViewVisibility(R.id.split, if (split) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.trend, if (chart) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.spacer, if (chart) View.GONE else View.VISIBLE)
    }
}
