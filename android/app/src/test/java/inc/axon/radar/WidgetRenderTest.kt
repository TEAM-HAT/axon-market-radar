package inc.axon.radar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import inc.axon.radar.data.Store
import inc.axon.radar.ui.Look
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Inflates every widget the way a launcher does, at several sizes, and saves what they look like. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetRenderTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val brief = JSONObject(File("../../brief.json").readText())
    private var out = File("build/widget-previews").apply { mkdirs() }
    private val log = StringBuilder()

    @Before
    fun colour() {
        Store.setLook(ctx, Look.Colour)
        File(ctx.filesDir, "radar.json").writeText(File("../../radar.json").readText())
    }

    private fun render(views: RemoteViews, d: Dims, name: String): View {
        val v = views.apply(ctx, FrameLayout(ctx))
        val den = ctx.resources.displayMetrics.density
        val w = (d.w * den).toInt()
        val h = (d.h * den).toInt()
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF1C2033.toInt())
        v.draw(c)
        FileOutputStream(File(out, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        log.append(name).append(" ").append(d.w).append("x").append(d.h).append(":")
        listOf("head" to R.id.head, "top" to R.id.top, "regions" to R.id.regions, "tiles" to R.id.tiles, "list" to R.id.list,
            "split" to R.id.split, "trend" to R.id.trend, "chart" to R.id.chart, "spacer" to R.id.spacer, "footer" to R.id.footer,
            "row1" to R.id.row1, "row2" to R.id.row2, "row3" to R.id.row3, "row4" to R.id.row4, "row5" to R.id.row5)
            .forEach { (k, id) ->
                val x = v.findViewById<View>(id)
                if (x != null && x.visibility == View.VISIBLE) log.append(" ").append(k).append("=").append((x.height / den).toInt())
            }
        log.append("\n")
        return v
    }

    @Test
    fun rendersEveryWidget() = everyWidget()

    /** The same widgets after the app is switched to black and white. */
    @Test
    fun rendersEveryWidgetInBlackAndWhite() {
        Store.setLook(ctx, Look.Mono)
        out = File("build/widget-previews-mono").apply { mkdirs() }
        everyWidget()
        Store.setLook(ctx, Look.Colour)
    }

    private fun everyWidget() {
        val cases = listOf(
            Triple(BriefWidget(), Dims(360, 170), "1-brief-170"),
            Triple(BriefWidget(), Dims(360, 248), "1-brief-248"),
            Triple(MovesWidget(), Dims(360, 360), "2-moves-360"),
            Triple(MovesWidget(), Dims(360, 496), "2-moves-496"),
            Triple(CompaniesWidget(), Dims(360, 360), "3-companies-360"),
            Triple(CompaniesWidget(), Dims(360, 496), "3-companies-496"),
            Triple(LicencesWidget(), Dims(360, 400), "4-licences-400"),
            Triple(LicencesWidget(), Dims(360, 496), "4-licences-496"),
            Triple(TrendsWidget(), Dims(360, 280), "5-trends-280"),
            Triple(TrendsWidget(), Dims(360, 372), "5-trends-372"),
            Triple(DashboardWidget(), Dims(360, 496), "6-dashboard-496"),
            Triple(DashboardWidget(), Dims(360, 620), "6-dashboard-620"),
            Triple(DashboardWidget(), Dims(360, 740), "6-dashboard-740"),
        )
        cases.forEach { (w, d, name) -> render(w.build(ctx, brief, d), d, name) }
        val fresh = JSONObject(brief.toString()).put("new_today", 2)
        render(BriefWidget().build(ctx, fresh, Dims(360, 248)), Dims(360, 248), "7-brief-new")
        render(DashboardWidget().build(ctx, fresh, Dims(360, 620)), Dims(360, 620), "7-dashboard-new")
        RadarWidget.all().filter { it !is CarouselWidget }.forEach { w -> render(w.build(ctx, null, w.fallback), w.fallback, "0-empty-" + w.javaClass.simpleName) }
        carousel()
        File(out, "measure.txt").writeText(log.toString())
    }

    /**
     * The carousel the way a launcher shows it: the frame from the provider, and its list filled with the
     * factory's cards, scrolled to the top, part-way through a swipe, and further down the deck.
     */
    private fun carousel() {
        val deck = inc.axon.radar.data.Radar.parse(File("../../radar.json").readText()).deck
        val ink = inc.axon.radar.ui.Ink.of(Store.look(ctx))
        listOf(Dims(360, 300), Dims(360, 420), Dims(360, 560), Dims(270, 420)).forEach { d ->
            listOf(0 to 0, 1 to 120, 4 to 0).forEach { (first, offsetDp) ->
                renderCarousel(deck, ink, d, first, offsetDp, "8-carousel-${d.w}x${d.h}-at$first" + (if (offsetDp > 0) "-swiping" else ""))
            }
        }
        // The picker preview, as the launcher's widget list shows it.
        val preview = android.view.LayoutInflater.from(ctx).inflate(R.layout.widget_carousel_preview, FrameLayout(ctx), false)
        draw(preview, Dims(360, 420), "8-carousel-preview")
    }

    private fun renderCarousel(deck: List<inc.axon.radar.data.Move>, ink: inc.axon.radar.ui.Ink, d: Dims, first: Int, offsetDp: Int, name: String) {
        val frame = CarouselWidget().buildFor(ctx, brief, d, 42)
        val root = runCatching { frame.apply(ctx, FrameLayout(ctx)) }.getOrElse {
            android.view.LayoutInflater.from(ctx).inflate(R.layout.widget_carousel, FrameLayout(ctx), false)
        }
        val list = root.findViewById<android.widget.ListView>(R.id.list)
        list.adapter = object : android.widget.BaseAdapter() {
            override fun getCount() = deck.size
            override fun getItem(p: Int) = deck[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, convert: View?, parent: android.view.ViewGroup): View = CarouselWidget.card(ctx, deck, p, ink, d).apply(ctx, parent)
        }
        root.findViewById<View>(R.id.empty).visibility = View.GONE
        val den = ctx.resources.displayMetrics.density
        list.setSelectionFromTop(first, -(offsetDp * den).toInt())
        draw(root, d, name)
    }

    private fun draw(v: View, d: Dims, name: String) {
        val den = ctx.resources.displayMetrics.density
        val w = (d.w * den).toInt()
        val h = (d.h * den).toInt()
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, w, h)
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF1C2033.toInt())
        v.draw(c)
        FileOutputStream(File(out, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** A phone still holding last week's file (schema 1) must keep drawing until the next refresh. */
    @Test
    fun drawsFromTheOldBriefFormat() {
        val old = JSONObject(
            """{"schema":1,"updated_at":"2026-10-09T09:00:00+03:00","window_start":"2026-10-03","window_end":"2026-10-09","week":41,
            "count":6,"headline":"Dubai gave **Rain a full exchange licence**.","mix_text":"4 regulatory actions","moves":[{"date":"2026-10-08",
            "type":"Regulation","type_label":"Regulation","regulator":"ESMA","title":"ESMA sets three-month wind-down","source_url":"https://www.esma.europa.eu/"}],
            "radar_url":"https://claude.ai/artifact/ApmPVY9n2xQFf4yP4qMbei"}"""
        )
        RadarWidget.all().forEach { w -> render(w.build(ctx, old, w.fallback), w.fallback, "0-old-" + w.javaClass.simpleName) }
    }
}
