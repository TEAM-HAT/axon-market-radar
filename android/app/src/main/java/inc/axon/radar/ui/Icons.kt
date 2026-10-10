package inc.axon.radar.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Thin line icons drawn on a 24 x 24 grid, in the spirit of the inspiration's tab bar. */
object Icons {
    private fun icon(name: String, vararg strokes: String, fills: List<String> = emptyList(), width: Float = 1.7f): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        strokes.forEach {
            b.addPath(addPathNodes(it), stroke = SolidColor(Color.Black), strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }
        fills.forEach { b.addPath(addPathNodes(it), fill = SolidColor(Color.Black)) }
        return b.build()
    }

    val Home = icon("home", "M4 10.5L12 4l8 6.5V20H4z", "M9.5 20v-5.5h5V20")
    val Globe = icon("globe", "M12 3.5a8.5 8.5 0 1 0 0 17a8.5 8.5 0 1 0 0-17z", "M3.5 12h17",
        "M12 3.5c2.6 2.4 3.6 5.3 3.6 8.5s-1 6.1-3.6 8.5c-2.6-2.4-3.6-5.3-3.6-8.5s1-6.1 3.6-8.5z")
    val Watch = icon("watch", "M5 4.5h14v15H5z", "M12 8.5v7M8.5 12h7")
    val Trend = icon("trend", "M4.5 5h15v14h-15z", fills = listOf("M10 9.2v5.6l4.8-2.8z"))
    val Person = icon("person", "M12 4.5a3.6 3.6 0 1 0 0 7.2a3.6 3.6 0 1 0 0-7.2z", "M5 20c.8-3.6 3.6-5.5 7-5.5s6.2 1.9 7 5.5")
    val ChevronDown = icon("down", "M6 9.5l6 6 6-6", width = 2f)
    val ChevronUp = icon("up", "M6 14.5l6-6 6 6", width = 2f)
    val ChevronLeft = icon("left", "M14.5 6l-6 6 6 6", width = 2f)
    val ArrowOut = icon("out", "M7 17L17 7M9 7h8v8", width = 2f)
    val Plus = icon("plus", "M12 6v12M6 12h12", width = 2.2f)
    val Check = icon("check", "M5.5 12.5l4.2 4.2L18.5 8", width = 2.2f)
    val Grid = icon("grid", "M5 5h5.5v5.5H5zM13.5 5H19v5.5h-5.5zM5 13.5h5.5V19H5zM13.5 13.5H19V19h-5.5z", width = 1.6f)
    val Refresh = icon("refresh", "M19 12a7 7 0 1 1-2.05-4.95", "M19 4.5V8h-3.5", width = 1.9f)
    val Search = icon("search", "M10.5 4.5a6 6 0 1 0 0 12a6 6 0 1 0 0-12z", "M15 15l4.5 4.5", width = 2f)
    val Close = icon("close", "M6.5 6.5l11 11M17.5 6.5l-11 11", width = 2.2f)
}

@Composable
fun Ico(v: ImageVector, tint: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Image(v, contentDescription = null, colorFilter = ColorFilter.tint(tint), modifier = modifier.size(size))
}
