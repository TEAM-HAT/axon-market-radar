package inc.axon.radar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.test.core.app.ApplicationProvider
import inc.axon.radar.data.Radar
import inc.axon.radar.data.Store
import inc.axon.radar.ui.Ink
import inc.axon.radar.ui.Link
import inc.axon.radar.ui.LocalInk
import inc.axon.radar.ui.Look
import inc.axon.radar.ui.RadarApp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

private fun dir(look: Look) = File(if (look == Look.Mono) "build/app-previews-mono" else "build/app-previews").apply { mkdirs() }

/** Renders every screen of the app at phone size from the published radar.json, in both looks, for review. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val radar = Radar.parse(File("../../radar.json").readText())

    private fun shot(name: String, look: Look) {
        rule.mainClock.advanceTimeBy(1200)
        rule.waitForIdle()
        rule.runOnIdle {
            val root = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bmp))
            FileOutputStream(File(dir(look), "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun everyScreen(look: Look) {
        val link = mutableStateOf<Link?>(Link("home"))
        rule.setContent { RadarApp(link, preload = radar, autoRefresh = false, startLook = look) }
        shot("01-home", look)
        listOf("moves" to "02-moves", "companies" to "03-companies", "licences" to "04-licences", "trends" to "05-trends").forEach { (d, n) ->
            link.value = Link(d); shot(n, look)
        }
        listOf("License", "Regulation", "Funding", "M&A", "Launch", "Partnership").forEach { t ->
            radar.moves.firstOrNull { it.type == t }?.let { m -> link.value = Link("move", m.id); shot("10-move-" + t.replace("&", ""), look) }
        }
        listOf("ripple", "tether", "noah").forEach { id -> link.value = Link("company", id); shot("20-company-$id", look) }
        link.value = Link("watch"); shot("06-watch", look)
        link.value = Link("about"); shot("07-about", look)
    }

    @Test fun everyScreenInColour() = everyScreen(Look.Colour)
    @Test fun everyScreenInBlackAndWhite() = everyScreen(Look.Mono)

    private fun deck(look: Look) {
        rule.setContent {
            CompositionLocalProvider(LocalInk provides Ink.of(look)) {
                androidx.compose.foundation.layout.Box {
                    inc.axon.radar.ui.Deck(radar, 4, { _, _ -> }, {}, {}, androidx.compose.ui.unit.Dp(82f))
                }
            }
        }
        shot("08-deck-middle", look)
    }

    /** Watching with a few companies, the empty Watching page, and the picker, searched. */
    private fun watching(look: Look) {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        Store.setWatched(ctx, emptySet())
        val link = mutableStateOf<Link?>(Link("watch"))
        rule.setContent { RadarApp(link, preload = radar, autoRefresh = false, startLook = look) }
        shot("40-watch-empty", look)
        rule.onNodeWithText("Add\ncompanies").performClick()
        shot("41-picker", look)
        // The picker lies over the Watching page, so its rows are the last match.
        listOf("Ripple", "Tether", "Noah", "zerohash", "Schuman Financial").forEach { rule.onAllNodesWithText(it).onLast().performClick() }
        rule.onNodeWithText("Search 40 companies").performTextInput("bank")
        shot("42-picker-search", look)
        link.value = Link("watch")
        shot("43-watch-boxes", look)
        rule.onAllNodesWithText("Ripple", substring = true).onFirst().performClick()
        shot("44-watch-open-company", look)
        Store.setWatched(ctx, emptySet())
    }

    @Test fun watchingInColour() = watching(Look.Colour)
    @Test fun watchingInBlackAndWhite() = watching(Look.Mono)

    @Test fun deckInTheMiddleInColour() = deck(Look.Colour)
    @Test fun deckInTheMiddleInBlackAndWhite() = deck(Look.Mono)

    /** The switch on the Radar tab: tap Black & white, and the choice is kept for the app and the widgets. */
    @Test
    fun switchesToBlackAndWhite() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        Store.setLook(ctx, Look.Colour)
        val link = mutableStateOf<Link?>(Link("about"))
        rule.setContent { RadarApp(link, preload = radar, autoRefresh = false) }
        shot("09-switch-before", Look.Mono)
        rule.onNodeWithText("Black & white").performClick()
        rule.mainClock.advanceTimeBy(200)
        shot("09-switch-during", Look.Mono)
        shot("09-switch-after", Look.Mono)
        assertEquals(Look.Mono, Store.look(ctx))
        rule.onNodeWithText("Colour").performClick()
        rule.mainClock.advanceTimeBy(1200)
        assertEquals(Look.Colour, Store.look(ctx))
    }
}

/** The same pages on a very tall screen, to see everything below the fold at once. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h2200dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TallRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val radar = Radar.parse(File("../../radar.json").readText())

    private fun shot(name: String, look: Look) {
        rule.mainClock.advanceTimeBy(1200)
        rule.waitForIdle()
        rule.runOnIdle {
            val root = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bmp))
            FileOutputStream(File(dir(look), "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun fullPages(look: Look) {
        val link = mutableStateOf<Link?>(Link("move", radar.deck.first().id))
        rule.setContent { RadarApp(link, preload = radar, autoRefresh = false, startLook = look) }
        shot("30-tall-move", look)
        link.value = Link("company", "ripple"); shot("31-tall-company", look)
        link.value = Link("trends"); shot("32-tall-trends", look)
        link.value = Link("companies"); shot("33-tall-companies", look)
        link.value = Link("about"); shot("34-tall-about", look)
    }

    @Test fun fullPagesInColour() = fullPages(Look.Colour)
    @Test fun fullPagesInBlackAndWhite() = fullPages(Look.Mono)
}
