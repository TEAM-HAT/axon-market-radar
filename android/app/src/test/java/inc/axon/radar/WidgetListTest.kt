package inc.axon.radar

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Parcel
import android.view.View
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import inc.axon.radar.data.Radar
import inc.axon.radar.data.Store
import inc.axon.radar.data.Text
import inc.axon.radar.ui.Link
import inc.axon.radar.ui.Look
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File

/**
 * The widgets' lists the way a launcher shows them: what each holds, that every row and box opens the right
 * thing in the app, that the dashboard only shows what fits, and that every widget travels to the launcher
 * within Android's limits, on new phones and old.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class WidgetListTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val json = File("../../radar.json").readText()
    private val radar = Radar.parse(json)
    private val den get() = ctx.resources.displayMetrics.density

    @Before
    fun setUp() {
        Store.setLook(ctx, Look.Colour)
        File(ctx.filesDir, "radar.json").writeText(json)
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx)
    }

    private fun mount(w: RadarWidget, d: Dims) = CarouselStage.mount(ctx, w.build(ctx, d, 5), d)

    /** Taps [v] and returns where the app was asked to open. */
    private fun tap(v: View): Link? {
        v.performClick()
        val started = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertNotNull("a tap opens the app", started)
        return Link.from(started)
    }

    /** Every row and box showing in [list], with what it says, scrolling it from top to bottom. */
    private fun everyItem(list: AdapterView<*>, each: (position: Int, item: View) -> Unit) {
        val seen = HashSet<Int>()
        var at = 0
        while (at < list.adapter.count) {
            list.setSelection(at)
            CarouselStage.idle(100)
            for (i in 0 until list.childCount) {
                val p = list.firstVisiblePosition + i
                if (seen.add(p)) each(p, list.getChildAt(i))
            }
            at = list.lastVisiblePosition.coerceAtLeast(at + 1)
            if (seen.size >= list.adapter.count) break
        }
        assertEquals("every item was shown", list.adapter.count, seen.size)
    }

    private fun title(item: View) = item.findViewById<TextView>(R.id.title)?.text?.toString()

    /** The briefing's list is the latest ten moves, newest first, and each opens its move in the app. */
    @Test
    fun briefingListsTheLatestTen() {
        val root = mount(BriefWidget(), Dims(360, 560))
        val list = root.findViewById<AbsListView>(R.id.list)
        assertEquals(View.VISIBLE, list.visibility)
        assertEquals(10, list.adapter.count)
        everyItem(list) { p, item ->
            assertEquals(radar.moves[p].title, title(item))
            assertEquals(Link("move", radar.moves[p].id), tap(item.findViewById(R.id.row)))
        }
        assertEquals(Link("home"), tap(root.findViewById(R.id.hero)))
    }

    /** Too short for a row, the briefing keeps to its count and headline, and the headline takes the room. */
    @Test
    fun aShortBriefingIsItsHeadline() {
        val root = mount(BriefWidget(), Dims(360, 250))
        assertEquals(View.GONE, root.findViewById<View>(R.id.list).visibility)
        val headline = root.findViewById<TextView>(R.id.headline)
        assertTrue("the headline has ${headline.maxLines} lines", headline.maxLines > 6)
        assertFits(root, Dims(360, 250))
    }

    /** The moves list holds the latest fifty under their months' headers, each opening its move. */
    @Test
    fun movesListEveryMoveUnderItsMonth() {
        val root = mount(MovesWidget(), Dims(360, 496))
        val list = root.findViewById<AbsListView>(R.id.list)
        val moves = radar.moves.take(MovesWidget.MOST)
        val months = moves.map { it.date.take(7) }.distinct()
        assertEquals(moves.size + months.size, list.adapter.count)
        var row = 0
        everyItem(list) { _, item ->
            val header = item.findViewById<TextView>(R.id.month_label)
            if (header != null) {
                val ym = moves[row].date.take(7)
                assertEquals(Text.monthFull("$ym-01"), header.text.toString())
                assertEquals(radar.moves.count { it.date.startsWith(ym) }.toString(), item.findViewById<TextView>(R.id.month_n).text.toString())
            } else {
                assertEquals(moves[row].title, title(item))
                if (row < 6) assertEquals(Link("move", moves[row].id), tap(item.findViewById(R.id.row)))
                row++
            }
        }
        assertEquals(moves.size, row)
        Kit.TILES.forEachIndexed { i, ids -> assertEquals(Link("move", radar.moves[i].id), tap(root.findViewById(ids.tile))) }
        assertEquals(Link("moves"), tap(root.findViewById(R.id.head)))
    }

    /** The licences list holds only licences and rules, on the licence colour. */
    @Test
    fun licencesListOnlyLicencesAndRules() {
        val root = mount(LicencesWidget(), Dims(360, 496))
        val list = root.findViewById<AbsListView>(R.id.list)
        val rules = radar.moves.filter { it.type == "License" || it.type == "Regulation" }
        val shown = rules.take(LicencesWidget.MOST)
        assertEquals(shown.size + shown.map { it.date.take(7) }.distinct().size, list.adapter.count)
        val titles = ArrayList<String>()
        everyItem(list) { _, item -> title(item)?.let { titles += it } }
        assertEquals(shown.map { it.title }, titles)
        // The busiest regulator's tile opens its latest move.
        val busiest = radar.regulators.maxBy { it.total }
        assertEquals(Link("move", radar.movesForRegulator(busiest.name).first().id), tap(root.findViewById(R.id.g1)))
    }

    /** The companies grid holds the most active companies, and a box opens its company. */
    @Test
    fun companiesOpenTheirCompany() {
        val root = mount(CompaniesWidget(), Dims(360, 496))
        val grid = root.findViewById<AbsListView>(R.id.grid)
        val active = Kit.active(radar).take(CompaniesWidget.MOST)
        assertEquals(active.size, grid.adapter.count)
        everyItem(grid) { p, item ->
            assertEquals(active[p].first.name, item.findViewById<TextView>(R.id.box_name).text.toString())
            if (p < 4) assertEquals(Link("company", active[p].first.id), tap(item.findViewById(R.id.box)))
        }
    }

    /** The dashboard shows as much as fits, in order, and nothing runs off its bottom. */
    @Test
    fun dashboardShowsWhatFits() {
        fun shown(root: View, id: Int) = root.findViewById<View>(id).visibility == View.VISIBLE
        val small = mount(DashboardWidget(), Dims(360, 400))
        assertTrue(shown(small, R.id.r1_row_item))
        assertTrue(!shown(small, R.id.boxes) && !shown(small, R.id.tiles) && !shown(small, R.id.chart))
        assertFits(small, Dims(360, 400))
        val tall = mount(DashboardWidget(), Dims(360, 740))
        listOf(R.id.r1_row_item, R.id.r2_row_item, R.id.boxes, R.id.tiles, R.id.chart).forEach { assertTrue(shown(tall, it)) }
        assertFits(tall, Dims(360, 740))
        listOf(460, 540, 620, 680, 820).forEach { assertFits(mount(DashboardWidget(), Dims(360, it)), Dims(360, it)) }
        assertEquals(Link("company", Kit.active(radar).first().first.id), tap(tall.findViewById(R.id.c1_box)))
        assertEquals(Link("trends"), tap(tall.findViewById(R.id.chart)))
    }

    /** The trends widget's chart fills what is left without running off the bottom, at every height. */
    @Test
    fun trendsChartFitsTheRoomLeft() {
        listOf(220, 280, 330, 372, 460).forEach { h ->
            val root = mount(TrendsWidget(), Dims(360, h))
            assertFits(root, Dims(360, h))
            if (h >= 280) assertEquals("chart at $h", View.VISIBLE, root.findViewById<View>(R.id.chart).visibility)
        }
    }

    /** Every part that shows ends inside the widget. */
    private fun assertFits(root: View, d: Dims) {
        val bottom = (d.h * den).toInt()
        val r = Rect()
        fun walk(v: View) {
            if (v.visibility != View.VISIBLE) return
            if (v is AdapterView<*>) return // lists scroll
            if (v.height > 0) {
                v.getDrawingRect(r)
                (root as android.view.ViewGroup).offsetDescendantRectToMyCoords(v, r)
                assertTrue("${ctx.resources.getResourceEntryName(v.id.takeIf { it > 0 } ?: R.id.list)} ends at ${r.bottom}, below $bottom", r.bottom <= bottom + 1)
            }
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(root)
    }

    /**
     * Each widget, parcelled as it goes to the launcher and applied there, keeps its list; its pictures take far
     * less than the 1.5 screens of memory Android allows a widget.
     */
    @Test
    fun everyWidgetTravelsToTheLauncher() {
        val dm = ctx.resources.displayMetrics
        val allowance = 6L * dm.widthPixels * dm.heightPixels
        val estimate = RemoteViews::class.java.getDeclaredMethod("estimateMemoryUsage").apply { isAccessible = true }
        listOf(BriefWidget() to Dims(360, 560), MovesWidget() to Dims(360, 496), CompaniesWidget() to Dims(360, 496),
            LicencesWidget() to Dims(360, 496), TrendsWidget() to Dims(360, 372), DashboardWidget() to Dims(360, 740)).forEach { (w, d) ->
            val views = w.build(ctx, d, 9)
            val bytes = (estimate.invoke(views) as Number).toLong()
            assertTrue("${w.javaClass.simpleName} takes $bytes bytes", bytes < allowance / 4)
            val parcel = Parcel.obtain()
            views.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val sent = RemoteViews(parcel)
            parcel.recycle()
            val root = CarouselStage.host(ctx, sent)
            w.listId?.let { assertTrue("${w.javaClass.simpleName} keeps its list", root.findViewById<AdapterView<*>>(it).adapter.count > 0) }
        }
    }

    /** Before Android 12 the launcher asks the service for each list; it gets the same rows and boxes. */
    @Test
    fun olderPhonesGetTheSameLists() {
        listOf("brief" to 10, "moves" to radar.moves.take(MovesWidget.MOST).let { it.size + it.map { m -> m.date.take(7) }.distinct().size },
            "companies" to minOf(CompaniesWidget.MOST, radar.companies.size)).forEach { (kind, n) ->
            val f = ListsFactory(ctx, kind, 7).apply { onCreate() }
            assertEquals(kind, n, f.count)
            assertEquals(Kit.VIEW_TYPES, f.viewTypeCount)
            (0 until f.count).forEach { f.getViewAt(it) }
        }
        val tap = Intent(ctx, MainActivity::class.java)
        tap.fillIn(CarouselWidget.fillIn("company", "tether"), 0)
        assertEquals(Link("company", "tether"), Link.from(tap))
    }

    /** A tap on ↻ fades it and asks for the radar at once; a widget that is gone is left alone. */
    @Test
    fun refreshAsksForTheRadar() {
        RadarWidget.all().filter { it !is CarouselWidget }.forEach { w ->
            w.onReceive(ctx, Intent(ctx, w.javaClass).setAction(RadarWidget.ACTION_REFRESH))
        }
        val work = androidx.work.WorkManager.getInstance(ctx).getWorkInfosForUniqueWork("radar-now").get()
        assertTrue("a fetch is queued", work.isNotEmpty())
        val fade = RemoteViews(ctx.packageName, R.layout.widget_brief).apply { setInt(R.id.refresh, "setImageAlpha", 70) }
        val root = CarouselStage.host(ctx, BriefWidget().build(ctx, Dims(360, 300), AppWidgetManager.INVALID_APPWIDGET_ID))
        fade.reapply(ctx, root)
        assertEquals(70, root.findViewById<android.widget.ImageView>(R.id.refresh).imageAlpha)
    }
}
