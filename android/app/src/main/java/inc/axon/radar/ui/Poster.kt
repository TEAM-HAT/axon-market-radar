@file:OptIn(ExperimentalFoundationApi::class)

package inc.axon.radar.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.util.lerp
import inc.axon.radar.data.Company
import inc.axon.radar.data.Move
import inc.axon.radar.data.Radar
import inc.axon.radar.data.Text
import java.time.LocalDate

/** What a move or company page does when tapped. */
class PosterActions(
    val close: () -> Unit,
    val openUrl: (String) -> Unit,
    val openCompany: (String) -> Unit,
    val openMoves: (List<Move>, Int) -> Unit,
    val toggleWatch: (String) -> Unit,
)

/** Swipe sideways between moves; the next page slides over the one before, as in the inspiration. */
@Composable
fun MovePager(moves: List<Move>, start: Int, radar: Radar, watched: Set<String>, actions: PosterActions) {
    val state = rememberPagerState(initialPage = start.coerceIn(0, (moves.size - 1).coerceAtLeast(0))) { moves.size }
    HorizontalPager(state, Modifier.fillMaxSize().background(Palette.Black), beyondBoundsPageCount = 1) { page ->
        val offset = (state.currentPage - page) + state.currentPageOffsetFraction
        Box(Modifier.fillMaxSize().graphicsLayer {
            // The page leaving to the left drifts slower and dims a little, so the new colour seems to slide over it.
            if (offset > 0f) {
                translationX = size.width * offset * 0.65f
                alpha = lerp(1f, 0.55f, offset.coerceIn(0f, 1f))
            }
        }) {
            MovePoster(moves[page], radar, watched, actions)
        }
    }
}

@Composable
fun MovePoster(m: Move, radar: Radar, watched: Set<String>, actions: PosterActions) {
    val bg = typeColor(m.type)
    val company = m.companyId?.let { radar.companyById[it] }
    val involved = listOfNotNull(m.companyName) + m.related.mapNotNull { radar.companyById[it]?.name } + listOfNotNull(m.regulator)
    val where = radar.regulators.firstOrNull { it.name == m.regulator }?.where
    val figure = m.figure
    val caption = when {
        m.amount != null -> if (m.type == "M&A") "Deal value" else "Raised"
        m.regulator != null -> where?.takeIf { it.isNotBlank() } ?: (m.jurisdiction ?: m.region ?: "")
        else -> m.region ?: ""
    }
    Poster(
        bg = bg, title = m.subject, onClose = actions.close,
        meta = { Faces(involved.distinct(), shade(bg, 0.7f), readingColor(shade(bg, 0.7f))); MetaText(listOfNotNull(Text.dayYear(m.date), m.region).joinToString(" · "), bg) },
        panel = { FigurePanel(bg, m.type, m.typeLabel, figure, caption, Text.long(m.date, m.precision)) },
        tile1 = Tile("Read the\nSource", Icons.ArrowOut, shade(bg), readingColor(bg), enabled = m.sourceUrl != null) { m.sourceUrl?.let(actions.openUrl) },
        tile2 = if (company != null) {
            val on = company.id in watched
            Tile(if (on) "Watching\n${company.name}" else "Watch\nCompany", if (on) Icons.Check else Icons.Plus, Palette.Black, Palette.White) { actions.toggleWatch(company.id) }
        } else Tile("More from\n${m.regulator ?: m.typeLabel}", Icons.Grid, Palette.Black, Palette.White) {
            val list = radar.timelineFor(m); actions.openMoves(list, list.indexOf(m).coerceAtLeast(0))
        },
        label = m.typeLabel, lead = m.title,
        timeline = { Timeline(radar.timelineFor(m), m, bg) { list, i -> actions.openMoves(list, i) } },
    ) {
        T(m.summary, Type.Body.copy(fontSize = 17.sp, lineHeight = 25.sp), readingColor(bg))
        Spacer(Modifier.height(22.dp))
        Facts(bg, listOfNotNull(
            company?.let { "Company" to it.name },
            m.related.mapNotNull { radar.companyById[it]?.name }.takeIf { it.isNotEmpty() }?.let { "Also involves" to it.joinToString(", ") },
            m.regulator?.let { "Regulator" to (it + (where?.let { w -> " · $w" } ?: "")) },
            m.jurisdiction?.let { "Jurisdiction" to it },
            m.region?.let { "Region" to it },
            "Date" to Text.long(m.date, m.precision),
            m.sourceName?.let { "Source" to "$it · ${Text.host(m.sourceUrl)}" },
        ))
        Spacer(Modifier.height(22.dp))
        if (m.sourceUrl != null) BigButton("Read the source", Icons.ArrowOut) { actions.openUrl(m.sourceUrl) }
        if (company != null) {
            Spacer(Modifier.height(8.dp))
            BigButton("Open ${company.name}", Icons.ChevronLeft, ghost = true, bg = bg) { actions.openCompany(company.id) }
        }
    }
}

