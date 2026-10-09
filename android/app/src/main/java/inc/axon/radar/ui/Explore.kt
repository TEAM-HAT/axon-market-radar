@file:OptIn(ExperimentalFoundationApi::class)

package inc.axon.radar.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import inc.axon.radar.data.Company
import inc.axon.radar.data.Move
import inc.axon.radar.data.Radar
import inc.axon.radar.data.Text

enum class Section(val title: String) { Moves("Moves"), Companies("Companies"), Licences("Licences") }

/**
 * The directory, after the inspiration's Artists screen: a huge section title with the next one
 * peeking beside it, a carousel, filters with raised counts, and plain rows on light grey.
 */
@Composable
fun Explore(
    radar: Radar, section: Section, onSection: (Section) -> Unit,
    onOpenMoves: (List<Move>, Int) -> Unit, onOpenCompany: (String) -> Unit, bottomInset: Dp,
) {
    val list = rememberLazyListState()
    var kind by rememberSaveable { mutableStateOf(0) }
    var region by rememberSaveable { mutableStateOf(0) }
    var seg by rememberSaveable { mutableStateOf(0) }
    var licKind by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(section) { list.scrollToItem(0) }
    LazyColumn(Modifier.fillMaxSize().background(Palette.Paper), state = list, contentPadding = PaddingValues(bottom = bottomInset + 24.dp)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item { Titles(Section.entries.map { it.title }, section.ordinal) { onSection(Section.entries[it]) } }
        when (section) {
            Section.Moves -> {
                item { MoveCarousel(radar.moves.take(10), onOpenMoves) }
                movesRows(radar.moves, KINDS, kind, { kind = it }, region, { region = it }, onOpenMoves)
            }
            Section.Companies -> {
                item { CompanyCarousel(radar, onOpenCompany) }
                companyRows(radar, seg, { seg = it }, onOpenCompany)
            }
            Section.Licences -> {
                item { RegulatorCarousel(radar, onOpenMoves) }
                val rules = radar.moves.filter { it.type == "License" || it.type == "Regulation" }
                movesRows(rules, KINDS.filter { it.second == null || it.second == "License" || it.second == "Regulation" }, licKind, { licKind = it }, region, { region = it }, onOpenMoves)
            }
        }
    }
}

@Composable
fun Titles(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val scroll = rememberScrollState()
    Row(Modifier.fillMaxWidth().horizontalScroll(scroll).padding(start = 14.dp, end = 40.dp, top = 6.dp, bottom = 10.dp)) {
        // The chosen section leads; the others follow in grey, as "Artists" leads "Songs".
        val order = listOf(selected) + titles.indices.filter { it != selected }
        order.forEach { i ->
            T(titles[i], Type.display(54f), if (i == selected) Palette.Black else Palette.Faint, Modifier.padding(end = 16.dp).tap { onSelect(i) }, maxLines = 1)
        }
    }
}

@Composable
private fun Filters(items: List<Pair<String, Int?>>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Palette.Paper).horizontalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items.forEachIndexed { i, (label, n) ->
            SupLabel(label, n, if (i == selected) Palette.Black else Palette.Faint, Modifier.tap { onSelect(i) })
        }
    }
}

@Composable
private fun SmallFilters(items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Palette.Paper).horizontalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEachIndexed { i, label ->
            val on = i == selected
            T(label.uppercase(), Type.Caps, if (on) Palette.White else Palette.Ink2,
                Modifier.clip(RoundedCornerShape(2.dp)).background(if (on) Palette.Black else Palette.Row).tap { onSelect(i) }.padding(horizontal = 9.dp, vertical = 6.dp))
        }
    }
}

/** A row on light grey: a small square, a bold name, and a muted count on the right. */
@Composable
private fun ListRow(thumb: @Composable () -> Unit, title: String, sub: String?, right: String, onClick: () -> Unit, titleLines: Int = 1) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 1.dp).clip(RoundedCornerShape(2.dp)).background(Palette.Row)
            .tap(onClick).padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        thumb()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            T(title, Type.Name, Palette.Black, maxLines = titleLines)
            if (sub != null) { Spacer(Modifier.height(2.dp)); T(sub, Type.Small, Palette.Muted, maxLines = 1) }
        }
        Spacer(Modifier.width(10.dp))
        T(right, Type.Row, Palette.Muted, maxLines = 1)
    }
}

@Composable
fun MoveThumb(m: Move, size: Dp = 44.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(2.dp)).background(typeColor(m.type)).border(1.dp, Palette.Black.copy(alpha = 0.12f), RoundedCornerShape(2.dp)), contentAlignment = Alignment.Center) {
        Glyph(glyphOf(m.type), Palette.Black, 11.dp)
    }
}

@Composable
private fun MonthHeader(label: String, n: Int) {
    Row(Modifier.fillMaxWidth().background(Palette.Paper).padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 8.dp)) {
        T(label.uppercase(), Type.Caps, Palette.Black, Modifier.weight(1f))
        T(n.toString(), Type.Caps, Palette.Black)
    }
}

// ---- Moves -------------------------------------------------------------------------------------

private val KINDS = listOf("All" to null, "Licences" to "License", "Rules" to "Regulation", "Funding" to "Funding", "M&A" to "M&A", "Launches" to "Launch", "Partnerships" to "Partnership")
private val REGIONS = listOf("All regions", "Europe", "Middle East", "North America", "Global")

