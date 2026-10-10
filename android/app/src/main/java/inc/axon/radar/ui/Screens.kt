@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package inc.axon.radar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import inc.axon.radar.data.Move
import inc.axon.radar.data.Radar
import inc.axon.radar.data.Text
import java.time.LocalDate

enum class Tab(val icon: ImageVector, val label: String) {
    Home(Icons.Home, "Briefing"), Explore(Icons.Globe, "Explore"), Watch(Icons.Watch, "Watching"),
    Trends(Icons.Trend, "Trends"), About(Icons.Person, "Radar"),
}

/** The tab bar: five white line icons on a grey band, black on the deck. */
@Composable
fun TabBar(tab: Tab, dark: Boolean, onTab: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    Row(
        modifier.fillMaxWidth().background(if (dark) ink.night else ink.bar).windowInsetsPadding(WindowInsets.navigationBars).height(58.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { t ->
            Box(Modifier.weight(1f).fillMaxHeight().tap { onTab(t) }, contentAlignment = Alignment.Center) {
                Ico(t.icon, if (t == tab) Palette.White else Palette.White.copy(alpha = 0.5f), 23.dp)
            }
        }
    }
}

@Composable
private fun PaperList(bottomInset: Dp, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize().background(Palette.Paper), contentPadding = PaddingValues(bottom = bottomInset + 28.dp)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        content()
    }
}

@Composable
fun Heading(text: String, n: Int? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 26.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        T(text, Type.display(30f), Palette.Black, Modifier.weight(1f))
        if (n != null) T(n.toString(), Type.Caps, Palette.Black)
    }
}

@Composable
private fun PaperRow(left: String, right: String, sub: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 1.dp).clip(RoundedCornerShape(2.dp)).background(Palette.Row)
            .then(if (onClick != null) Modifier.tap(onClick) else Modifier).padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            T(left, Type.Name, Palette.Black)
            if (sub != null) { Spacer(Modifier.height(2.dp)); T(sub, Type.Small, Palette.Muted) }
        }
        Spacer(Modifier.width(10.dp))
        T(right, Type.Row, Palette.Muted)
    }
}

// ---- Trends ------------------------------------------------------------------------------------

private val ORDER = listOf("License", "Regulation", "Funding", "M&A", "Launch", "Partnership")
private val LABEL = mapOf("License" to "Licences", "Regulation" to "Rules", "Funding" to "Funding", "M&A" to "M&A", "Launch" to "Launches", "Partnership" to "Partnerships")

@Composable
fun TrendsScreen(radar: Radar, bottomInset: Dp) {
    val ink = LocalInk.current
    val t = radar.trends
    PaperList(bottomInset) {
        item { Titles(listOf("Trends"), 0) {} }
        item {
            Column(Modifier.padding(horizontal = 14.dp)) {
                T("${t.total} moves since ${Text.monthYear(t.since)}", Type.display(34f), Palette.Black)
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth()) {
                    listOf(t.licences to "Licences", t.actions to "Rules", t.commercial to "Commercial", t.capital to "Capital").forEachIndexed { i, (n, l) ->
                        if (i > 0) Box(Modifier.width(1.dp).height(58.dp).background(Palette.Rule))
                        Column(Modifier.weight(1f).padding(start = if (i > 0) 10.dp else 0.dp)) {
                            T(n.toString(), Type.display(40f), Palette.Black, maxLines = 1)
                            Spacer(Modifier.height(4.dp))
                            T(l, Type.Small, Palette.Muted, maxLines = 1)
                        }
                    }
                }
            }
        }
        item { Heading("Moves per month") }
        item { MonthChart(radar) }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ORDER.forEach { k ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(if (ink.mono) 11.dp else 9.dp).kind(k, ink))
                        Spacer(Modifier.width(4.dp))
                        T(LABEL[k] ?: k, Type.Small, Palette.Ink2, maxLines = 1)
                    }
                }
            }
        }
        item { Heading("What kind of moves") }
        item {
            val max = ORDER.maxOf { k -> radar.moves.count { it.type == k } }.coerceAtLeast(1)
            Column(Modifier.padding(horizontal = 14.dp)) {
                ORDER.forEach { k ->
                    val n = radar.moves.count { it.type == k }
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        T(LABEL[k] ?: k, Type.Name, Palette.Black, Modifier.width(110.dp))
                        Box(Modifier.weight(1f).height(22.dp)) {
                            Box(Modifier.fillMaxWidth(n / max.toFloat()).fillMaxHeight().kind(k, ink))
                        }
                        T(n.toString(), Type.Name, Palette.Black, Modifier.width(36.dp).padding(start = 8.dp))
                    }
                }
            }
        }
        item { Heading("Where", radar.moves.size) }
        items(listOf("Europe", "Middle East", "North America", "Global")) { r ->
            PaperRow(r, Text.plural(radar.moves.count { it.region == r }, "move", "moves"), "${radar.regions[r] ?: 0} in the last 7 days")
        }
        item { Heading("Most active regulators") }
        items(radar.regulators.take(10)) { r ->
            PaperRow(r.name, r.total.toString(), listOfNotNull(r.where.ifEmpty { null }, Text.plural(r.licences, "licence", "licences"), Text.plural(r.actions, "rule", "rules")).joinToString(" · "))
        }
    }
}

