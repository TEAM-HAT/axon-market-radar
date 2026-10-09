@file:OptIn(ExperimentalTextApi::class)

package inc.axon.radar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import inc.axon.radar.data.Text as Fmt

/** Plain text with the colour folded into the style, since the app uses no Material theme. */
@Composable
fun T(
    text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
) {
    BasicText(
        text, modifier, style.copy(color = color, textAlign = align ?: style.textAlign),
        overflow = TextOverflow.Ellipsis, maxLines = maxLines,
    )
}

@Composable
fun T(text: AnnotatedString, style: TextStyle, color: Color, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    BasicText(text, modifier, style.copy(color = color), overflow = TextOverflow.Ellipsis, maxLines = maxLines)
}

/** The sweep's headline with its **key phrases** set heavier. */
fun headline(raw: String, bold: Color): AnnotatedString = buildAnnotatedString {
    Fmt.runs(raw).forEach { (s, b) -> if (b) withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = bold)) { append(s) } else append(s) }
}

/**
 * Huge display type that picks the largest size at which the text fits in [maxLines] without
 * breaking a word, the way the inspiration sets artist names.
 */
@Composable
fun FitTitle(
    text: String, color: Color, modifier: Modifier = Modifier, maxSize: Float = 58f, minSize: Float = 26f, maxLines: Int = 2,
) {
    BoxWithConstraints(modifier) {
        val measurer = rememberTextMeasurer()
        val width = constraints.maxWidth
        val size = remember(text, width, maxSize, maxLines) {
            var s = maxSize
            val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
            while (s > minSize) {
                val style = Type.display(s)
                val widest = words.maxOfOrNull { w -> measurer.measure(w, style, softWrap = false, maxLines = 1).size.width } ?: 0
                val lines = measurer.measure(text, style, constraints = Constraints(maxWidth = width)).lineCount
                if (widest <= width && lines <= maxLines) break
                s -= 2f
            }
            s
        }
        T(text, Type.display(size), color, maxLines = maxLines)
    }
}

/**
 * A shape that names the group of a move: diamond for rules and licences, square for capital, dot for
 * commercial. [hollow] draws only its outline; [halo] rings it in a colour so it stays crisp over lines.
 */
@Composable
fun Glyph(shape: Shape, color: Color, size: Dp = 9.dp, modifier: Modifier = Modifier, hollow: Boolean = false, halo: Color? = null) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val c = Offset(w / 2, w / 2)
        fun draw(col: Color, grow: Float, style: DrawStyle) {
            val k = (w + 2 * grow) / w
            val half = w / 2 * k
            when (shape) {
                Shape.Square -> drawRect(col, topLeft = Offset(c.x - half * 0.8f, c.y - half * 0.8f), size = Size(w * 0.8f * k, w * 0.8f * k), style = style)
                Shape.Dot -> drawCircle(col, radius = half * 0.9f, center = c, style = style)
                Shape.Diamond -> drawPath(Path().apply {
                    moveTo(c.x, c.y - half); lineTo(c.x + half, c.y); lineTo(c.x, c.y + half); lineTo(c.x - half, c.y); close()
                }, col, style = style)
            }
        }
        val stroke = (w * 0.16f).coerceAtLeast(1.dp.toPx())
        if (halo != null) draw(halo, 2.dp.toPx(), Fill)
        if (hollow) draw(color, -stroke / 2, Stroke(stroke)) else draw(color, 0f, Fill)
    }
}

/** A flat block in a kind's colour; in black and white, its tone, with lines for the second kind of a family. */
fun Modifier.kind(type: String?, ink: Ink): Modifier {
    val fill = ink.page(type)
    return background(fill).then(if (ink.lined(type)) lines(ink.lines(fill), 3.5.dp, 1.dp) else Modifier)
}

/** Fine diagonal lines behind the content, for the second kind in each family in black and white. */
fun Modifier.lines(color: Color, gap: Dp = 4.dp, width: Dp = 1.dp): Modifier =
    drawBehind { hatch(color, gap.toPx(), width.toPx()) }

/**
 * A move's small square: its colour (or tone and lines in black and white), a hairline edge and its
 * glyph. Used in lists, on the timeline and in a company's moves.
 */
@Composable
fun Swatch(
    type: String?, fill: Color, size: Dp, glyphSize: Dp, border: Color, modifier: Modifier = Modifier,
    glyphColor: Color? = null, lined: Boolean = LocalInk.current.lined(type),
) {
    val ink = LocalInk.current
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier.size(size).clip(shape).background(fill)
            .then(if (lined) Modifier.lines(ink.lines(fill), gap = if (size > 30.dp) 4.5.dp else 3.5.dp, width = 1.dp) else Modifier)
            .border(1.dp, border, shape),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(glyphOf(type), glyphColor ?: if (ink.mono) ink.reading(fill) else Palette.Black, glyphSize, halo = if (lined) fill else null)
    }
}

/** A square with initials: the radar's stand-in for a photo. */
@Composable
fun Mono(name: String?, bg: Color, fg: Color, size: Dp, modifier: Modifier = Modifier, radius: Dp = 3.dp) {
    Box(modifier.size(size).background(bg, RoundedCornerShape(radius)), contentAlignment = Alignment.Center) {
        val fontSize = with(LocalDensity.current) { (size * 0.36f).toSp() }
        BasicText(Fmt.initials(name), style = Type.Name.copy(color = fg, fontSize = fontSize, lineHeight = fontSize))
    }
}

/** Overlapping initials, like the inspiration's row of listener photos. */
@Composable
fun Faces(names: List<String>, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    val n = names.take(3).size.coerceAtLeast(1)
    Box(modifier.width((26 + 21 * (n - 1)).dp)) {
        names.take(3).forEachIndexed { i, n ->
            Mono(n, bg, fg, 26.dp, Modifier.offset(x = (i * 21).dp), radius = 4.dp)
        }
    }
}

/** A label with its count set small and raised, as in "Featured⁹¹²". */
@Composable
fun SupLabel(label: String, count: Int?, color: Color, modifier: Modifier = Modifier) {
    Row(modifier) {
        T(label, Type.Tab, color)
        if (count != null) T(count.toString(), Type.Sup, color, Modifier.offset(x = 1.dp, y = (-4).dp))
    }
}

fun Modifier.tap(onClick: () -> Unit): Modifier = composed {
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}
