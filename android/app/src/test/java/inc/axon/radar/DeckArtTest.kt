package inc.axon.radar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
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
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max

/**
 * The widget's deck against the app's: the app's Deck() rendered on a phone screen, and DeckArt's picture of the
 * same deck laid out for the same screen, compared pixel by pixel. Saves both, side by side, with the
 * differences, in build/deck-art.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeckArtTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val radar = Radar.parse(File("../../radar.json").readText())
    private val out = File("build/deck-art").apply { mkdirs() }
    private val inset = 82f

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

    /** DeckArt's deck and top line, put where the app has them on the same screen. */
    private fun artShot(look: Look, focus: Int, w: Int, h: Int): Bitmap {
        val ctx = rule.activity
        val p = DeckArt.forScreen(3f, 412f, 915f, inset)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF000000.toInt())
        val line = "${Text.dowDay(radar.windowEnd)} · ${Text.plural(radar.count, "move", "moves")} in 7 days"
        c.drawBitmap(DeckArt.topLine(ctx, line, radar.newToday, 412f, p), 0f, 0f, null)
        c.drawBitmap(DeckArt.deck(ctx, radar.deck, focus, Ink.of(look), p), 0f, p.px(DeckArt.TOP).toFloat(), null)
        return bmp
    }

    private fun save(b: Bitmap, name: String) = FileOutputStream(File(out, "$name.png")).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }

    /**
     * Share of pixels that differ by more than [tolerance] in any channel, right of the app's controls (the
     * widget has its own), and a picture of where: app | widget | differences.
     */
    private fun compare(app: Bitmap, art: Bitmap, name: String, tolerance: Int = 48): Float {
        val w = app.width
        val h = app.height
        val from = 44 * 3 // the app's controls take the left 44dp
        val diff = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        var off = 0
        var total = 0
        var worst = 0
        for (y in 0 until h) for (x in 0 until w) {
            val a = app.getPixel(x, y)
            val b = art.getPixel(x, y)
            val d = max(max(abs((a shr 16 and 255) - (b shr 16 and 255)), abs((a shr 8 and 255) - (b shr 8 and 255))), abs((a and 255) - (b and 255)))
            val counted = x >= from
            if (counted) {
                total++
                if (d > tolerance) off++
                worst = max(worst, d)
            }
            val v = if (!counted) 30 else (d * 4).coerceAtMost(255)
            diff.setPixel(x, y, if (counted && d > tolerance) 0xFFFF2D2D.toInt() else (0xFF000000.toInt() or (v shl 16) or (v shl 8) or v))
        }
        val board = Bitmap.createBitmap(w * 3 + 40, h, Bitmap.Config.ARGB_8888)
        Canvas(board).apply {
            drawColor(0xFF1C2033.toInt())
            drawBitmap(app, 0f, 0f, null)
            drawBitmap(art, (w + 20).toFloat(), 0f, null)
            drawBitmap(diff, (2 * w + 40).toFloat(), 0f, Paint())
        }
        save(board, name)
        val share = off.toFloat() / total
        File(out, "report.txt").appendText("%-28s off %.4f%%  worst %d\n".format(name, share * 100, worst))
        return share
    }

    private fun run(look: Look) {
        val focus = mutableStateOf(0)
        rule.setContent {
            CompositionLocalProvider(LocalInk provides Ink.of(look)) {
                Box { key(focus.value) { Deck(radar, focus.value, { _, _ -> }, {}, {}, Dp(inset)) } }
            }
        }
        listOf(0, 1, 4, radar.deck.lastIndex).forEach { f ->
            focus.value = f
            val app = appShot()
            val art = artShot(look, f, app.width, app.height)
            val name = "${look.name.lowercase()}-focus$f"
            save(app, "$name-app")
            save(art, "$name-widget")
            val share = compare(app, art, name)
            assertTrue("$name: ${share * 100}% of the deck differs from the app", share < 0.002f)
        }
    }

    @Test fun matchesTheAppInColour() = run(Look.Colour)
    @Test fun matchesTheAppInBlackAndWhite() = run(Look.Mono)
}
