package inc.axon.radar

import android.app.Activity
import android.appwidget.AppWidgetHostView
import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.ListView
import org.json.JSONObject
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/**
 * The carousel as a launcher holds it, for tests: the provider's views applied the way a launcher applies
 * them. From Android 12 the list's cards come inside them, so the list is filled as on a phone.
 */
object CarouselStage {
    /** Android fills a widget's list only inside a widget host, as a launcher has. */
    fun build(ctx: Context, brief: JSONObject?, d: Dims, id: Int): View =
        CarouselWidget().buildFor(ctx, brief, d, id).apply(ctx, AppWidgetHostView(ctx))

    fun list(root: View): ListView = root.findViewById(R.id.list)

    /** The same, in an activity window at the widget's size, so the list lays out and scrolls as on a phone. */
    fun mount(ctx: Context, brief: JSONObject?, d: Dims, id: Int): View {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = build(ctx, brief, d, id)
        val den = ctx.resources.displayMetrics.density
        val holder = FrameLayout(activity)
        holder.addView(root, FrameLayout.LayoutParams((d.w * den).toInt(), (d.h * den).toInt()))
        activity.setContentView(holder)
        idle(200)
        return root
    }

    fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
}
