@file:OptIn(ExperimentalFoundationApi::class)

package inc.axon.radar.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.time.LocalDate

/*
 * Every page ends with a quiet link that saves it as a PDF: the page laid out in full off screen, drawn
 * onto A4 pages (as text and shapes, so it stays sharp and searchable) and cut between rows, never
 * through a line. The file goes where the reader picks in the system's save dialog.
 */

/** Set while a page is laid out for a PDF. It gathers what a page break must not cut. */
class PrintMarks {
    private val blocks = HashMap<Any, FloatArray>()

    /** The slice being drawn; rows of a long list outside it skip their drawing. */
    var top = 0f
    var bottom = Float.MAX_VALUE

    fun keep(key: Any, top: Float, bottom: Float) { if (bottom > top) blocks[key] = floatArrayOf(top, bottom) }
    fun all(): List<FloatArray> = blocks.values.toList()
}

/** Present only in the composition that lays a page out for its PDF. */
val LocalPrint = staticCompositionLocalOf<PrintMarks?> { null }

/** Keeps this element whole on one page of the PDF, when it fits on one. */
fun Modifier.keepWhole(): Modifier = composed {
    val marks = LocalPrint.current
    if (marks == null) Modifier else {
        val key = remember { Any() }
        Modifier.onGloballyPositioned { val r = it.boundsInRoot(); marks.keep(key, r.top, r.bottom) }
    }
}

/** Each side of a PDF page is left empty: 15% of the page's width, in the page's own colour. */
const val PDF_SIDE = 0.15f

/** Where each A4 page of a laid-out page starts and ends: as full as it can be without cutting a block that fits on one page. */
fun pdfPages(height: Float, page: Float, padTop: Float, padBottom: Float, blocks: List<FloatArray>): List<Pair<Float, Float>> {
    val n = kotlin.math.ceil(height).toInt() + 2
    val cut = BooleanArray(n)
    val most = page * 0.55f
    for (b in blocks) {
        if (b[1] - b[0] > most) continue
        val a = maxOf(0, kotlin.math.floor(b[0] + 0.5f).toInt() + 1)
        val z = minOf(n - 1, kotlin.math.ceil(b[1] - 0.5f).toInt() - 1)
        for (y in a..z) cut[y] = true
    }
    val pages = ArrayList<Pair<Float, Float>>()
    var s = 0f
    while (s < height - 1 && pages.size < 2000) {
        val cap = page - (if (pages.isEmpty()) 0f else padTop) - padBottom
        if (s + cap >= height) { pages += s to height; break }
        val lo = kotlin.math.ceil(s + cap * 0.4f).toInt()
        var y = kotlin.math.floor(s + cap).toInt()
        while (y > lo && cut[y]) y--
        if (y <= lo) y = kotlin.math.floor(s + cap).toInt()
        pages += s to y.toFloat()
        s = y.toFloat()
    }
    return pages
}

/** The file name a page is saved under: what it is and the day. */
fun pdfFileName(name: String, day: LocalDate = LocalDate.now()): String =
    ("Market Radar - $name - $day").replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]+"), "-").replace(Regex("\\s+"), " ").trim().take(150) + ".pdf"

/** Where the pages are drawn: Android's PDF writer on the phone; tests draw them as pictures instead. */
interface PdfSink {
    fun page(width: Int, height: Int, draw: (android.graphics.Canvas) -> Unit)
    fun write(out: java.io.OutputStream)
    fun close()
}

internal class AndroidPdf : PdfSink {
    private val doc = PdfDocument()
    private var n = 0
    override fun page(width: Int, height: Int, draw: (android.graphics.Canvas) -> Unit) {
        val p = doc.startPage(PdfDocument.PageInfo.Builder(width, height, ++n).create())
        draw(p.canvas)
        doc.finishPage(p)
    }
    override fun write(out: java.io.OutputStream) = doc.writeTo(out)
    override fun close() = doc.close()
}

