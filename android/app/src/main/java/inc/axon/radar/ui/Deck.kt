package inc.axon.radar.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.zIndex
import inc.axon.radar.data.Move
import inc.axon.radar.data.Radar
import inc.axon.radar.data.Text
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The opening screen: the latest moves as a deck of flat colour cards on black. Cards above the one in
 * focus fold up into thin strips, the focused card opens fully, and the next cards wait below with
 * their names showing. Drag to flip through; tap the open card to read it.
 */
@Composable
fun Deck(radar: Radar, start: Int, onOpen: (List<Move>, Int) -> Unit, onExplore: () -> Unit, onFocus: (Int) -> Unit, bottomInset: Dp) {
    val moves = radar.deck
    val scope = rememberCoroutineScope()
    val pos = remember { Animatable(start.coerceIn(0, (moves.size - 1).coerceAtLeast(0)).toFloat()) }
    val density = LocalDensity.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    BoxWithConstraints(Modifier.fillMaxSize().background(Palette.Black)) {
        val fullW = maxWidth
        val fullH = maxHeight
        val cardW = fullW * 0.68f
        val cardH = cardW * 1.36f
        val small = cardH * 0.075f
        val medium = cardH * 0.29f
        val unit = with(density) { 96.dp.toPx() }

        fun gap(d: Float): Dp = when {
            d <= -1f -> small
            d < 0f -> lerp(small, cardH, d + 1f)
            d <= 1f -> lerp(cardH, medium, d)
            else -> medium
        }

        fun go(i: Int) {
            val target = i.coerceIn(0, (moves.size - 1).coerceAtLeast(0))
            onFocus(target)
            scope.launch { pos.animateTo(target.toFloat(), spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessLow)) }
        }

        // Where each card's top edge sits for the current position. The folded cards above the open one
        // take room only once there are some, so the first card opens near the top.
        val p = pos.value
        val anchor = top + 58.dp + small * p.coerceIn(0f, 3f)
        val k = floor(p).toInt().coerceIn(0, (moves.size - 1).coerceAtLeast(0))
        val t = p - k
        val ys = FloatArray(moves.size)
        if (moves.isNotEmpty()) {
            ys[k] = anchor.value - t * gap(k - p).value
            for (i in k + 1 until moves.size) ys[i] = ys[i - 1] + gap(i - 1 - p).value
            for (i in k - 1 downTo 0) ys[i] = ys[i + 1] - gap(i - p).value
        }
        val focus = p.roundToInt().coerceIn(0, (moves.size - 1).coerceAtLeast(0))

        Box(
            Modifier.fillMaxSize().pointerInput(moves.size) {
                val tracker = VelocityTracker()
                detectVerticalDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onVerticalDrag = { change, dy ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        scope.launch { pos.snapTo((pos.value - dy / unit).coerceIn(-0.35f, moves.size - 0.65f)) }
                    },
                    onDragEnd = {
                        val v = tracker.calculateVelocity().y
                        go((pos.value - v / unit * 0.22f).roundToInt())
                    },
                )
            },
        ) {
            // Cards slide under the top line rather than over it.
            val shift = (top + 44.dp).value
            Box(Modifier.fillMaxSize().padding(top = (top + 44.dp)).clipToBounds()) {
                moves.forEachIndexed { i, m ->
                    val y = ys[i]
                    if (y < fullH.value + 10 && y + cardH.value > shift - 4) {
                        Card(
                            m, cardW, cardH,
                            Modifier.offset(x = (fullW - cardW) / 2, y = (y - shift).dp).zIndex(i.toFloat())
                                .tap { if (i == focus) onOpen(moves, i) else go(i) },
                        )
                    }
                }

                // One dot per card on the right edge, level with each card's top.
                moves.forEachIndexed { i, m ->
                    val y = (ys[i] + 16f).coerceIn(shift + 6f, fullH.value - bottomInset.value - 24)
                    val on = i == focus
                    Box(
                        Modifier.offset(x = fullW - 22.dp - (if (on) 2.dp else 0.dp), y = (y - shift).dp).zIndex(500f)
                            .size(if (on) 10.dp else 6.dp).clip(CircleShape).background(typeColor(m.type))
                            .then(if (on) Modifier.border(1.5.dp, Palette.White, CircleShape) else Modifier),
                    )
                }
            }
        }

        // Top line: the date and how many moves the deck holds.
        Row(Modifier.padding(top = top + 14.dp, start = 18.dp, end = 18.dp).fillMaxWidth().zIndex(600f), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(18.dp).background(Palette.White, RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
                Ico(Icons.Play, Palette.Black, 11.dp)
            }
            Spacer(Modifier.width(10.dp))
            T("${Text.dowDay(radar.windowEnd)} · ${Text.plural(radar.count, "move", "moves")} in 7 days".uppercase(), Type.Caps, Palette.White.copy(alpha = 0.75f), Modifier.weight(1f), maxLines = 1)
            if (radar.newToday > 0) {
                T("+${radar.newToday} NEW", Type.Caps, Palette.Black, Modifier.background(Palette.White, RoundedCornerShape(2.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }

        // Left edge controls, as in the inspiration: up, down and a grid that opens the directory.
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = bottomInset + 18.dp).zIndex(600f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(34.dp).tap { go(focus - 1) }, contentAlignment = Alignment.Center) { Ico(Icons.ChevronUp, Palette.White.copy(alpha = 0.8f), 18.dp) }
            Box(Modifier.size(34.dp).tap(onExplore), contentAlignment = Alignment.Center) { Ico(Icons.Grid, Palette.White.copy(alpha = 0.8f), 18.dp) }
            Box(Modifier.size(34.dp).tap { go(focus + 1) }, contentAlignment = Alignment.Center) { Ico(Icons.ChevronDown, Palette.White.copy(alpha = 0.8f), 18.dp) }
        }
        Box(Modifier.align(Alignment.CenterStart).padding(start = 10.dp).size(34.dp).zIndex(600f).tap { go(focus - 1) }, contentAlignment = Alignment.Center) {
            Ico(Icons.ChevronLeft, Palette.White.copy(alpha = 0.55f), 20.dp)
        }
    }
}

/** One card: the name huge in black on the move's colour, then the black figure block. */
@Composable
private fun Card(m: Move, w: Dp, h: Dp, modifier: Modifier) {
    val bg = typeColor(m.type)
    Column(modifier.size(w, h).clip(RoundedCornerShape(3.dp)).background(bg).padding(10.dp)) {
        FitTitle(m.subject, Palette.Black, Modifier.fillMaxWidth(), maxSize = 46f, minSize = 24f, maxLines = 2)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(2.dp)).background(Palette.Black).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(glyphOf(m.type), bg, 8.dp)
                Spacer(Modifier.width(6.dp))
                T(listOfNotNull(m.typeLabel, m.regulator?.takeIf { Text.money(m.amount) != null }).joinToString(" · ").uppercase(), Type.Caps, bg, maxLines = 1)
            }
            Column(Modifier.align(Alignment.BottomStart)) {
                FitTitle(m.figure, bg, Modifier.fillMaxWidth(), maxSize = 64f, minSize = 26f, maxLines = 1)
                Spacer(Modifier.height(8.dp))
                T(m.title, Type.Small.copy(fontSize = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp)), Palette.White, maxLines = 3)
                Spacer(Modifier.height(6.dp))
                T(Text.dayYear(m.date).uppercase(), Type.Caps, Palette.White.copy(alpha = 0.5f), maxLines = 1)
            }
        }
    }
}
