package inc.axon.radar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import inc.axon.radar.data.Move
import inc.axon.radar.data.Text
import inc.axon.radar.ui.Palette
import kotlin.math.roundToInt

/**
 * The app's type for the widgets, as pictures (Letters sets it): the app's top line, and each card's name and
 * figure for the carousel. A card's name and figure come as alpha masks the widget tints, so each costs one
 * byte a pixel.
 */
object CardArt {
    /** The app's top line is 44dp tall. */
    const val TOP = 44f
    private const val MARK_ASPECT = 2f / 1.8660254f
    private val BLACK = Palette.Black.toArgb()
    private val WHITE = Palette.White.toArgb()

    /**
     * The app's top line across a widget [widthDp] wide: the mark, the date and count in small capitals, and the
     * white chip with what is new today. A narrow widget says [shorter] instead, when given, if [line] would not fit.
     */
    fun topLine(ctx: Context, line: String, fresh: Int, widthDp: Float, shorter: String? = null): Bitmap {
        val den = ctx.resources.displayMetrics.density
        fun px(dp: Float) = (dp * den).roundToInt()
        val w = px(widthDp).coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, px(TOP), Bitmap.Config.ARGB_8888)
        bmp.density = ctx.resources.displayMetrics.densityDpi
        val c = Canvas(bmp)
        c.drawColor(BLACK)
        // The app's Row: 18dp in from each side and 14dp down.
        val start = px(18f)
        val rowW = w - 2 * start
        val rowTop = px(14f)
        val markH = px(18f)
        val markW = (18f * MARK_ASPECT * den).roundToInt()
        val chip = if (fresh > 0) Letters.text("+$fresh NEW", Letters.caps(ctx, BLACK), Int.MAX_VALUE, 1) else null
        val chipW = chip?.let { it.width + 2 * px(6f) } ?: 0
        val chipH = chip?.let { it.height + 2 * px(2f) } ?: 0
        val caps = Letters.caps(ctx, Palette.White.copy(alpha = 0.75f).toArgb())
        val room = (rowW - markW - px(10f) - chipW).coerceAtLeast(1)
        val said = if (shorter != null && Letters.intrinsic(line.uppercase(), caps) > room) shorter else line
        val label = Letters.layout(said.uppercase(), caps, room, 1)
        val rowH = maxOf(markH, label.height, chipH)
        ctx.getDrawable(R.drawable.logo_mark)?.mutate()?.let { mark ->
            mark.setTint(WHITE)
            val y = rowTop + Letters.center(rowH, markH)
            mark.setBounds(start, y, start + markW, y + markH)
            mark.draw(c)
        }
        Letters.draw(c, label, start + markW + px(10f), rowTop + Letters.center(rowH, label.height))
        if (chip != null) {
            val x = start + rowW - chipW
            val y = rowTop + Letters.center(rowH, chipH)
            val r = 2f * den
            c.drawRoundRect(x.toFloat(), y.toFloat(), (x + chipW).toFloat(), (y + chipH).toFloat(), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WHITE })
            Letters.draw(c, chip, x + px(6f), y + px(2f))
        }
        return bmp
    }

    /**
     * A card's name as the app sets it on a card [cardPx] wide (inside its 10dp padding): up to 46sp, on two lines.
     * Text is shaded by its colour's lightness, so the mask is drawn in [color], the colour it will be tinted.
     */
    fun name(ctx: Context, m: Move, cardPx: Int, color: Int): Bitmap {
        val inner = cardPx - 2 * px(ctx, 10f)
        return Letters.mask(ctx, Letters.fit(ctx, m.subject, Letters.opaque(color), 46f, 24f, 2, inner))
    }

    /**
     * A card's figure as the app sets it in the black block (12dp in from the card's padding), on one line: up to
     * [maxSp], which is 64sp in the app; the widget's shorter cards take it a little smaller.
     */
    fun figure(ctx: Context, m: Move, cardPx: Int, color: Int, maxSp: Float = 64f): Bitmap {
        val inner = cardPx - 2 * px(ctx, 10f) - 2 * px(ctx, 12f)
        return Letters.mask(ctx, Letters.fit(ctx, m.figure, Letters.opaque(color), maxSp, 26f, 1, inner))
    }

    /** What a card says, for screen readers. */
    fun describe(m: Move): String = "${m.subject}. ${m.typeLabel}, ${m.figure}. ${m.title}. ${Text.dayYear(m.date)}"

    private fun px(ctx: Context, dp: Float) = (dp * ctx.resources.displayMetrics.density).roundToInt()
}
