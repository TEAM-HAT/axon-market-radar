package inc.axon.radar

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import inc.axon.radar.data.Radar
import inc.axon.radar.ui.Link
import inc.axon.radar.ui.RadarApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Renders every screen of the app at phone size from the published radar.json, for review. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val radar = Radar.parse(File("../../radar.json").readText())
    private val out = File("build/app-previews").apply { mkdirs() }

    private fun shot(name: String) {
        rule.mainClock.advanceTimeBy(1200)
        rule.waitForIdle()
        rule.runOnIdle {
            val root = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bmp))
            FileOutputStream(File(out, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun everyScreen() {
        val link = mutableStateOf<Link?>(Link("home"))
        rule.setContent { RadarApp(link, preload = radar, autoRefresh = false) }
        shot("01-home")
        listOf("moves" to "02-moves", "companies" to "03-companies", "licences" to "04-licences", "trends" to "05-trends").forEach { (d, n) ->
            link.value = Link(d); shot(n)
        }
        listOf("License", "Regulation", "Funding", "M&A", "Launch", "Partnership").forEach { t ->
            radar.moves.firstOrNull { it.type == t }?.let { m -> link.value = Link("move", m.id); shot("10-move-" + t.replace("&", "")) }
        }
        listOf("ripple", "tether", "noah").forEach { id -> link.value = Link("company", id); shot("20-company-$id") }
        link.value = Link("watch"); shot("06-watch")
        link.value = Link("about"); shot("07-about")
    }

    @Test
    fun deckInTheMiddle() {
        rule.setContent {
            androidx.compose.foundation.layout.Box {
                inc.axon.radar.ui.Deck(radar, 4, { _, _ -> }, {}, {}, androidx.compose.ui.unit.Dp(82f))
            }
        }
        shot("08-deck-middle")
    }
}

/** The same pages on a very tall screen, to see everything below the fold at once. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h2200dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TallRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val radar = Radar.parse(File("../../radar.json").readText())
    private val out = File("build/app-previews").apply { mkdirs() }

    private fun shot(name: String) {
        rule.mainClock.advanceTimeBy(1200)
        rule.waitForIdle()
        rule.runOnIdle {
            val root = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bmp))
            FileOutputStream(File(out, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun fullPages() {
        val link = mutableStateOf<Link?>(Link("move", radar.deck.first().id))
        rule.setContent { RadarApp(link, preload = radar, autoRefresh = false) }
        shot("30-tall-move")
        link.value = Link("company", "ripple"); shot("31-tall-company")
        link.value = Link("trends"); shot("32-tall-trends")
        link.value = Link("companies"); shot("33-tall-companies")
    }
}
