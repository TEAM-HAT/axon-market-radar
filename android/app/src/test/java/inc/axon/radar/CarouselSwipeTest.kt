package inc.axon.radar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import inc.axon.radar.data.Store
import inc.axon.radar.ui.Look
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.io.FileOutputStream
import java.time.Duration

/**
 * Swipes the carousel the way a finger does on the home screen: up for the next card, down for the
 * previous one, one card per swipe, and saves frames of each swipe in build/carousel-swipe.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class CarouselSwipeTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val out = File("build/carousel-swipe").apply { mkdirs() }
    private val d = Dims(360, 420)
    private val den get() = ctx.resources.displayMetrics.density
    private var frame = 0

    private fun stage(): Pair<View, android.widget.StackView> {
        Store.setLook(ctx, Look.Colour)
        File(ctx.filesDir, "radar.json").writeText(File("../../radar.json").readText())
        val brief = JSONObject(File("../../brief.json").readText())
        val root = CarouselStage.mount(ctx, brief, d, 77)
        return root to CarouselStage.stack(root)
    }

    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun shot(root: View, name: String) {
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF1C2033.toInt())
        root.draw(c)
        FileOutputStream(File(out, "%03d-%s.png".format(frame++, name))).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** A finger from [fromDp] to [toDp] down the widget, in [steps] moves 16 ms apart, then let go. */
    private fun swipe(root: View, fromDp: Float, toDp: Float, steps: Int = 12, name: String) {
        val x = 200 * den
        val t0 = SystemClock.uptimeMillis()
        fun ev(action: Int, t: Long, yDp: Float) = MotionEvent.obtain(t0, t, action, x, yDp * den, 0)
        root.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, t0, fromDp))
        for (k in 1..steps) {
            val y = fromDp + (toDp - fromDp) * k / steps
            root.dispatchTouchEvent(ev(MotionEvent.ACTION_MOVE, t0 + k * 16L, y))
            idle(16)
            if (k % 3 == 0) shot(root, "$name-drag")
        }
        root.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, t0 + (steps + 1) * 16L, toDp))
        repeat(8) { idle(50); shot(root, "$name-settle") }
    }

    /** Android outlines a dragged card in the theme's primary colour; the stack's theme makes that clear. */
    @Test
    fun noOutlineOnDraggedCards() {
        val (_, stack) = stage()
        listOf("mResOutColor", "mClickColor").forEach { f ->
            val field = android.widget.StackView::class.java.getDeclaredField(f).apply { isAccessible = true }
            assertEquals(f, 0, (field.get(stack) as Int) ushr 24)
        }
    }

    @Test
    fun swipesOneCardAtATime() {
        val (root, stack) = stage()
        shot(root, "rest")
        assertEquals(0, stack.displayedChild)
        swipe(root, 330f, 150f, name = "up1")
        assertEquals(1, stack.displayedChild)
        swipe(root, 330f, 150f, name = "up2")
        assertEquals(2, stack.displayedChild)
        swipe(root, 150f, 330f, name = "down")
        assertEquals(1, stack.displayedChild)
        // A short nudge springs back without moving.
        swipe(root, 300f, 280f, steps = 4, name = "nudge")
        assertEquals(1, stack.displayedChild)
        // At the newest card a swipe down has nowhere to go.
        swipe(root, 150f, 330f, name = "down2")
        assertEquals(0, stack.displayedChild)
        swipe(root, 150f, 330f, name = "top")
        assertEquals(0, stack.displayedChild)
    }

    /**
     * Frames every 16 ms of two swipes up and one down, for a clip of how the widget moves. The finger
     * carries each card the whole way, so every frame of the move is drawn from touch alone.
     */
    @Test
    fun recordsAClip() {
        val (root, stack) = stage()
        val clip = File("build/carousel-clip").apply { deleteRecursively(); mkdirs() }
        var n = 0
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        fun frame() {
            canvas.drawColor(0xFF1C2033.toInt())
            root.draw(canvas)
            FileOutputStream(File(clip, "%03d.png".format(n++))).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        fun gesture(fromDp: Float, toDp: Float, moves: Int) {
            val x = 200 * den
            val t0 = SystemClock.uptimeMillis()
            root.dispatchTouchEvent(MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, x, fromDp * den, 0))
            for (k in 1..moves) {
                val f = k.toFloat() / moves
                val y = fromDp + (toDp - fromDp) * (1 - (1 - f) * (1 - f))   // eases out, like a flick
                root.dispatchTouchEvent(MotionEvent.obtain(t0, t0 + k * 16L, MotionEvent.ACTION_MOVE, x, y * den, 0))
                idle(16); frame()
            }
            root.dispatchTouchEvent(MotionEvent.obtain(t0, t0 + (moves + 1) * 16L, MotionEvent.ACTION_UP, x, toDp * den, 0))
            repeat(30) { idle(16); frame() }
        }
        repeat(30) { idle(16); frame() }
        gesture(330f, -40f, 18)
        assertEquals(1, stack.displayedChild)
        gesture(330f, -40f, 18)
        assertEquals(2, stack.displayedChild)
        gesture(60f, 430f, 18)
        assertEquals(1, stack.displayedChild)
    }

    /** The arrows step the stack with the same motion, through RemoteViews as the launcher applies them. */
    @Test
    fun arrowsStepTheStack() {
        val (root, stack) = stage()
        android.widget.RemoteViews(ctx.packageName, R.layout.widget_carousel).apply { showNext(R.id.stack) }.reapply(ctx, root)
        repeat(8) { idle(50) }
        assertEquals(1, stack.displayedChild)
        android.widget.RemoteViews(ctx.packageName, R.layout.widget_carousel).apply { showPrevious(R.id.stack) }.reapply(ctx, root)
        repeat(8) { idle(50) }
        assertEquals(0, stack.displayedChild)
        shot(root, "arrows-back")
    }
}
