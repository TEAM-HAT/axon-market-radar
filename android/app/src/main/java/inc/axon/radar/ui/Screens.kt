@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package inc.axon.radar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
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
    Row(
        modifier.fillMaxWidth().background(if (dark) Palette.Night else Palette.Bar).windowInsetsPadding(WindowInsets.navigationBars).height(58.dp),
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
private fun Heading(text: String, n: Int? = null) {
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

// ---- Watching ----------------------------------------------------------------------------------

@Composable
fun WatchScreen(radar: Radar, watched: Set<String>, onOpenCompany: (String) -> Unit, onOpenMoves: (List<Move>, Int) -> Unit, onExplore: () -> Unit, bottomInset: Dp) {
    val cos = radar.companies.filter { it.id in watched }
    val moves = radar.moves.filter { m -> cos.any { it.id == m.companyId || it.id in m.related } }
    PaperList(bottomInset) {
        item { Titles(listOf("Watching", "Companies"), 0) { if (it == 1) onExplore() } }
        if (cos.isEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    T("Tap Watch on any company page and it lands here, with its latest moves. Your list stays on this phone.", Type.Lead, Palette.Ink2)
                    Spacer(Modifier.height(20.dp))
                    T("Browse companies", Type.Tab, Palette.White, Modifier.clip(RoundedCornerShape(3.dp)).background(Palette.Black).tap(onExplore).padding(horizontal = 16.dp, vertical = 14.dp))
                }
            }
        } else {
            items(cos, key = { "c-" + it.id }) { c ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 1.dp).clip(RoundedCornerShape(2.dp)).background(Palette.Row)
                        .tap { onOpenCompany(c.id) }.padding(horizontal = 8.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Mono(c.name, radar.latestMove(c.id)?.let { typeColor(it.type) } ?: Palette.Slate, Palette.Black, 44.dp, radius = 2.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        T(c.name, Type.Name, Palette.Black)
                        T(listOfNotNull(c.segment, c.hqCountry).joinToString(" · "), Type.Small, Palette.Muted, maxLines = 1)
                    }
                    T(c.lastDate?.let { Text.day(it) } ?: "", Type.Row, Palette.Muted)
                }
            }
            item { Heading("Their moves", moves.size) }
            items(moves, key = { "m-" + it.id }) { m ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 1.dp).clip(RoundedCornerShape(2.dp)).background(Palette.Row)
                        .tap { onOpenMoves(moves, moves.indexOf(m)) }.padding(horizontal = 8.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MoveThumb(m)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        T(m.title, Type.Name, Palette.Black, maxLines = 2)
                        T(listOfNotNull(m.typeLabel, m.companyName).joinToString(" · "), Type.Small, Palette.Muted, maxLines = 1)
                    }
                    Spacer(Modifier.width(10.dp))
                    T(Text.day(m.date), Type.Row, Palette.Muted)
                }
            }
        }
    }
}

// ---- Trends ------------------------------------------------------------------------------------

private val ORDER = listOf("License", "Regulation", "Funding", "M&A", "Launch", "Partnership")
private val LABEL = mapOf("License" to "Licences", "Regulation" to "Rules", "Funding" to "Funding", "M&A" to "M&A", "Launch" to "Launches", "Partnership" to "Partnerships")

@Composable
fun TrendsScreen(radar: Radar, bottomInset: Dp) {
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
                        Box(Modifier.size(9.dp).background(typeColor(k)))
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
                            Box(Modifier.fillMaxWidth(n / max.toFloat()).fillMaxHeight().background(typeColor(k)))
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
                    drawRect(typeColor(ORDER[j]), Offset(x, y - h), Size(bw, h))
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
fun AboutScreen(radar: Radar, refreshing: Boolean, onRefresh: () -> Unit, onOpenWeb: () -> Unit, bottomInset: Dp) {
    PaperList(bottomInset) {
        item { Titles(listOf("Radar"), 0) {} }
        item {
            Column(Modifier.padding(horizontal = 14.dp)) {
                T(androidx.compose.ui.text.AnnotatedString("Stablecoin and blockchain payments moves in Europe, the Middle East and North America, checked against their sources every morning."), Type.Lead, Palette.Ink2)
            }
        }
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

/** Shown once, before the first copy of the radar has arrived. */
@Composable
fun Loading(failed: Boolean, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Palette.Black).padding(20.dp)) {
        Column(Modifier.align(Alignment.CenterStart)) {
            T("Market\nRadar", Type.display(64f), Palette.Yellow)
            Spacer(Modifier.height(16.dp))
            T(if (failed) "The radar couldn't be reached. Check the connection and try again." else "Loading the radar…", Type.Lead, Palette.White)
            if (failed) {
                Spacer(Modifier.height(20.dp))
                T("Try again", Type.Tab, Palette.Black, Modifier.clip(RoundedCornerShape(3.dp)).background(Palette.White).tap(onRetry).padding(horizontal = 16.dp, vertical = 14.dp))
            }
        }
    }
}