/** Saves pages as PDFs for the app: one at a time, through the system's save dialog. */
@Stable
class PdfSaver internal constructor(private val activity: Activity, private val scope: CoroutineScope) {
    var busy by mutableStateOf(false)
        private set
    internal var ink: Ink = Ink.Colour
    internal var pick: ((String) -> Unit)? = null
    /** The writer of each PDF; replaced in tests, where Android's PDF writer does not run. */
    internal var sink: () -> PdfSink = { testSink?.invoke() ?: AndroidPdf() }

    /** Lays [content] out in full at [widthDp] (the screen's width when null), writes the PDF and asks where to keep it. */
    fun save(name: String, bg: Color, widthDp: Int? = null, content: @Composable () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            val file = try { render(name, bg, widthDp, content) } catch (e: CancellationException) { throw e } catch (e: Throwable) { null }
            busy = false
            val p = pick
            if (file == null || p == null) { say("Couldn't make the PDF. Try again."); return@launch }
            prefs().edit().putString(PENDING, file.absolutePath).apply()
            runCatching { p(file.name) }.onFailure { say("Couldn't open the save dialog.") }
        }
    }

    /** The save dialog's answer: copy the PDF there, or drop it when the reader cancelled. */
    internal fun picked(uri: Uri?) {
        val path = prefs().getString(PENDING, null)
        prefs().edit().remove(PENDING).apply()
        val file = path?.let { File(it) }?.takeIf { it.exists() } ?: return
        if (uri == null) { file.delete(); return }
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { activity.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } != null }.getOrDefault(false)
            }
            withContext(Dispatchers.Main) {
                file.delete()
                say(if (ok) "Saved ${file.name}" else "Couldn't save the PDF. Try again.")
            }
        }
    }

    private fun prefs() = activity.getSharedPreferences("radar.pdf", Context.MODE_PRIVATE)
    private fun say(text: String) { runCatching { Toast.makeText(activity, text, Toast.LENGTH_LONG).show() } }

    internal suspend fun render(name: String, bg: Color, widthDp: Int?, content: @Composable () -> Unit): File {
        val decor = activity.window.decorView as ViewGroup
        val density = activity.resources.displayMetrics.density
        val width = widthDp?.let { (it * density).toInt() } ?: decor.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels
        val marks = PrintMarks()
        val look = ink
        // Laid out off screen at full height: a scroll view measures its child without a height limit.
        val holder = ScrollView(activity).apply {
            visibility = View.INVISIBLE
            isVerticalScrollBarEnabled = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        val view = ComposeView(activity).apply {
            id = View.generateViewId()
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                CompositionLocalProvider(LocalInk provides look, LocalPrint provides marks) {
                    Box(Modifier.fillMaxWidth().background(bg)) { content() }
                }
            }
        }
        holder.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        decor.addView(holder, FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT))
        val doc = sink()
        try {
            try {
                var last = -1
                var still = 0
                var frames = 0
                while (still < 3 && frames < 240) {
                    withFrameNanos { }
                    frames++
                    val h = view.height
                    if (h > 0 && h == last) still++ else still = 0
                    last = h
                }
                val height = view.height.toFloat()
                check(height > 0f) { "the page did not lay out" }
                val pageW = 595
                val pageH = 842
                val side = pageW * PDF_SIDE
                val k = (pageW - 2 * side) / width
                val padTop = 18 * density
                val cuts = pdfPages(height, pageH / k, padTop, 14 * density, marks.all())
                cuts.forEachIndexed { i, (s, e) ->
                    doc.page(pageW, pageH) { c ->
                        c.drawColor(bg.toArgb())
                        c.save()
                        c.translate(side, 0f)
                        c.scale(k, k)
                        c.translate(0f, (if (i == 0) 0f else padTop) - s)
                        c.clipRect(0f, s, width.toFloat(), e)
                        marks.top = s
                        marks.bottom = e
                        view.draw(c)
                        c.restore()
                    }
                    yield()
                }
            } finally {
                decor.removeView(holder)
            }
            // Written here on the main thread: a few megabytes at most, while the link reads "Preparing the PDF…".
            val dir = File(activity.cacheDir, "pdf").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, pdfFileName(name))
            file.outputStream().use { doc.write(it) }
            return file
        } finally {
            doc.close()
        }
    }

    companion object {
        private const val PENDING = "pending"
        /** Tests draw the pages as pictures through this. */
        internal var testSink: (() -> PdfSink)? = null
    }
}

