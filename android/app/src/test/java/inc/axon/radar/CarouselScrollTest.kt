package inc.axon.radar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.ListView
import androidx.test.core.app.ApplicationProvider
import inc.axon.radar.data.Store
import inc.axon.radar.ui.Link
import inc.axon.radar.ui.Look
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * Scrolls the carousel the way a finger does on the home screen: the list follows the finger, flings on after
 * a flick and rests where it is let go after a slow drag, like any list. Saves a clip of a drag in
 * build/carousel-clip.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class CarouselScrollTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val d = Dims(360, 420)
    private val den get() = ctx.resources.displayMetrics.density

    private fun stage(): Pair<View, ListView> {
        Store.setLook(ctx, Look.Colour)
        File(ctx.filesDir, "radar.json").writeText(File("../../radar.json").readText())
        val root = CarouselStage.mount(ctx, d, 77)
        return root to CarouselStage.list(root)
    }

    /** How far the list has scrolled, in px: the cards above the first one showing, and how much of it is gone. */
    private fun scrolled(list: ListView): Int {
        val first = list.getChildAt(0) ?: return 0
        val heights = (0 until list.firstVisiblePosition).sumOf { p ->
            list.adapter.getView(p, null, list).apply {
                measure(View.MeasureSpec.makeMeasureSpec(list.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            }.measuredHeight
        }
        return heights + list.paddingTop - first.top
    }

    /** Lets [n] frames of animation run, one frame at a time, as the screen would. */
    private fun frames(n: Int) = repeat(n) { CarouselStage.idle(16) }

    // What the list has decided to do. The test machine does not run the list's scrolling animation (on a phone
    // that is Android's own list scrolling), so this reads the list's state rather than waits for it to move.
    private fun field(owner: Class<*>, name: String) = owner.getDeclaredField(name).apply { isAccessible = true }
    private fun touchMode(list: ListView) = field(android.widget.AbsListView::class.java, "mTouchMode").getInt(list)
    private fun mode(name: String) = field(android.widget.AbsListView::class.java, name).getInt(null)

    private fun touch(root: View, action: Int, t0: Long, t: Long, yDp: Float) =
        root.dispatchTouchEvent(MotionEvent.obtain(t0, t, action, 200 * den, yDp * den, 0))

    /** The list moves with the finger, move by move, and stays where it is let go after a slow drag. */
    @Test
    fun followsTheFinger() {
        val (root, list) = stage()
        assertTrue("the list holds the deck", list.adapter.count >= 12)
        val t0 = SystemClock.uptimeMillis()
        touch(root, MotionEvent.ACTION_DOWN, t0, t0, 380f)
        var last = scrolled(list)
        val steps = 30
        val moved = ArrayList<Int>()
        for (k in 1..steps) {
            touch(root, MotionEvent.ACTION_MOVE, t0, t0 + k * 40L, 380f - k * 8f)
            CarouselStage.idle(40)
            val now = scrolled(list)
            moved += now - last
            last = now
        }
        // After the touch slop, every 8dp of finger is 8dp of list.
        val steady = moved.drop(4)
        steady.forEach { assertTrue("moved ${it}px for 24px of finger: $moved", abs(it - (8 * den).toInt()) <= 2) }
        // Hold still a moment, then lift: the list stays put.
        val end = 380f - steps * 8f
        for (k in 1..10) {
            touch(root, MotionEvent.ACTION_MOVE, t0, t0 + steps * 40L + k * 40L, end)
            CarouselStage.idle(40)
        }
        last = scrolled(list)
        touch(root, MotionEvent.ACTION_UP, t0, t0 + steps * 40L + 440L, end)
        assertEquals("let go after holding still, the list rests", mode("TOUCH_MODE_REST"), touchMode(list))
        frames(30)
        val rest = scrolled(list)
        assertTrue("it stays where it was let go: $last then $rest", abs(rest - last) < 2 * den)
        assertTrue("the drag scrolled the list: $rest", rest > 200 * den)
    }

    /** A quick flick sets the list flinging on after the finger lifts, as any list does. */
    @Test
    fun aFlickFlings() {
        val (root, list) = stage()
        val t0 = SystemClock.uptimeMillis()
        touch(root, MotionEvent.ACTION_DOWN, t0, t0, 380f)
        for (k in 1..6) {
            touch(root, MotionEvent.ACTION_MOVE, t0, t0 + k * 10L, 380f - k * 40f)
            CarouselStage.idle(10)
        }
        touch(root, MotionEvent.ACTION_UP, t0, t0 + 70L, 140f)
        assertEquals("the list flings on", mode("TOUCH_MODE_FLING"), touchMode(list))
    }

    /** Frames every 16 ms of a flick down the list and a drag back up, for a clip of how the widget moves. */
    @Test
    fun recordsAClip() {
        val (root, _) = stage()
        val clip = File("build/carousel-clip").apply { deleteRecursively(); mkdirs() }
        var n = 0
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        fun frame() {
            canvas.drawColor(0xFF1C2033.toInt())
            root.draw(canvas)
            FileOutputStream(File(clip, "%03d.png".format(n++))).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        fun gesture(fromDp: Float, toDp: Float, moves: Int, ms: Long) {
            val t0 = SystemClock.uptimeMillis()
            touch(root, MotionEvent.ACTION_DOWN, t0, t0, fromDp)
            for (k in 1..moves) {
                val f = k.toFloat() / moves
                touch(root, MotionEvent.ACTION_MOVE, t0, t0 + k * ms, fromDp + (toDp - fromDp) * (1 - (1 - f) * (1 - f)))
                CarouselStage.idle(ms)
                frame()
            }
            touch(root, MotionEvent.ACTION_UP, t0, t0 + (moves + 1) * ms, toDp)
            repeat(50) { CarouselStage.idle(16); frame() }
        }
        repeat(10) { CarouselStage.idle(16); frame() }
        gesture(380f, 120f, 10, 16)
        gesture(100f, 400f, 24, 40)
    }

    /** The arrows scroll the list a card on and back, through RemoteViews as the launcher applies them. */
    @Test
    fun arrowsScrollACard() {
        val (root, list) = stage()
        // The test machine records where a list is asked to scroll smoothly instead of animating it.
        android.widget.RemoteViews(ctx.packageName, R.layout.widget_carousel).apply { setRelativeScrollPosition(R.id.list, 1) }.reapply(ctx, root)
        assertEquals("down scrolls to the next card", 1, shadowOf(list).smoothScrolledPosition)
        android.widget.RemoteViews(ctx.packageName, R.layout.widget_carousel).apply { setRelativeScrollPosition(R.id.list, -1) }.reapply(ctx, root)
        assertEquals("up scrolls back to the first", 0, shadowOf(list).smoothScrolledPosition)
    }

    /** A tap on any card reads that move in the app. */
    @Test
    fun aTapReadsTheCard() {
        val (_, list) = stage()
        val deck = Store.cachedFast(ctx)!!.deck
        for (i in 0 until list.childCount) {
            val card = list.getChildAt(i).findViewById<View>(R.id.card)
            card.performClick()
            val opened = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity
            assertNotNull("card $i opens the app", opened)
            assertEquals(Link("move", deck[list.firstVisiblePosition + i].id), Link.from(opened))
        }
    }
}
