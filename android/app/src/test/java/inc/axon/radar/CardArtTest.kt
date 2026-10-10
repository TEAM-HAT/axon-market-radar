package inc.axon.radar

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Dp
import inc.axon.radar.data.Radar
import inc.axon.radar.data.Text
import inc.axon.radar.ui.Deck
import inc.axon.radar.ui.Ink
import inc.axon.radar.ui.LocalInk
import inc.axon.radar.ui.Look
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The widget's type against the app's: the app's deck rendered on a phone screen, and CardArt's top line and
 * card names and figures, set for the same screen, compared pixel by pixel. Writes a report to build/card-art.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardArtTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val radar = Radar.parse(File("../../radar.json").readText())
    private val out = File("build/card-art").apply { mkdirs() }

    private fun appShot(): Bitmap {
        rule.mainClock.advanceTimeBy(1200)
        rule.waitForIdle()
        var bmp: Bitmap? = null
        rule.runOnIdle {
            val root = rule.activity.window.decorView
            bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also { root.draw(Canvas(it)) }
        }
        return bmp!!
    }

    private fun channels(c: Int) = intArrayOf(c shr 16 and 255, c shr 8 and 255, c and 255)

    /** Pixels, and where [only] is set, the only ones to compare. */
    private class Px(val w: Int, val h: Int, val p: IntArray, val only: BooleanArray? = null)
    private fun px(b: Bitmap) = Px(b.width, b.height, IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) })

    /** The worst channel difference between [art], laid at ([x], [y]) on [app], and the app; past [stop], gives up. */
    private fun worst(app: Px, art: Px, x: Int, y: Int, stop: Int = 255): Int {
        var w = 0
        for (j in 0 until art.h) {
            val row = (y + j) * app.w + x
            for (i in 0 until art.w) {
                if (art.only != null && !art.only[j * art.w + i]) continue
                val a = app.p[row + i]
                val b = art.p[j * art.w + i]
                val d = max(max(abs((a shr 16 and 255) - (b shr 16 and 255)), abs((a shr 8 and 255) - (b shr 8 and 255))), abs((a and 255) - (b and 255)))
                if (d > w) { w = d; if (w > stop) return w }
            }
        }
        return w
    }

    /** An alpha mask in [ink] over [ground], as the widget tints it. */
    private fun tint(mask: Bitmap, ink: Int, ground: Int): Bitmap {
        val b = Bitmap.createBitmap(mask.width, mask.height, Bitmap.Config.ARGB_8888)
        val i = channels(ink)
        val g = channels(ground)
        for (y in 0 until mask.height) for (x in 0 until mask.width) {
            val a = (mask.getPixel(x, y) ushr 24) / 255f
            val c = IntArray(3) { k -> (g[k] * (1 - a) + i[k] * a).roundToInt() }
            b.setPixel(x, y, (0xFF shl 24) or (c[0] shl 16) or (c[1] shl 8) or c[2])
        }
        return b
    }

    private fun run(look: Look) {
        val ink = Ink.of(look)
        val focus = mutableStateOf(0)
        rule.setContent {
            CompositionLocalProvider(LocalInk provides ink) {
                Box { key(focus.value) { Deck(radar, focus.value, { _, _ -> }, {}, {}, Dp(82f)) } }
            }
        }
        val ctx = rule.activity
        val report = StringBuilder()
        // The top line, across the screen.
        val app0 = px(appShot())
        val line = "${Text.dowDay(radar.windowEnd)} · ${Text.plural(radar.count, "move", "moves")} in 7 days"
        val topArt = CardArt.topLine(ctx, line, radar.newToday, 412f)
        // The lettering and chip exactly; the mark, which the app scales through a cached picture, on average.
        val topPx = px(topArt)
        val lettering = Px(topPx.w - 132, topPx.h, IntArray((topPx.w - 132) * topPx.h) { topPx.p[(it / (topPx.w - 132)) * topPx.w + 132 + it % (topPx.w - 132)] })
        val top = worst(app0, lettering, 132, 0)
        var markSum = 0L
        for (y in 0 until topPx.h) for (x in 0 until 132) markSum += abs((app0.p[y * app0.w + x] and 255) - (topPx.p[y * topPx.w + x] and 255))
        val markMean = markSum.toFloat() / (132 * topPx.h)
        report.append("mark: mean difference %.2f\n".format(markMean))
        assertTrue("mark differs by $markMean on average", markMean < 1.5f)
        java.io.FileOutputStream(File(out, "top-${look.name.lowercase()}.png")).use { o ->
            val both = Bitmap.createBitmap(topArt.width, topArt.height * 2, Bitmap.Config.ARGB_8888)
            Canvas(both).apply { drawBitmap(Bitmap.createBitmap(app0.p, app0.w, app0.h, Bitmap.Config.ARGB_8888), 0f, 0f, null); drawBitmap(topArt, 0f, topArt.height.toFloat(), null) }
            both.compress(Bitmap.CompressFormat.PNG, 100, o)
        }
        report.append("top line: worst $top\n")
        assertTrue("top line differs by $top", top <= 6)
        // The open card's name and figure, for a few cards. The app's card is 0.68 of the screen, at 198px.
        val cardPx = (412 * 0.68f * 3).roundToInt()
        val small = 412 * 0.68f * 1.36f * 0.075f
        listOf(0, 1, 2, 4, 7).forEach { f ->
            focus.value = f
            val app = px(appShot())
            val m = radar.deck[f]
            val bg = ink.page(m.type)
            val y = 132 + ((14 + small * minOf(f, 3)) * 3).roundToInt()
            val name = px(tint(CardArt.name(ctx, m, cardPx, ink.display(bg).toArgb()), ink.display(bg).toArgb(), bg.toArgb()))
            val nameOff = worst(app, name, 198 + 30, y + 30)
            // The figure sits at the bottom of the black block; find it there, then compare.
            // On a lined block the lines run behind the figure, so only its solid letters are compared.
            val figMask = CardArt.figure(ctx, m, cardPx, bg.toArgb())
            val figTinted = px(tint(figMask, bg.toArgb(), ink.panel(bg).toArgb()))
            val solid = if (ink.lined(m.type)) BooleanArray(figTinted.p.size) { (figMask.getPixel(it % figMask.width, it / figMask.width) ushr 24) >= 250 } else null
            val fig = Px(figTinted.w, figTinted.h, figTinted.p, solid)
            val fx = 198 + 30 + 36
            val figOff = (y + 300 until y + 1143 - fig.h - 60).minOf { fy -> worst(app, fig, fx, fy, stop = 40) }
            report.append("${m.subject} / ${m.figure}: name worst $nameOff, figure worst $figOff\n")
            File(out, "report-${look.name.lowercase()}.txt").writeText(report.toString())
            if (nameOff > 6) {
                java.io.FileOutputStream(File(out, "name-${look.name.lowercase()}-$f.png")).use { o ->
                    val a = Bitmap.createBitmap(app.p, app.w, app.h, Bitmap.Config.ARGB_8888)
                    val both = Bitmap.createBitmap(name.w, name.h * 2, Bitmap.Config.ARGB_8888)
                    Canvas(both).apply {
                        drawBitmap(a, android.graphics.Rect(228, y + 30, 228 + name.w, y + 30 + name.h), android.graphics.Rect(0, 0, name.w, name.h), null)
                        drawBitmap(Bitmap.createBitmap(name.p, name.w, name.h, Bitmap.Config.ARGB_8888), 0f, name.h.toFloat(), null)
                    }
                    both.compress(Bitmap.CompressFormat.PNG, 100, o)
                }
            }
            assertTrue("${m.subject}: name differs by $nameOff", nameOff <= 6)
            assertTrue("${m.figure}: figure differs by $figOff", figOff <= 6)
        }
    }

    @Test fun matchesTheAppInColour() = run(Look.Colour)
    @Test fun matchesTheAppInBlackAndWhite() = run(Look.Mono)
}