/** The app's saver; absent in previews and where no activity hosts the app. */
val LocalPdf = staticCompositionLocalOf<PdfSaver?> { null }

internal fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

/** The quiet link at the end of a page: plain text in the page's own colour, three quarters strong, never underlined. */
@Composable
fun PdfLink(color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val pdf = LocalPdf.current ?: return
    if (LocalPrint.current != null) return
    T(
        if (pdf.busy) "Preparing the PDF…" else "Download as PDF", Type.Row, color.copy(alpha = color.alpha * 0.75f),
        modifier.then(if (pdf.busy) Modifier else Modifier.tap(onClick)).padding(vertical = 6.dp),
    )
}

/**
 * A page that scrolls as a list and ends with the link that saves it as a PDF. The same rows are laid out
 * one after another for the PDF, so the file holds the whole page, not only what is on screen.
 */
@Composable
fun PageList(
    name: String, bg: Color, fg: Color, modifier: Modifier = Modifier, state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp), content: LazyListScope.() -> Unit,
) {
    val pdf = LocalPdf.current
    LazyColumn(modifier, state = state, contentPadding = contentPadding) {
        content()
        if (pdf != null) item(key = "pdf-link") {
            PdfLink(fg, Modifier.padding(start = 14.dp, end = 14.dp, top = 22.dp)) { pdf.save(name, bg) { PrintList(content) } }
        }
    }
}

/** The rows of a list page one after another, each kept whole on a page when it fits. */
@Composable
internal fun PrintList(content: LazyListScope.() -> Unit) {
    val marks = LocalPrint.current
    val rows = remember { PrintScope().apply(content).rows }
    Column(Modifier.fillMaxWidth()) {
        rows.forEachIndexed { i, row ->
            key(i) {
                val at = remember { FloatArray(2) }
                Box(
                    Modifier.fillMaxWidth()
                        .onGloballyPositioned { val r = it.boundsInRoot(); at[0] = r.top; at[1] = r.bottom; marks?.keep(at, r.top, r.bottom) }
                        // Only the rows on the page being drawn draw themselves, so long lists draw quickly.
                        .drawWithContent { if (marks == null || (at[1] >= marks.top && at[0] <= marks.bottom)) drawContent() },
                ) { row() }
            }
        }
        Box(Modifier.padding(bottom = 16.dp))
    }
}

private class PrintScope : LazyListScope {
    val rows = ArrayList<@Composable () -> Unit>()
    override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        rows.add @Composable { PrintItem.content() }
    }
    override fun items(count: Int, key: ((index: Int) -> Any)?, contentType: (index: Int) -> Any?, itemContent: @Composable LazyItemScope.(index: Int) -> Unit) {
        for (i in 0 until count) rows.add @Composable { PrintItem.itemContent(i) }
    }
    override fun stickyHeader(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        rows.add @Composable { PrintItem.content() }
    }
}

private object PrintItem : LazyItemScope {
    override fun Modifier.fillParentMaxSize(fraction: Float): Modifier = fillMaxWidth(fraction)
    override fun Modifier.fillParentMaxWidth(fraction: Float): Modifier = fillMaxWidth(fraction)
    override fun Modifier.fillParentMaxHeight(fraction: Float): Modifier = this
    override fun Modifier.animateItemPlacement(animationSpec: FiniteAnimationSpec<IntOffset>): Modifier = this
}
