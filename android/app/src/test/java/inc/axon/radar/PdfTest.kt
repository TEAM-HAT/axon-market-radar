package inc.axon.radar

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToNode
import inc.axon.radar.data.Radar
import inc.axon.radar.ui.Layout
import inc.axon.radar.ui.Link
import inc.axon.radar.ui.PdfSaver
import inc.axon.radar.ui.PdfSink
import inc.axon.radar.ui.RadarApp
import inc.axon.radar.ui.pdfFileName
import inc.axon.radar.ui.pdfPages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** Every page ends with "Download as PDF", and the PDF is the whole page cut between its rows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val radar = Radar.parse(File("../../radar.json").readText())

    @Test
    fun pageBreaksNeverCutARowThatFits() {
        // Rows of 100 with a heading of 40 every five rows; pages of 380 with 10 above and below.
        val blocks = ArrayList<FloatArray>()
        var y = 0f
        repeat(40) { i ->
            if (i % 5 == 0) { blocks += floatArrayOf(y, y + 40f); y += 40f }
            blocks += floatArrayOf(y, y + 100f); y += 100f
        }
        val pages = pdfPages(y, 380f, 10f, 10f, blocks)
        assertEquals(0f, pages.first().first)
        assertEquals(y, pages.last().second)
        pages.zipWithNext().forEach { (a, b) -> assertEquals(a.second, b.first) }
        pages.forEachIndexed { i, (s, e) ->
            assertTrue("page $i too long", e - s <= 380f - (if (i == 0) 0f else 10f) - 10f + 0.5f)
            blocks.forEach { b -> assertTrue("page $i cuts a row at $e", !(b[0] + 0.5f < e && e < b[1] - 0.5f)) }
        }
        // A block taller than a page is cut where it must be.
        val tall = pdfPages(1000f, 300f, 0f, 0f, listOf(floatArrayOf(0f, 1000f)))
        assertTrue(tall.size >= 3)
    }

    @Test
    fun fileNamesSayWhatAndWhen() {
        assertEquals("Market Radar - Competitors - Rivals - 2026-10-13.pdf", pdfFileName("Competitors - Rivals", LocalDate.of(2026, 10, 13)))
        assertEquals("Market Radar - a-b - 2026-10-13.pdf", pdfFileName("a/b", LocalDate.of(2026, 10, 13)))
    }

    /** Draws each page as a picture, as Android's PDF writer would draw it, and keeps them for review. */
    private class PictureSink(private val tag: String) : PdfSink {
        var pages = 0
        override fun page(width: Int, height: Int, draw: (android.graphics.Canvas) -> Unit) {
            val bmp = android.graphics.Bitmap.createBitmap(width * 2, height * 2, android.graphics.Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(bmp)
            c.scale(2f, 2f)
            draw(c)
            pages++
            val dir = File("build/pdf-previews").apply { mkdirs() }
            java.io.FileOutputStream(File(dir, "$tag-${"%02d".format(pages)}.png")).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        override fun write(out: java.io.OutputStream) { out.write("%PDF-1.4 pictures $pages\n".toByteArray()) }
        override fun close() {}
    }

    private fun pump(ms: Long, cond: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            rule.mainClock.advanceTimeBy(32)
            rule.waitForIdle()
            if (cond()) return true
        }
        return false
    }

    private fun saveFrom(dest: String, name: String, id: String? = null) {
        PdfSaver.testSink = { PictureSink(if (id != null) "$dest-$id" else dest) }
        val link = mutableStateOf<Link?>(Link(dest, id))
        rule.setContent { RadarApp(link, preload = radar, autoRefresh = false, startLayout = Layout.Areas, preloadLens = privateLens()) }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1500)
        val lists = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionInfo)).fetchSemanticsNodes()
        if (id == null && dest != "home" && lists.isNotEmpty()) {
            rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionInfo)).performScrollToNode(hasText("Download as PDF"))
        }
        val node = rule.onAllNodesWithText("Download as PDF").onLast()
        // Clicked through its action: at the bottom of the screen the bar may lie over it.
        if (dest != "home") node.performScrollTo()
        node.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        val dir = File(rule.activity.cacheDir, "pdf")
        // The test clock drives the app's coroutines here, so it is moved on while the PDF is made.
        val made = pump(90_000) { dir.listFiles()?.any { it.name.endsWith(".pdf") } == true && rule.activity.getSharedPreferences("radar.pdf", 0).getString("pending", null) != null }
        if (!made) println("PDFDEBUG $dest toast=" + org.robolectric.shadows.ShadowToast.getTextOfLatestToast() + " files=" + dir.listFiles()?.map { it.name } + " link=" + runCatching { rule.onAllNodesWithText("Preparing the PDF…").fetchSemanticsNodes().size }.getOrNull())
        assertTrue("no PDF for $dest", made)
        val pdf = dir.listFiles()!!.first { it.name.endsWith(".pdf") }
        assertTrue(pdf.name, pdf.name.startsWith("Market Radar - $name - "))
        val head = pdf.readBytes()
        assertTrue("not a PDF", String(head, 0, 5) == "%PDF-")
        val pages = Regex("pictures (\\d+)").find(String(head))?.groupValues?.get(1)?.toInt() ?: 0
        assertTrue("no pages", pages >= 1)
        println("PDFPAGES $dest $pages")
        // The system's save dialog was asked for, with the file name.
        assertTrue("no save dialog for $dest", pump(10_000) { shadowOf(rule.activity).peekNextStartedActivityForResult() != null })
        val asked = shadowOf(rule.activity).nextStartedActivityForResult.intent
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, asked.action)
        assertEquals(pdf.name, asked.getStringExtra(Intent.EXTRA_TITLE))
    }

    @Test fun rivalsSavesAsPdf() = saveFrom("rivals", "Competitors - Rivals")
    @Test fun allMovesSavesAsPdf() = saveFrom("moves", "All moves")
    @Test fun settingsSavesAsPdf() = saveFrom("settings", "AXON - Settings")
    @Test fun compareSavesAsPdf() = saveFrom("compare", "Competitors - Compare")
    @Test fun deckSavesAsPdf() = saveFrom("home", "Today")
    @Test fun companySavesAsPdf() = saveFrom("company", "Ripple", "ripple")
    @Test fun investorSavesAsPdf() = saveFrom("investor", radar.investors.first().name, radar.investors.first().id)
}
