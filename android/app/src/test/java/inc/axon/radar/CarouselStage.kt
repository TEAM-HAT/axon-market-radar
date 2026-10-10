package inc.axon.radar

import android.app.Activity
import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.RemoteViews
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.io.FileOutputStream
import java.time.Duration

/**
 * A widget as a launcher holds it, for tests: the provider's views applied the way a launcher applies them,
 * in a window at the widget's size. From Android 12 a widget's list comes inside its views, so lists are
 * filled as on a phone.
 */
object CarouselStage {
    /** Android fills a widget's list only inside a widget host, as a launcher has. */
    fun build(ctx: Context, d: Dims, id: Int): View = host(ctx, CarouselWidget().build(ctx, d, id))

    fun host(ctx: Context, views: RemoteViews): View = views.apply(ctx, AppWidgetHostView(ctx))

    fun list(root: View): ListView = root.findViewById(R.id.list)

    /** The carousel in an activity window at the widget's size, so the list lays out and scrolls as on a phone. */
    fun mount(ctx: Context, d: Dims, id: Int): View = mount(ctx, CarouselWidget().build(ctx, d, id), d)

    /** Any widget's views, mounted the same way. */
    fun mount(ctx: Context, views: RemoteViews, d: Dims): View {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = host(ctx, views)
        val den = ctx.resources.displayMetrics.density
        val holder = FrameLayout(activity)
        holder.addView(root, FrameLayout.LayoutParams((d.w * den).toInt(), (d.h * den).toInt()))
        activity.setContentView(holder)
        idle(200)
        return root
    }

    fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    /** What a mounted widget looks like, on the dark blue of a home screen, saved as [file]. */
    fun picture(root: View, file: File, backdrop: Int = 0xFF1C2033.toInt()): Bitmap {
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(backdrop)
        root.draw(c)
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bmp
    }
}
