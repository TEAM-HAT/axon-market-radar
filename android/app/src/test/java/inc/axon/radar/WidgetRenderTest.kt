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
     * The carousel the way a launcher shows it: the frame from the provider and the card stack filled with
     * the factory's cards, at the top of the deck, part-way down it and at its end, at several sizes.
     */
    private fun carousel() {
        val deck = inc.axon.radar.data.Radar.parse(File("../../radar.json").readText()).deck
        val id = 42
        listOf(Dims(360, 300), Dims(360, 360), Dims(360, 420), Dims(360, 560), Dims(270, 420)).forEach { d ->
            listOf(0, 4, deck.lastIndex).forEach { front -> renderCarousel(id, d, front, "8-carousel-${d.w}x${d.h}-front$front") }
        }
    }

    /**
     * The picture the launcher's widget list shows for the carousel: the widget at 4 x 4, newest card open, on
     * a clear background. With RADAR_WRITE_PREVIEW=1 it is also written into the app's resources.
     */
    @Test
    fun picturesTheCarouselForThePicker() {
        val d = Dims(360, 420)
        val root = CarouselStage.mount(ctx, brief, d, 41)
        CarouselStage.idle(800)
        val drawn = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(drawn))
        // The launcher clips the widget to its rounded background; drawing in software does not, so round it here.
        val r = 26 * ctx.resources.displayMetrics.density
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            clipPath(android.graphics.Path().apply { addRoundRect(0f, 0f, root.width.toFloat(), root.height.toFloat(), r, r, android.graphics.Path.Direction.CW) })
            drawBitmap(drawn, 0f, 0f, null)
        }
        FileOutputStream(File(out, "8-carousel-picker.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (System.getenv("RADAR_WRITE_PREVIEW") == "1") {
            FileOutputStream(File("src/main/res/drawable-nodpi/widget_carousel_preview.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun renderCarousel(id: Int, d: Dims, front: Int, name: String) {
        val root = CarouselStage.mount(ctx, brief, d, id)
        CarouselStage.stack(root).setDisplayedChild(front)
        CarouselStage.idle(800)
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF1C2033.toInt())
        root.draw(c)
        FileOutputStream(File(out, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
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

    /** The carousel on first install, before any data: the app's top line, no controls, and a word on what is coming. */
    @Test
    fun carouselBeforeAnyData() {
        File(ctx.filesDir, "radar.json").delete()
        val root = CarouselStage.mount(ctx, null, Dims(360, 420), 43)
        org.junit.Assert.assertEquals(View.INVISIBLE, root.findViewById<View>(R.id.controls).visibility)
        org.junit.Assert.assertEquals(View.VISIBLE, root.findViewById<View>(R.id.empty).visibility)
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF1C2033.toInt())
        root.draw(c)
        FileOutputStream(File(out, "0-empty-CarouselWidget.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
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
