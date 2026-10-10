package inc.axon.radar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.view.View
import androidx.test.core.app.ApplicationProvider
import inc.axon.radar.data.Store
import inc.axon.radar.ui.Look
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.io.FileOutputStream

/** Lays out every widget the way a launcher does, at several sizes and in both looks, and saves what they look like. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class WidgetRenderTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private var out = File("build/widget-previews").apply { mkdirs() }
    private val log = StringBuilder()
    private val den get() = ctx.resources.displayMetrics.density

    @Before
    fun colour() {
        Store.setLook(ctx, Look.Colour)
        File(ctx.filesDir, "radar.json").writeText(File("../../radar.json").readText())
    }

    private fun render(w: RadarWidget, d: Dims, name: String): View {
        val root = CarouselStage.mount(ctx, w.build(ctx, d, 0), d)
        CarouselStage.picture(root, File(out, "$name.png"))
        log.append(name).append(" ").append(d.w).append("x").append(d.h).append(":")
        listOf("top" to R.id.top, "hero" to R.id.hero, "mix" to R.id.mix, "head" to R.id.head, "tiles" to R.id.tiles, "since" to R.id.since,
            "stats" to R.id.stats, "list" to R.id.list, "grid" to R.id.grid, "cover" to R.id.cover, "rows" to R.id.rows, "boxes" to R.id.boxes,
            "chart" to R.id.chart, "legend" to R.id.legend)
            .forEach { (k, id) ->
                val x = root.findViewById<View>(id)
                if (x != null && x.visibility == View.VISIBLE) log.append(" ").append(k).append("=").append((x.height / den).toInt())
            }
        log.append("\n")
        return root
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
            Triple(BriefWidget(), "1-brief", listOf(170, 250, 320, 420, 560)),
            Triple(MovesWidget(), "2-moves", listOf(260, 360, 496)),
            Triple(CompaniesWidget(), "3-companies", listOf(300, 400, 496)),
            Triple(LicencesWidget(), "4-licences", listOf(260, 400, 496)),
            Triple(TrendsWidget(), "5-trends", listOf(200, 280, 372)),
            Triple(DashboardWidget(), "6-dashboard", listOf(400, 496, 620, 740)),
        )
        cases.forEach { (w, name, hs) ->
            hs.forEach { h -> render(w, Dims(360, h), "$name-$h") }
            render(w, Dims(280, w.fallback.h), "$name-narrow")
        }
        carousel()
        File(out, "measure.txt").writeText(log.toString())
    }

    /** The carousel scrolled to the top of its list, part-way down it and to its end, at several sizes. */
    private fun carousel() {
        val deck = inc.axon.radar.data.Radar.parse(File("../../radar.json").readText()).deck
        listOf(Dims(360, 300), Dims(360, 420), Dims(360, 560), Dims(270, 420)).forEach { d ->
            listOf(0, 4, deck.lastIndex).forEach { at ->
                val root = CarouselStage.mount(ctx, d, 42)
                CarouselStage.list(root).setSelection(at)
                CarouselStage.idle(400)
                CarouselStage.picture(root, File(out, "8-carousel-${d.w}x${d.h}-at$at.png"))
            }
        }
    }

    /** The widgets on a phone like the owner's: 400dp across at 360dpi, with text at 85%, in both looks. */
    @Test
    @Config(qualifiers = "w400dp-h889dp-360dpi", fontScale = 0.85f)
    fun rendersOnAPhoneLikeTheOwners() {
        listOf(Look.Colour to "build/widget-previews-phone", Look.Mono to "build/widget-previews-phone-mono").forEach { (look, dir) ->
            Store.setLook(ctx, look)
            out = File(dir).apply { mkdirs() }
            listOf(Triple(BriefWidget(), "1-brief", 509), Triple(BriefWidget(), "1-brief", 260), Triple(MovesWidget(), "2-moves", 509),
                Triple(CompaniesWidget(), "3-companies", 509), Triple(LicencesWidget(), "4-licences", 509), Triple(TrendsWidget(), "5-trends", 380),
                Triple(DashboardWidget(), "6-dashboard", 660)).forEach { (w, name, h) -> render(w, Dims(400, h), "$name-$h") }
            File(out, "measure.txt").writeText(log.toString())
        }
        Store.setLook(ctx, Look.Colour)
    }

    /**
     * The pictures the launcher's widget list shows: each widget at its usual size, on a clear background. With
     * RADAR_WRITE_PREVIEW=1 they are also written into the app's resources.
     */
    @Test
    fun picturesEveryWidgetForThePicker() {
        val cases = listOf(
            Triple(BriefWidget(), "brief", Dims(360, 400)),
            Triple(MovesWidget(), "moves", Dims(360, 400)),
            Triple(CompaniesWidget(), "companies", Dims(360, 400)),
            Triple(LicencesWidget(), "licences", Dims(360, 400)),
            Triple(TrendsWidget(), "trends", Dims(360, 280)),
            Triple(DashboardWidget(), "dashboard", Dims(360, 620)),
            Triple(CarouselWidget(), "carousel", Dims(360, 420)),
        )
        cases.forEach { (w, name, d) ->
            val root = CarouselStage.mount(ctx, w.build(ctx, d, 41), d)
            CarouselStage.idle(800)
            val drawn = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(drawn))
            // The launcher clips a widget to its rounded background; drawing in software does not, so round it here.
            val r = 26 * den
            val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            Canvas(bmp).apply {
                clipPath(Path().apply { addRoundRect(0f, 0f, root.width.toFloat(), root.height.toFloat(), r, r, Path.Direction.CW) })
                drawBitmap(drawn, 0f, 0f, null)
            }
            FileOutputStream(File(out, "9-picker-$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (System.getenv("RADAR_WRITE_PREVIEW") == "1") {
                FileOutputStream(File("src/main/res/drawable-nodpi/widget_${name}_preview.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    /** On first install, before any data: every widget says what is coming, and nothing is drawn from nothing. */
    @Test
    fun widgetsBeforeAnyData() {
        File(ctx.filesDir, "radar.json").delete()
        RadarWidget.all().forEach { w ->
            val root = CarouselStage.mount(ctx, w.build(ctx, w.fallback, 43), w.fallback)
            CarouselStage.picture(root, File(out, "0-empty-${w.javaClass.simpleName}.png"))
            root.findViewById<View>(R.id.list)?.takeIf { w !is CarouselWidget }?.let { assertEquals(View.GONE, it.visibility) }
        }
        val carousel = CarouselStage.mount(ctx, Dims(360, 420), 43)
        assertEquals(View.INVISIBLE, carousel.findViewById<View>(R.id.controls).visibility)
        assertEquals(View.VISIBLE, carousel.findViewById<View>(R.id.empty).visibility)
    }

    /**
     * A phone holding a thin copy of the radar (a day with one move, and no companies, regulators or trends
     * yet) still draws every widget.
     */
    @Test
    fun drawsFromAThinRadar() {
        File(ctx.filesDir, "radar.json").writeText(
            """{"updated_at":"2026-10-09T09:00:00+03:00","window_start":"2026-10-03","window_end":"2026-10-09","count":1,
            "headline":"Dubai gave **Rain a full exchange licence**.","events":[{"id":"esma-wind-down","date":"2026-10-08",
            "type":"Regulation","type_label":"Regulation","regulator":"ESMA","title":"ESMA sets three-month wind-down"}]}"""
        )
        RadarWidget.all().forEach { w ->
            val root = CarouselStage.mount(ctx, w.build(ctx, w.fallback, 44), w.fallback)
            CarouselStage.picture(root, File(out, "0-thin-${w.javaClass.simpleName}.png"))
            assertTrue(root.width > 0)
        }
    }
}