/** Moves per month for the last thirteen months, each bar stacked in the colours of its kinds. */
@Composable
private fun MonthChart(radar: Radar) {
    val months = radar.trends.months.map { it.ym }.ifEmpty {
        val end = LocalDate.now(); (12 downTo 0).map { end.minusMonths(it.toLong()) }.map { "%04d-%02d".format(it.year, it.monthValue) }
    }
    val stacks = months.map { ym -> ORDER.map { k -> radar.moves.count { it.date.startsWith(ym) && it.type == k } } }
    val totals = stacks.map { it.sum() }
    val max = (totals.maxOrNull() ?: 0).coerceAtLeast(3)
    val measurer = rememberTextMeasurer()
    val ink = LocalInk.current
    Canvas(Modifier.fillMaxWidth().height(220.dp).padding(horizontal = 14.dp)) {
        val labelH = 18.dp.toPx()
        val topPad = 18.dp.toPx()
        val base = size.height - labelH
        val slot = size.width / months.size
        val bw = (slot * 0.62f).coerceAtMost(20.dp.toPx())
        drawLine(Palette.Rule, Offset(0f, base), Offset(size.width, base), strokeWidth = 1.dp.toPx())
        months.forEachIndexed { i, ym ->
            val x = slot * i + (slot - bw) / 2
            var y = base
            val last = i == months.lastIndex && radar.trends.partialLast
            stacks[i].forEachIndexed { j, n ->
                if (n > 0) {
                    val h = n / max.toFloat() * (base - topPad)
                    val fill = ink.page(ORDER[j])
                    // In black and white a hairline of paper keeps neighbouring kinds apart.
                    val gap = if (ink.mono && y < base) 1.dp.toPx() else 0f
                    drawRect(fill, Offset(x, y - h), Size(bw, h - gap))
                    if (ink.lined(ORDER[j])) hatch(ink.lines(fill), 3.5.dp.toPx(), 1.dp.toPx(), x, y - h, x + bw, y - gap)
                    y -= h
                }
            }
            if (last && totals[i] > 0) {
                drawRect(Palette.Black, Offset(x, y), Size(bw, base - y), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))))
            }
            if (totals[i] > 0 && (totals[i] == totals.maxOrNull() || i == months.lastIndex)) {
                val r = measurer.measure(totals[i].toString(), Type.Caps.copy(color = Palette.Black))
                drawText(r, topLeft = Offset(x + bw / 2 - r.size.width / 2, y - r.size.height - 3.dp.toPx()))
            }
            val lab = measurer.measure(Text.monthYear(ym).take(1), Type.Small.copy(color = if (i == months.lastIndex) Palette.Black else Palette.Muted))
            drawText(lab, topLeft = Offset(x + bw / 2 - lab.size.width / 2, base + 4.dp.toPx()))
        }
    }
}

// ---- About -------------------------------------------------------------------------------------

@Composable
fun AboutScreen(
    radar: Radar, refreshing: Boolean, onRefresh: () -> Unit, onOpenWeb: () -> Unit,
    look: Look, onLook: (Look) -> Unit, bottomInset: Dp,
) {
    PaperList(bottomInset) {
        item { Titles(listOf("Radar"), 0) {} }
        item {
            Column(Modifier.padding(horizontal = 14.dp)) {
                T(androidx.compose.ui.text.AnnotatedString("Stablecoin and blockchain payments moves in Europe, the Middle East and North America, checked against their sources every morning."), Type.Lead, Palette.Ink2)
            }
        }
        item { Heading("Look") }
        item { LookPicker(radar, look, onLook) }
        item { Heading("Data") }
        item { PaperRow("Updated", Text.updated(radar.updatedAt)) }
        item { PaperRow("Next update", radar.nextRun?.let { Text.updated(it) + ", 06:54 Riyadh" } ?: "Daily") }
        item { PaperRow("Moves tracked", radar.moves.size.toString()) }
        item { PaperRow("Companies", radar.companies.size.toString()) }
        item { PaperRow("Regulators", radar.regulators.size.toString()) }
        item {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 20.dp)) {
                Row(
                    Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(3.dp)).background(Palette.Black).tap(onRefresh).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T(if (refreshing) "Checking for news…" else "Refresh now", Type.Tab, Palette.White, Modifier.weight(1f))
                    Ico(Icons.Refresh, Palette.White, 18.dp)
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(3.dp)).background(Palette.Row).tap(onOpenWeb).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T("Open the full radar on the web", Type.Tab, Palette.Black, Modifier.weight(1f))
                    Ico(Icons.ArrowOut, Palette.Black, 18.dp)
                }
                Spacer(Modifier.height(24.dp))
                T("The app shows public market news only. Company tags for AXON and the radar's watchlist stay in the web radar. Type is Inter Tight, under the SIL Open Font License.", Type.Small, Palette.Muted)
            }
        }
    }
}

