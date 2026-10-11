package inc.axon.radar

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import inc.axon.radar.data.Lens
import inc.axon.radar.data.Radar
import inc.axon.radar.ui.Layout
import inc.axon.radar.ui.Link
import inc.axon.radar.ui.Look
import inc.axon.radar.ui.RadarApp
import inc.axon.radar.ui.todayDeck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * AXON's private view is never part of this repository. When a clone of the private repository sits beside this
 * one, the five areas are also rendered with it; the pictures stay in the build folder, which git ignores.
 */
internal val PRIVATE_LENS = File("../../../axon-radar-private/lens/axon-lens.json")

internal fun privateLens(): Lens? = PRIVATE_LENS.takeIf { it.exists() }?.let { Lens.parse(it.readText()) }

private fun areasDir(look: Look) = File(if (look == Look.Mono) "build/app-previews-areas-mono" else "build/app-previews-areas").apply { mkdirs() }

/** Renders the five areas at phone size, with and without the private view, in both looks, for review. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AreasRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val radar = Radar.parse(File("../../radar.json").readText())

    private fun shot(name: String, look: Look) {
        rule.mainClock.advanceTimeBy(1200)
        rule.waitForIdle()
        rule.runOnIdle {
            val root = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bmp))
            FileOutputStream(File(areasDir(look), "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private val PAGES = listOf(
        "home" to "50-today", "moves" to "51-all-moves", "rivals" to "52-rivals", "map" to "53-map", "companies" to "54-companies",
        "funding" to "55-funding", "acquisitions" to "56-acquisitions", "partnerships" to "57-partnerships",
        "trends" to "58-trends", "licences" to "59-licences", "watch" to "60-watching", "steps" to "61-steps", "settings" to "62-settings",
    )

    private fun everyArea(look: Look, lens: Lens?, suffix: String) {
        val link = mutableStateOf<Link?>(Link("home"))
        rule.setContent { RadarApp(link, preload = radar, autoRefresh = false, startLook = look, startLayout = Layout.Areas, preloadLens = lens) }
        PAGES.forEach { (d, n) -> link.value = Link(d); shot(n + suffix, look) }
        if (lens != null) {
            // A move AXON marked as a threat or an opportunity, and two companies with AXON's reading.
            val marked = radar.moves.firstOrNull { lens.mark(it.id)?.mark == "threat" } ?: radar.moves.first()
            link.value = Link("move", marked.id); shot("63-move-marked$suffix", look)
            listOf("ripple", "thiqwave").forEach { id -> link.value = Link("company", id); shot("64-company-$id$suffix", look) }
        }
    }

    @Test fun areasWithoutKeyInColour() = everyArea(Look.Colour, null, "-nokey")
    @Test fun areasWithoutKeyInBlackAndWhite() = everyArea(Look.Mono, null, "-nokey")

    @Test fun areasWithPrivateViewInColour() {
        val lens = privateLens(); assumeTrue("no private clone beside this one", lens != null)
        everyArea(Look.Colour, lens, "")
    }

    @Test fun areasWithPrivateViewInBlackAndWhite() {
        val lens = privateLens(); assumeTrue("no private clone beside this one", lens != null)
        everyArea(Look.Mono, lens, "")
    }

    /** The day opens with the marked moves that matter most: threats, then opportunities, then moves to watch. */
    @Test
    fun todayLeadsWithWhatMatters() {
        val lens = privateLens(); assumeTrue(lens != null)
        val deck = todayDeck(radar, lens!!)
        assertEquals(radar.deck.toSet(), deck.toSet())
        val lead = deck.take(3).map { lens.mark(it.id)?.mark }
        assertTrue(lead.all { it != null && it != "none" } || radar.deck.count { (lens.mark(it.id)?.mark ?: "none") != "none" } < 3)
        assertEquals(lead.sortedBy { Lens.weight(it) }, lead)
    }

    /** Without the private view the deck keeps its date order. */
    @Test
    fun todayWithoutKeyKeepsTheDeck() = assertEquals(radar.deck, todayDeck(radar, null))
}

/** The longest pages on a very tall screen, to see everything below the fold at once. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h2400dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TallAreasRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val radar = Radar.parse(File("../../radar.json").readText())

    private fun shot(name: String, look: Look) {
        rule.mainClock.advanceTimeBy(1200)
        rule.waitForIdle()
        rule.runOnIdle {
            val root = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bmp))
            FileOutputStream(File(areasDir(look), "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun tall(look: Look) {
        val lens = privateLens(); assumeTrue(lens != null)
        val link = mutableStateOf<Link?>(Link("rivals"))
        rule.setContent { RadarApp(link, preload = radar, autoRefresh = false, startLook = look, startLayout = Layout.Areas, preloadLens = lens) }
        shot("70-tall-rivals", look)
        listOf("map" to "71-tall-map", "funding" to "72-tall-funding", "steps" to "73-tall-steps", "settings" to "74-tall-settings", "partnerships" to "75-tall-partnerships")
            .forEach { (d, n) -> link.value = Link(d); shot(n, look) }
        link.value = Link("company", "ripple"); shot("76-tall-company-ripple", look)
        link.value = Link("company", "bvnk"); shot("77-tall-company-bvnk", look)
        val marked = radar.moves.first { lens!!.mark(it.id)?.mark == "opportunity" }
        link.value = Link("move", marked.id); shot("78-tall-move-marked", look)
        val round = radar.moves.first { it.type == "Funding" && it.leads.isNotEmpty() && it.investors.isNotEmpty() }
        link.value = Link("move", round.id); shot("79-tall-move-round", look)
    }

    @Test fun tallInColour() = tall(Look.Colour)
    @Test fun tallInBlackAndWhite() = tall(Look.Mono)
}