@Composable
private fun MoveCarousel(moves: List<Move>, onOpen: (List<Move>, Int) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        itemsIndexed(moves) { i, m ->
            val bg = typeColor(m.type)
            Column(
                Modifier.width(if (i == 0) 150.dp else 118.dp).height(if (i == 0) 200.dp else 150.dp).clip(RoundedCornerShape(3.dp)).background(bg)
                    .tap { onOpen(moves, i) }.padding(10.dp),
            ) {
                FitTitle(m.subject, Palette.Black, Modifier.fillMaxWidth(), maxSize = if (i == 0) 30f else 24f, minSize = 14f, maxLines = 2)
                Spacer(Modifier.weight(1f))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp)).background(Palette.Black).padding(8.dp)) {
                    FitTitle(m.figure, bg, Modifier.fillMaxWidth(), maxSize = if (i == 0) 34f else 26f, minSize = 12f, maxLines = 1)
                }
            }
        }
    }
}

private fun LazyListScope.movesRows(
    base: List<Move>, kinds: List<Pair<String, String?>>, kind: Int, onKind: (Int) -> Unit,
    region: Int, onRegion: (Int) -> Unit, onOpen: (List<Move>, Int) -> Unit,
) {
    stickyHeader {
        Column(Modifier.background(Palette.Paper)) {
            Filters(kinds.map { (label, t) -> label to base.count { t == null || it.type == t } }, kind, onKind)
            SmallFilters(REGIONS, region, onRegion)
        }
    }
    val t = kinds.getOrNull(kind)?.second
    val r = REGIONS[region].takeIf { region > 0 }
    val shown = base.filter { (t == null || it.type == t) && (r == null || it.region == r) }
    if (shown.isEmpty()) item { T("Nothing matches. Try another kind or region.", Type.Row, Palette.Muted, Modifier.padding(16.dp)) }
    shown.groupBy { Text.monthFull(it.date) }.forEach { (month, ms) ->
        item(key = "m-$month") { MonthHeader(month, ms.size) }
        items(ms, key = { it.id }) { m ->
            ListRow({ MoveThumb(m) }, m.title, listOfNotNull(m.typeLabel, m.regulator ?: m.companyName, m.region).joinToString(" · "),
                Text.day(m.date), { onOpen(shown, shown.indexOf(m)) }, titleLines = 2)
        }
    }
}

// ---- Companies ---------------------------------------------------------------------------------

private val SEGMENTS = listOf("Featured" to null, "Issuers" to "Issuer", "Rails" to "Settlement rails", "Ramps" to "On/off-ramp", "Custody" to "Custody & wallets", "Gateways" to "Merchant gateway")

@Composable
private fun CompanyCarousel(radar: Radar, onOpen: (String) -> Unit) {
    val top = radar.companies.sortedByDescending { c -> radar.moves.count { (it.companyId == c.id || c.id in it.related) && it.date >= (radar.windowStart ?: "") } * 100 + c.moves }.take(10)
    LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        itemsIndexed(top) { i, c -> Portrait(c, tall = i == 1, dark = i % 3 != 2) { onOpen(c.id) } }
    }
}

/** A black-and-white tile with the company's initials, standing in for the inspiration's portraits. */
@Composable
private fun Portrait(c: Company, tall: Boolean, dark: Boolean, onClick: () -> Unit) {
    val bg = if (dark) Palette.Black else Color(0xFFF2F2F2)
    val fg = if (dark) Palette.White else Palette.Black
    Box(Modifier.width(if (tall) 128.dp else 112.dp).height(if (tall) 196.dp else 136.dp).clip(RoundedCornerShape(3.dp)).background(bg).tap(onClick).padding(10.dp)) {
        T(Text.initials(c.name), Type.display(if (tall) 76f else 60f), fg, Modifier.align(Alignment.BottomStart), maxLines = 1)
        T(c.name, Type.Small, fg.copy(alpha = 0.7f), Modifier.align(Alignment.TopStart), maxLines = 2)
    }
}

private fun LazyListScope.companyRows(radar: Radar, seg: Int, onSeg: (Int) -> Unit, onOpen: (String) -> Unit) {
    stickyHeader { Filters(SEGMENTS.map { (label, s) -> label to radar.companies.count { s == null || it.segment == s } }, seg, onSeg) }
    val s = SEGMENTS[seg].second
    items(radar.companies.filter { s == null || it.segment == s }, key = { it.id }) { c ->
        val latest = radar.latestMove(c.id)
        ListRow(
            { Mono(c.name, latest?.let { typeColor(it.type) } ?: Palette.Slate, Palette.Black, 44.dp, radius = 2.dp) },
            c.name, listOfNotNull(c.segment, c.hqCountry).joinToString(" · "),
            Text.plural(c.moves, "Move", "Moves"), { onOpen(c.id) },
        )
    }
}

// ---- Licences ----------------------------------------------------------------------------------

@Composable
private fun RegulatorCarousel(radar: Radar, onOpen: (List<Move>, Int) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        itemsIndexed(radar.regulators.take(12)) { i, r ->
            val tall = i == 0
            Column(
                Modifier.width(if (tall) 150.dp else 120.dp).height(if (tall) 170.dp else 132.dp).clip(RoundedCornerShape(3.dp))
                    .background(if (i % 2 == 0) Palette.Black else Color(0xFFF2F2F2))
                    .tap { val ms = radar.movesForRegulator(r.name); if (ms.isNotEmpty()) onOpen(ms, 0) }.padding(10.dp),
            ) {
                val fg = if (i % 2 == 0) Palette.White else Palette.Black
                T(r.total.toString().padStart(2, '0'), Type.display(if (tall) 58f else 44f), fg, maxLines = 1)
                Spacer(Modifier.weight(1f))
                FitTitle(r.name, fg, Modifier.fillMaxWidth(), maxSize = if (tall) 30f else 24f, minSize = 12f, maxLines = 2)
                T(r.where.ifEmpty { "Regulator" }, Type.Small, fg.copy(alpha = 0.6f), maxLines = 1)
            }
        }
    }
}
