package inc.axon.radar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Inflates both widgets the way a launcher does and saves what they look like. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetRenderTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val brief = JSONObject(File("../../brief.json").readText())
    private val out = File("build/widget-previews").apply { mkdirs() }

    private fun render(views: RemoteViews, wDp: Int, hDp: Int, name: String) {
        val v = views.apply(ctx, FrameLayout(ctx))
        val d = ctx.resources.displayMetrics.density
        val w = (wDp * d).toInt()
        val h = (hDp * d).toInt()
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF1C2033.toInt())
        v.draw(c)
        FileOutputStream(File(out, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun rendersBothWidgets() {
        render(BriefWidget().build(ctx, brief), 360, 172, "brief")
        render(MovesWidget().build(ctx, brief), 360, 360, "moves")
        render(BriefWidget().build(ctx, null), 360, 172, "brief-empty")
        render(MovesWidget().build(ctx, null), 360, 360, "moves-empty")
    }
}
