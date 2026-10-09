package inc.axon.radar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

/** Moves per month as flat blue bars, drawn for the widgets the way the radar's Trends tab draws them. */
object Chart {
    private const val BLUE = 0xFF2852EA.toInt()
    private const val INK = 0xFF101325.toInt()
    private const val INK_3 = 0xFF646A80.toInt()
    private const val LINE = 0xFFE3E6EF.toInt()
    private const val PAPER = 0xFFFFFFFF.toInt()
    private const val INITIALS = "JFMAMJJASOND"

    /**
     * Draws [trends]'s thirteen months at [wDp] x [hDp]. The tallest month and the latest month carry their
     * count; the latest month, while still running, is a dashed outline. Returns null when there is nothing to draw.
     */
    fun months(ctx: Context, trends: JSONObject?, wDp: Int, hDp: Int): Bitmap? {
        val months = trends?.objects("months").orEmpty()
        if (months.isEmpty() || wDp < 40 || hDp < 30) return null
        val partial = trends?.optBoolean("partial_last", false) ?: false
        val d = ctx.resources.displayMetrics.density
        val w = (wDp * d).toInt()
        val h = (hDp * d).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        val face = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK_3; textSize = 10.5f * d; typeface = face; textAlign = Paint.Align.CENTER; letterSpacing = 0.04f }
        val value = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; textSize = 11.5f * d; typeface = face; textAlign = Paint.Align.CENTER }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BLUE; style = Paint.Style.FILL }
        val paper = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PAPER; style = Paint.Style.FILL }
        val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BLUE; style = Paint.Style.STROKE; strokeWidth = 1.5f * d
            pathEffect = DashPathEffect(floatArrayOf(3f * d, 2f * d), 0f)
        }
        val rule = Paint().apply { color = LINE; strokeWidth = max(1f, d) }

        val labelH = 16f * d
        val top = 16f * d
        val base = h - labelH
        val plotH = base - top
        val slot = w.toFloat() / months.size
        val barW = min(16f * d, slot * 0.6f)
        val counts = months.map { max(0, it.optInt("n")) }
        val peak = max(3, counts.maxOrNull() ?: 0)
        val peakAt = counts.indexOf(counts.maxOrNull() ?: 0)

        c.drawLine(0f, base, w.toFloat(), base, rule)
        months.forEachIndexed { i, m ->
            val n = counts[i]
            val cx = slot * i + slot / 2
            val x0 = cx - barW / 2
            val last = i == months.size - 1
            if (n > 0) {
                val bh = n.toFloat() / peak * plotH
                val y0 = base - bh
                val r = min(3f * d, bh)
                val radii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
                if (last && partial) {
                    val s = dash.strokeWidth / 2
                    val p = Path().apply { addRoundRect(RectF(x0 + s, y0 + s, x0 + barW - s, base), radii, Path.Direction.CW) }
                    c.drawPath(p, paper)
                    c.drawPath(p, dash)
                } else {
                    c.drawPath(Path().apply { addRoundRect(RectF(x0, y0, x0 + barW, base), radii, Path.Direction.CW) }, fill)
                }
                if (i == peakAt || last) c.drawText(n.toString(), cx, y0 - 4f * d, value)
            }
            val mi = m.optString("m").substringAfter('-').toIntOrNull()?.minus(1)?.coerceIn(0, 11)
            if (mi != null) c.drawText(INITIALS[mi].toString(), cx, h - 3.5f * d, axis)
        }
        return bmp
    }
}
