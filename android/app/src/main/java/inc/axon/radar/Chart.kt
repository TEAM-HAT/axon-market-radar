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

/**
 * Moves per month as flat bars, drawn for the widgets the way the radar's Trends tab draws them:
 * blue in colour, black in black and white.
 */
object Chart {
    private class Colours(val bar: Int, val ink: Int, val ink3: Int, val line: Int, val paper: Int)
    private val COLOUR = Colours(0xFF061AD3.toInt(), 0xFF101325.toInt(), 0xFF646A80.toInt(), 0xFFE3E6EF.toInt(), 0xFFFFFFFF.toInt())
    private val MONO = Colours(0xFF000000.toInt(), 0xFF000000.toInt(), 0xFF6E6E6E.toInt(), 0xFFE4E4E4.toInt(), 0xFFFFFFFF.toInt())
    private const val INITIALS = "JFMAMJJASOND"

    /**
     * Draws [trends]'s thirteen months at [wDp] x [hDp]. The tallest month and the latest month carry their
     * count; the latest month, while still running, is a dashed outline. Returns null when there is nothing to draw.
     */
    fun months(ctx: Context, trends: JSONObject?, wDp: Int, hDp: Int): Bitmap? {
        val months = trends?.objects("months").orEmpty()
        if (months.isEmpty() || wDp < 40 || hDp < 30) return null
        val partial = trends?.optBoolean("partial_last", false) ?: false
        val k = if (RadarWidget.mono(ctx)) MONO else COLOUR
        val d = ctx.resources.displayMetrics.density
        val w = (wDp * d).toInt()
        val h = (hDp * d).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        val face = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = k.ink3; textSize = 10.5f * d; typeface = face; textAlign = Paint.Align.CENTER; letterSpacing = 0.04f }
        val value = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = k.ink; textSize = 11.5f * d; typeface = face; textAlign = Paint.Align.CENTER }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = k.bar; style = Paint.Style.FILL }
        val paper = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = k.paper; style = Paint.Style.FILL }
        val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = k.bar; style = Paint.Style.STROKE; strokeWidth = 1.5f * d
            pathEffect = DashPathEffect(floatArrayOf(3f * d, 2f * d), 0f)
        }
        val rule = Paint().apply { color = k.line; strokeWidth = max(1f, d) }

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
