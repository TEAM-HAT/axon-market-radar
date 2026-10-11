package inc.axon.radar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import inc.axon.radar.data.Lens
import inc.axon.radar.data.Mark
import inc.axon.radar.data.Stance

/**
 * The sign for AXON's mark on a move, drawn the same in both looks: a triangle pointing down for a threat,
 * up for an opportunity, a ring for a move to watch and a short bar for no effect.
 */
@Composable
fun MarkSign(mark: String, color: Color, size: Dp = 9.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        when (mark) {
            "threat" -> drawPath(Path().apply { moveTo(0f, w * 0.12f); lineTo(w, w * 0.12f); lineTo(w / 2, w * 0.95f); close() }, color)
            "opportunity" -> drawPath(Path().apply { moveTo(w / 2, w * 0.05f); lineTo(w, w * 0.88f); lineTo(0f, w * 0.88f); close() }, color)
            "watch" -> drawCircle(color, radius = w * 0.38f, center = Offset(w / 2, w / 2), style = Stroke(w * 0.2f))
            else -> drawLine(color, Offset(w * 0.1f, w / 2), Offset(w * 0.9f, w / 2), strokeWidth = w * 0.2f)
        }
    }
}

/** The mark as a small label: sign and word, in [fg] on [fill]. */
@Composable
fun MarkTag(mark: String, fill: Color, fg: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(2.dp)).background(fill).padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MarkSign(mark, fg, 8.dp)
        Spacer(Modifier.width(5.dp))
        T(Lens.markLabel(mark).uppercase(), Type.Caps, fg, maxLines = 1)
    }
}

/**
 * A company's threat to AXON as a short label: black for high, dark grey for medium, an outline for low.
 * On paper only.
 */
@Composable
fun ThreatTag(threat: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(2.dp)
    val (fill, fg, edge) = when (threat) {
        "high" -> Triple(Palette.Black, Palette.White, null)
        "medium" -> Triple(Palette.Bar, Palette.White, null)
        "low" -> Triple(Color.Transparent, Palette.Black, Palette.Black.copy(alpha = 0.55f))
        else -> Triple(Color.Transparent, Palette.Muted, null)
    }
    T(
        when (threat) { "high" -> "HIGH"; "medium" -> "MEDIUM"; "low" -> "LOW"; else -> "NONE" }, Type.Caps, fg,
        modifier.clip(shape).background(fill).then(if (edge != null) Modifier.border(1.dp, edge, shape) else Modifier)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        maxLines = 1,
    )
}

/**
 * AXON's line on a move, for the bottom of a card or a list: the mark's sign and word, then the reason. Colours
 * come from the block it sits on.
 */
@Composable
fun MarkLine(mark: Mark, accent: Color, text: Color, lines: Int = 2, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MarkSign(mark.mark, accent, 8.dp)
            Spacer(Modifier.width(5.dp))
            T("FOR AXON · ${mark.label.uppercase()}", Type.Caps, accent, maxLines = 1)
        }
        Spacer(Modifier.height(3.dp))
        T(mark.why, Type.Small, text, maxLines = lines)
    }
}

/**
 * The private block on a move or company page: AXON's reading in a black panel (paper on black pages), with
 * the facts that go with it.
 */
@Composable
fun AxonPanel(bg: Color, heading: String, sign: String?, lead: String, rows: List<Pair<String, String>>) {
    val ink = LocalInk.current
    val fill = ink.panel(bg)
    val fg = ink.onPanel(bg)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(fill).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (sign != null) { MarkSign(sign, bg, 10.dp); Spacer(Modifier.width(7.dp)) }
            T(heading.uppercase(), Type.Caps, bg, maxLines = 1)
        }
        Spacer(Modifier.height(10.dp))
        T(lead, Type.Lead, fg)
        rows.forEach { (k, v) ->
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(fg.copy(alpha = 0.2f)))
            Spacer(Modifier.height(10.dp))
            Row {
                T(k, Type.Small, fg.copy(alpha = 0.6f), Modifier.weight(0.3f).padding(top = 2.dp))
                T(v, Type.Row, fg, Modifier.weight(0.7f))
            }
        }
    }
}

/** A company's stance in a few words: "Direct competitor · High threat". */
fun stanceLine(s: Stance): String = buildList {
    add(Lens.roleLabel(s.role))
    if (s.threat != "none") add(Lens.threatLabel(s.threat))
}.joinToString(" · ")
