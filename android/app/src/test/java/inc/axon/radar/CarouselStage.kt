package inc.axon.radar

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.StackView
import org.json.JSONObject
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/**
 * The carousel as a launcher holds it, for tests: the provider's frame applied the way a launcher applies
 * it, and the card stack filled from the factory (a launcher would bind CarouselService for that).
 */
object CarouselStage {
    fun build(ctx: Context, brief: JSONObject?, d: Dims, id: Int): View {
        val frame = CarouselWidget().buildFor(ctx, brief, d, id)
        val root = runCatching { frame.apply(ctx, FrameLayout(ctx)) }.getOrElse {
            android.view.LayoutInflater.from(ctx).inflate(R.layout.widget_carousel, FrameLayout(ctx), false)
        }
        val factory = CarouselFactory(ctx, id, d).apply { onCreate() }
        stack(root).adapter = object : BaseAdapter() {
            override fun getCount() = factory.count
            override fun getItem(p: Int) = p
            override fun getItemId(p: Int) = factory.getItemId(p)
            override fun getView(p: Int, convert: View?, parent: ViewGroup): View = factory.getViewAt(p).apply(ctx, parent)
        }
        return root
    }

    fun stack(root: View): StackView = root.findViewById(R.id.stack)

    /** The same, in an activity window at the widget's size, so the stack lays out and animates as on a phone. */
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
