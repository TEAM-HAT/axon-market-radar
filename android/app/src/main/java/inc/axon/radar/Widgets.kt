package inc.axon.radar

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import org.json.JSONObject

/** Shared behaviour: draw from the cached brief, then ask for a fresh copy. */
abstract class RadarWidget : AppWidgetProvider() {
    abstract fun build(ctx: Context, brief: JSONObject?): RemoteViews

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val views = build(ctx, Brief.cached(ctx))
        ids.forEach { mgr.updateAppWidget(it, views) }
        Refresh.schedule(ctx)
        Refresh.now(ctx)
    }

    override fun onEnabled(ctx: Context) {
        Refresh.schedule(ctx)
        Refresh.now(ctx)
    }

    companion object {
        fun updateAll(ctx: Context) {
            val brief = Brief.cached(ctx)
            val mgr = AppWidgetManager.getInstance(ctx)
            listOf(BriefWidget(), MovesWidget()).forEach { w ->
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, w.javaClass))
                if (ids.isNotEmpty()) mgr.updateAppWidget(ids, w.build(ctx, brief))
            }
        }

        fun open(ctx: Context, url: String, requestCode: Int): PendingIntent {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return PendingIntent.getActivity(ctx, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}

/** 4 x 2: this week's count, dates and headline. */
class BriefWidget : RadarWidget() {
    override fun build(ctx: Context, brief: JSONObject?): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_brief)
        if (brief == null) {
            v.setTextViewText(R.id.eyebrow, "Market Radar")
            v.setTextViewText(R.id.count, "–")
            v.setTextViewText(R.id.dates, "")
            v.setTextViewText(R.id.headline, "Loading this week’s brief…")
            v.setTextViewText(R.id.updated, "")
        } else {
            val n = brief.optInt("count")
            v.setTextViewText(R.id.eyebrow, "Market Radar · Week ${brief.optInt("week")}")
            v.setTextViewText(R.id.count, n.toString())
            v.setTextViewText(R.id.count_label, if (n == 1) "Move\nthis week" else "Moves\nthis week")
            v.setTextViewText(R.id.dates, Fmt.range(brief.optString("window_start"), brief.optString("window_end")))
            v.setTextViewText(R.id.headline, Fmt.headline(brief.optString("headline")))
            v.setTextViewText(R.id.updated, Fmt.updated(brief.optString("updated_at")))
        }
        v.setOnClickPendingIntent(R.id.tap, open(ctx, Brief.radarUrl(brief), 100))
        return v
    }
}

/** 4 x 4: the brief plus the two latest moves, each opening its source. */
class MovesWidget : RadarWidget() {
    override fun build(ctx: Context, brief: JSONObject?): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_moves)
        val radar = open(ctx, Brief.radarUrl(brief), 200)
        v.setOnClickPendingIntent(R.id.top, radar)
        v.setOnClickPendingIntent(R.id.footer, radar)
        if (brief == null) {
            v.setTextViewText(R.id.eyebrow, "Market Radar")
            v.setTextViewText(R.id.count, "–")
            v.setTextViewText(R.id.mix, "")
            v.setTextViewText(R.id.headline, "Loading this week’s brief…")
            v.setTextViewText(R.id.updated, "")
            v.setViewVisibility(R.id.row1, View.GONE)
            v.setViewVisibility(R.id.divider, View.GONE)
            v.setViewVisibility(R.id.row2, View.GONE)
            return v
        }
        val n = brief.optInt("count")
        v.setTextViewText(R.id.eyebrow, "Market Radar · Week ${brief.optInt("week")} · " +
            Fmt.range(brief.optString("window_start"), brief.optString("window_end")))
        v.setTextViewText(R.id.count, n.toString())
        v.setTextViewText(R.id.count_label, if (n == 1) "Move this week" else "Moves this week")
        v.setTextViewText(R.id.mix, brief.optString("mix_text"))
        v.setTextViewText(R.id.headline, Fmt.headline(brief.optString("headline")))
        v.setTextViewText(R.id.updated, Fmt.updated(brief.optString("updated_at")))

        val moves = brief.optJSONArray("moves")
        val rows = listOf(
            Triple(R.id.row1, R.id.glyph1, Pair(R.id.meta1, R.id.title1)),
            Triple(R.id.row2, R.id.glyph2, Pair(R.id.meta2, R.id.title2))
        )
        rows.forEachIndexed { i, (row, glyph, texts) ->
            val m = moves?.optJSONObject(i)
            if (m == null) {
                v.setViewVisibility(row, View.GONE)
            } else {
                v.setViewVisibility(row, View.VISIBLE)
                val type = m.optString("type")
                v.setImageViewResource(glyph, Fmt.glyph(type))
                v.setTextViewText(texts.first, Fmt.meta(m.optString("type_label", type), m.optString("regulator").takeIf { it != "null" }, m.optString("date")))
                v.setTextViewText(texts.second, m.optString("title"))
                val url = m.optString("source_url").takeIf { it.startsWith("http") } ?: Brief.radarUrl(brief)
                v.setOnClickPendingIntent(row, open(ctx, url, 210 + i))
            }
        }
        v.setViewVisibility(R.id.divider, if ((moves?.length() ?: 0) > 1) View.VISIBLE else View.GONE)
        if ((moves?.length() ?: 0) == 0) {
            v.setViewVisibility(R.id.row1, View.VISIBLE)
            v.setImageViewResource(R.id.glyph1, R.drawable.g_dot)
            v.setTextViewText(R.id.meta1, "")
            v.setTextViewText(R.id.title1, "A quiet week. Nothing new passed the source check.")
            v.setOnClickPendingIntent(R.id.row1, radar)
        }
        return v
    }
}