/**
 * The two looks side by side, each shown as a small copy of today's deck, so the choice is made by
 * seeing it. The chosen one is framed in black.
 */
@Composable
private fun LookPicker(radar: Radar, look: Look, onLook: (Look) -> Unit) {
    Column(Modifier.padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LookOption(radar, Look.Colour, "Colour", look == Look.Colour, Modifier.weight(1f)) { onLook(Look.Colour) }
            LookOption(radar, Look.Mono, "Black & white", look == Look.Mono, Modifier.weight(1f)) { onLook(Look.Mono) }
        }
        Spacer(Modifier.height(10.dp))
        T("Changes the app and its home-screen widgets. Kept on this phone.", Type.Small, Palette.Muted)
    }
}

@Composable
private fun LookOption(radar: Radar, look: Look, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(3.dp)
    Column(
        modifier.clip(shape).background(Palette.Row)
            .then(if (selected) Modifier.border(2.dp, Palette.Black, shape) else Modifier)
            .selectable(selected, interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton, onClick = onClick),
    ) {
        MiniDeck(radar.deck.take(4), Ink.of(look), Modifier.fillMaxWidth().height(138.dp))
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            T(label, Type.Name, Palette.Black, Modifier.weight(1f), maxLines = 1)
            Box(
                Modifier.size(18.dp).clip(CircleShape)
                    .then(if (selected) Modifier.background(Palette.Black) else Modifier.border(1.5.dp, Palette.Black.copy(alpha = 0.35f), CircleShape)),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Box(Modifier.size(6.dp).clip(CircleShape).background(Palette.White))
            }
        }
    }
}

/** A few cards of the deck in miniature: folded strips with their names, the last one open. */
@Composable
private fun MiniDeck(moves: List<Move>, ink: Ink, modifier: Modifier) {
    BoxWithConstraints(modifier.background(Palette.Black).clipToBounds()) {
        val w = maxWidth * 0.76f
        val full = maxHeight
        val strip = 21.dp
        moves.forEachIndexed { i, m ->
            val bg = ink.page(m.type)
            val shape = RoundedCornerShape(2.dp)
            val last = i == moves.lastIndex
            Column(
                Modifier.offset(x = (maxWidth - w) / 2, y = 14.dp + strip * i).width(w).height(full).clip(shape).background(bg)
                    .then(if (ink.mono) Modifier.border(1.dp, if (ink.dark(bg)) Tones.Edge else Palette.Black, shape) else Modifier)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                T(m.subject, Type.display(15f), ink.display(bg), maxLines = 1)
                if (last) {
                    Spacer(Modifier.height(5.dp))
                    Box(
                        Modifier.fillMaxWidth().height(full).clip(RoundedCornerShape(1.dp)).background(ink.panel(bg))
                            .then(if (ink.lined(m.type)) Modifier.lines(ink.panelLines(bg), gap = 4.dp) else Modifier).padding(6.dp),
                    ) {
                        T(m.figure, Type.display(19f), bg, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Shown once, before the first copy of the radar has arrived. */
@Composable
fun Loading(failed: Boolean, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Palette.Black).padding(20.dp)) {
        Column(Modifier.align(Alignment.CenterStart)) {
            T("Market\nRadar", Type.display(64f), LocalInk.current.brand)
            Spacer(Modifier.height(16.dp))
            T(if (failed) "The radar couldn't be reached. Check the connection and try again." else "Loading the radar…", Type.Lead, Palette.White)
            if (failed) {
                Spacer(Modifier.height(20.dp))
                T("Try again", Type.Tab, Palette.Black, Modifier.clip(RoundedCornerShape(3.dp)).background(Palette.White).tap(onRetry).padding(horizontal = 16.dp, vertical = 14.dp))
            }
        }
    }
}