@Composable
fun CompanyPoster(c: Company, radar: Radar, watched: Set<String>, actions: PosterActions) {
    val moves = radar.movesFor(c.id)
    val latest = moves.firstOrNull()
    val bg = latest?.let { typeColor(it.type) } ?: Palette.Slate
    val on = c.id in watched
    Poster(
        bg = bg, title = c.name, onClose = actions.close,
        meta = {
            Faces(listOf(c.name) + c.licences.mapNotNull { it.key ?: it.regulator }.distinct().take(2), shade(bg, 0.7f), readingColor(shade(bg, 0.7f)))
            MetaText(listOf(Text.plural(moves.size, "move", "moves"), Text.plural(c.licences.size, "licence", "licences")).joinToString(" · "), bg)
        },
        panel = {
            FigurePanel(bg, latest?.type, c.segment ?: "Company", Text.initials(c.name),
                listOfNotNull(c.hqCity, c.hqCountry).joinToString(", ").ifEmpty { c.region ?: "" },
                latest?.let { "Latest move · ${Text.dayYear(it.date)}" } ?: "")
        },
        tile1 = Tile("Latest\nMove", Icons.ArrowOut, shade(bg), readingColor(bg), enabled = latest != null) { if (latest != null) actions.openMoves(moves, 0) },
        tile2 = Tile(if (on) "Watching" else "Watch\nCompany", if (on) Icons.Check else Icons.Plus, Palette.Black, Palette.White) { actions.toggleWatch(c.id) },
        label = c.segment ?: "About", lead = c.blurb ?: "",
        timeline = { Timeline(moves, null, bg) { list, i -> actions.openMoves(list, i) } },
    ) {
        Facts(bg, listOfNotNull(
            listOfNotNull(c.hqCity, c.hqCountry).joinToString(", ").takeIf { it.isNotEmpty() }?.let { "Headquarters" to it },
            c.region?.let { "Region" to it },
            c.founded?.let { "Founded" to it.toString() },
            c.status?.let { "Status" to it },
            c.funding?.total?.let { "Total raised" to (Text.money(it) ?: "") },
            c.funding?.let { f ->
                listOfNotNull(f.lastType, Text.money(f.lastAmount), f.lastDate?.let { Text.dayYear(it) }, f.lead?.let { "led by $it" })
                    .takeIf { it.isNotEmpty() }?.let { "Last round" to it.joinToString(", ") }
            },
            c.website?.let { "Website" to Text.host(it) },
        ))
        if (c.licences.isNotEmpty()) {
            Spacer(Modifier.height(26.dp))
            T("Licences", Type.display(30f), Palette.Black)
            Spacer(Modifier.height(10.dp))
            c.licences.forEach { l ->
                Rule(bg)
                Column(Modifier.padding(vertical = 11.dp)) {
                    T(listOfNotNull(l.key ?: l.regulator, l.jurisdiction, l.year?.toString()).joinToString(" · "), Type.Caps, secondaryColor(bg))
                    Spacer(Modifier.height(3.dp))
                    T(l.type ?: "", Type.Row, readingColor(bg))
                }
            }
        }
        if (moves.isNotEmpty()) {
            Spacer(Modifier.height(26.dp))
            T("Moves", Type.display(30f), Palette.Black)
            Spacer(Modifier.height(10.dp))
            moves.forEachIndexed { i, m ->
                Rule(bg)
                Row(Modifier.fillMaxWidth().tap { actions.openMoves(moves, i) }.padding(vertical = 11.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(22.dp).background(visible(typeColor(m.type), bg), RoundedCornerShape(2.dp)).border(1.dp, Palette.Black.copy(alpha = 0.25f), RoundedCornerShape(2.dp)), contentAlignment = Alignment.Center) {
                        Glyph(glyphOf(m.type), Palette.Black, 8.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        T("${m.typeLabel.uppercase()} · ${Text.dayYear(m.date).uppercase()}", Type.Caps, secondaryColor(bg))
                        Spacer(Modifier.height(3.dp))
                        T(m.title, Type.Row, readingColor(bg))
                    }
                }
            }
        }
        if (c.sources.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            T("Profile sources: " + c.sources.joinToString(" · ") { Text.host(it) }, Type.Small, secondaryColor(bg))
        }
    }
}

class Tile(val text: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val bg: Color, val fg: Color, val enabled: Boolean = true, val onClick: () -> Unit)

/**
 * The page itself: a huge name, a row of faces, the picture block with two tiles beside it, a short
 * statement and the timeline strip, all on one flat colour. Details continue below the fold.
 */
@Composable
private fun Poster(
    bg: Color, title: String, onClose: () -> Unit,
    meta: @Composable () -> Unit, panel: @Composable () -> Unit, tile1: Tile, tile2: Tile,
    label: String, lead: String, timeline: @Composable () -> Unit,
    details: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(bg)) {
        val content = maxWidth - 28.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                FitTitle(title, Palette.Black, Modifier.weight(1f), maxSize = 60f, minSize = 30f, maxLines = 2)
                Box(Modifier.padding(top = 6.dp, start = 8.dp).size(36.dp).tap(onClose), contentAlignment = Alignment.TopEnd) {
                    Ico(Icons.ChevronDown, Palette.Black, 26.dp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { meta() }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().height(content * 0.66f)) {
                Box(Modifier.weight(0.68f).fillMaxHeight()) { panel() }
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(0.32f).fillMaxHeight()) {
                    TileView(tile1, Modifier.weight(0.56f))
                    Spacer(Modifier.height(6.dp))
                    TileView(tile2, Modifier.weight(0.44f))
                }
            }
            Spacer(Modifier.height(22.dp))
            Row {
                T(label, Type.Small, secondaryColor(bg), Modifier.weight(0.3f).padding(top = 4.dp))
                T(lead, Type.Lead, readingColor(bg), Modifier.weight(0.7f))
            }
            Spacer(Modifier.height(26.dp))
            timeline()
            Spacer(Modifier.height(30.dp))
            details()
            Spacer(Modifier.height(32.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun MetaText(text: String, bg: Color) {
    Spacer(Modifier.width(10.dp))
    T(text, Type.Small, secondaryColor(bg), maxLines = 1)
}

/** The black block that stands in for the inspiration's photograph: one big figure in the page colour. */
@Composable
fun FigurePanel(bg: Color, type: String?, label: String, figure: String, caption: String, footer: String) {
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(3.dp)).background(Palette.Black).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (type != null) { Glyph(glyphOf(type), bg, 9.dp); Spacer(Modifier.width(7.dp)) }
            T(label.uppercase(), Type.Caps, bg, maxLines = 1)
        }
        Column(Modifier.align(Alignment.BottomStart)) {
            FitTitle(figure, bg, Modifier.fillMaxWidth(), maxSize = 84f, minSize = 30f, maxLines = 1)
            if (caption.isNotBlank()) { Spacer(Modifier.height(4.dp)); T(caption, Type.Tile, Palette.White, maxLines = 1) }
            if (footer.isNotBlank()) { Spacer(Modifier.height(10.dp)); T(footer, Type.Small, Palette.White.copy(alpha = 0.55f), maxLines = 1) }
        }
    }
}

@Composable
private fun TileView(t: Tile, modifier: Modifier) {
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(t.bg)
            .then(if (t.enabled) Modifier.tap(t.onClick) else Modifier).padding(10.dp),
    ) {
        Box(Modifier.size(22.dp).background(t.fg.copy(alpha = 0.16f), RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
            Ico(t.icon, t.fg.copy(alpha = if (t.enabled) 0.85f else 0.35f), 14.dp)
        }
        T(t.text, Type.Tile, t.fg.copy(alpha = if (t.enabled) 1f else 0.45f), Modifier.align(Alignment.BottomStart), maxLines = 3)
    }
}

/**
 * The strip along the bottom of each page: the company's moves month by month over the last year,
 * one small square per move in its own colour, like the inspiration's albums by year.
 */
@Composable
fun Timeline(moves: List<Move>, current: Move?, bg: Color, onOpen: (List<Move>, Int) -> Unit) {
    val end = runCatching { LocalDate.parse((moves.firstOrNull()?.date ?: "").take(10)) }.getOrNull()
        ?.let { maxOf(it, current?.let { c -> runCatching { LocalDate.parse(c.date.take(10)) }.getOrNull() } ?: it) }
        ?: LocalDate.now()
    val months = (12 downTo 0).map { end.minusMonths(it.toLong()) }.map { "%04d-%02d".format(it.year, it.monthValue) }
    val byMonth = moves.withIndex().groupBy { it.value.date.take(7) }
    val curMonth = current?.date?.take(7) ?: months.last()
    val rows = months.maxOf { byMonth[it]?.size ?: 0 }.coerceIn(1, 3)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        months.forEach { ym ->
            val items = byMonth[ym].orEmpty()
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.height((rows * 25).dp), verticalArrangement = Arrangement.Bottom) {
                    items.take(3).reversed().forEach { (i, m) ->
                        val isCur = current != null && m.id == current.id
                        Box(
                            Modifier.padding(top = 3.dp).size(22.dp).clip(RoundedCornerShape(2.dp))
                                .background(if (isCur) Palette.Black else visible(typeColor(m.type), bg))
                                .border(1.dp, Palette.Black.copy(alpha = if (isCur) 1f else 0.28f), RoundedCornerShape(2.dp))
                                .tap { onOpen(moves, i) },
                            contentAlignment = Alignment.Center,
                        ) { Glyph(glyphOf(m.type), if (isCur) bg else Palette.Black, 7.dp) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                val strong = ym == curMonth
                T(Text.monthYear(ym).take(1), Type.Small.copy(fontWeight = if (strong) FontWeight.ExtraBold else FontWeight.Medium),
                    if (strong) readingColor(bg) else secondaryColor(bg))
            }
        }
    }
}

/** A move's colour on a page of the same colour would vanish; darken it there. */
private fun visible(c: Color, bg: Color): Color = if (c == bg) shade(bg, 0.7f) else c

@Composable
private fun Facts(bg: Color, rows: List<Pair<String, String>>) {
    rows.forEach { (k, v) ->
        Rule(bg)
        Row(Modifier.padding(vertical = 10.dp)) {
            T(k, Type.Small, secondaryColor(bg), Modifier.weight(0.3f).padding(top = 2.dp))
            T(v, Type.Row, readingColor(bg), Modifier.weight(0.7f))
        }
    }
    Rule(bg)
}

@Composable
private fun Rule(bg: Color) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(readingColor(bg).copy(alpha = 0.22f)))
}

@Composable
private fun BigButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, ghost: Boolean = false, bg: Color = Palette.Black, onClick: () -> Unit) {
    val fill = if (ghost) shade(bg) else Palette.Black
    val fg = if (ghost) readingColor(bg) else Palette.White
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(3.dp)).background(fill).tap(onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(text, Type.Tab, fg, Modifier.weight(1f), maxLines = 1)
        Ico(icon, fg, 18.dp, if (ghost) Modifier.graphicsLayer { rotationZ = 180f } else Modifier)
    }
}

