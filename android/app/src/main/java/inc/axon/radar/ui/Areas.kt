@file:OptIn(ExperimentalFoundationApi::class)

package inc.axon.radar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import inc.axon.radar.data.Assessed
import inc.axon.radar.data.Company
import inc.axon.radar.data.Grid
import inc.axon.radar.data.Investors
import inc.axon.radar.data.Lens
import inc.axon.radar.data.LensStore
import inc.axon.radar.data.Move
import inc.axon.radar.data.Pillar
import inc.axon.radar.data.Radar
import inc.axon.radar.data.Stance
import inc.axon.radar.data.Text

/** The five areas, or the original tabs one switch away, the way the app switches between its two looks. */
enum class Layout { Areas, Original }

/** The five areas of the app, each answering one question. */
enum class Area(val icon: ImageVector, val label: String) {
    Today(Icons.Home, "Today"), Competitors(Icons.Target, "Competitors"), Deals(Icons.Exchange, "Deals"),
    Market(Icons.Globe, "Market"), Axon(Icons.Flag, "AXON"),
}

/** The bar along the bottom: five line icons with their names, black on the deck, grey on the directory pages. */
@Composable
fun AreaBar(area: Area, dark: Boolean, onArea: (Area) -> Unit, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    Row(
        modifier.fillMaxWidth().background(if (dark) ink.night else ink.bar).windowInsetsPadding(WindowInsets.navigationBars).height(58.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Area.entries.forEach { a ->
            val c = if (a == area) Palette.White else Palette.White.copy(alpha = 0.5f)
            Column(
                Modifier.weight(1f).fillMaxHeight().tap { onArea(a) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Ico(a.icon, c, 21.dp)
                Spacer(Modifier.height(3.dp))
                T(a.label.uppercase(), Type.Caps.copy(fontSize = 9.sp, lineHeight = 11.sp, letterSpacing = 0.05.em), c, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Top() = Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

/** A paragraph under a page's title. */
@Composable
private fun Intro(text: String) {
    T(text, Type.Lead, Palette.Ink2, Modifier.padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 14.dp))
}

/** The black block that stands in for AXON's private view when the phone has no key yet. */
@Composable
fun KeyPrompt(text: String, onAddKey: () -> Unit) {
    Column(
        Modifier.padding(horizontal = 14.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(Palette.Black)
            .tap(onAddKey).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(Icons.Key, Palette.White, 18.dp)
            Spacer(Modifier.width(8.dp))
            T("AXON'S PRIVATE VIEW", Type.Caps, Palette.White, maxLines = 1)
        }
        Spacer(Modifier.height(10.dp))
        T(text, Type.Lead, Palette.White)
        Spacer(Modifier.height(10.dp))
        T("Add the read-only key in AXON, under Settings", Type.Small, Palette.White.copy(alpha = 0.65f))
    }
}

// ---- Today -------------------------------------------------------------------------------------

/** Today's deck for AXON: the marked moves that matter most lead, three at most, then the rest in date order. */
fun todayDeck(radar: Radar, lens: Lens?): List<Move> {
    val deck = radar.deck
    if (lens == null) return deck
    val lead = deck.filter { (lens.mark(it.id)?.mark ?: "none") != "none" }
        .sortedWith(compareBy<Move> { Lens.weight(lens.mark(it.id)?.mark) }.thenByDescending { it.date }).take(3)
    return lead + deck.filterNot { it in lead }
}

@Composable
fun TodayArea(
    radar: Radar, lens: Lens?, focus: Int, deckKey: Int, list: Boolean, onList: (Boolean) -> Unit,
    onOpen: (List<Move>, Int) -> Unit, onFocus: (Int) -> Unit, bottomInset: Dp,
) {
    if (!list) {
        val moves = remember(radar, lens) { todayDeck(radar, lens) }
        key(deckKey) {
            Deck(radar, focus, onOpen, onExplore = { onList(true) }, onFocus = onFocus, bottomInset = bottomInset, moves = moves, marks = lens?.marks ?: emptyMap())
        }
    } else AllMoves(radar, lens, { onList(false) }, onOpen, bottomInset)
}

private val MARK_FILTERS = listOf("Everything" to null, "Threats" to "threat", "Opportunities" to "opportunity", "To watch" to "watch")

/** Every move on the radar, newest first, filtered by AXON's mark, by kind and by region. */
@Composable
private fun AllMoves(radar: Radar, lens: Lens?, onBack: () -> Unit, onOpen: (List<Move>, Int) -> Unit, bottomInset: Dp) {
    var mark by rememberSaveable { mutableStateOf(0) }
    var kind by rememberSaveable { mutableStateOf(0) }
    var region by rememberSaveable { mutableStateOf(0) }
    val base = radar.moves
    val m = if (lens != null) MARK_FILTERS[mark].second else null
    val t = KINDS[kind].second
    val r = REGIONS[region].takeIf { region > 0 }
    val shown = base.filter { (m == null || lens?.mark(it.id)?.mark == m) && (t == null || it.type == t) && (r == null || it.region == r) }
    LazyColumn(Modifier.fillMaxSize().background(Palette.Paper), contentPadding = PaddingValues(bottom = bottomInset + 24.dp)) {
        item { Top() }
        item { Titles(listOf("All moves", "Today"), 0) { if (it == 1) onBack() } }
        stickyHeader {
            Column(Modifier.background(Palette.Paper)) {
                if (lens != null) Filters(MARK_FILTERS.map { (l, k) -> l to base.count { k == null || lens.mark(it.id)?.mark == k } }, mark) { mark = it }
                else Filters(KINDS.map { (l, k) -> l to base.count { k == null || it.type == k } }, kind) { kind = it }
                if (lens != null) SmallFilters(KINDS.map { it.first }, kind) { kind = it }
                SmallFilters(REGIONS, region) { region = it }
            }
        }
        if (shown.isEmpty()) item { T("Nothing matches. Try another filter.", Type.Row, Palette.Muted, Modifier.padding(16.dp)) }
        shown.groupBy { Text.monthFull(it.date) }.forEach { (month, ms) ->
            item(key = "m-$month") { MonthHeader(month, ms.size) }
            items(ms, key = { it.id }) { mv -> MarkedRow(mv, lens?.mark(mv.id)?.mark) { onOpen(shown, shown.indexOf(mv)) } }
        }
    }
}

/** A move on light grey with AXON's mark beside its date. */
@Composable
private fun MarkedRow(m: Move, mark: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 1.dp).clip(RoundedCornerShape(2.dp)).background(Palette.Row)
            .tap(onClick).padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MoveThumb(m)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            T(m.title, Type.Name, Palette.Black, maxLines = 2)
            Spacer(Modifier.height(2.dp))
            T(listOfNotNull(m.typeLabel, m.regulator ?: m.companyName, m.region).joinToString(" · "), Type.Small, Palette.Muted, maxLines = 1)
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            T(Text.day(m.date), Type.Row, Palette.Muted, maxLines = 1)
            if (mark != null && mark != "none") {
                Spacer(Modifier.height(5.dp))
                MarkSign(mark, Palette.Black, 10.dp)
            }
        }
    }
}

// ---- Competitors -------------------------------------------------------------------------------

private val ROLE_FILTERS: List<Pair<String, (Stance) -> Boolean>> = listOf(
    "Rivals" to { s -> s.rival },
    "Competitors" to { s -> "competitor" in s.roles },
    "Kits" to { s -> "kit" in s.roles },
    "Buyers & investors" to { s -> "buyer-investor" in s.roles },
    "Prospects" to { s -> "prospect" in s.roles },
    "Partners" to { s -> "partner" in s.roles },
    "Everyone" to { _ -> true },
)
private val MARKET_FILTERS = listOf("All markets") + Lens.MARKETS

@Composable
fun CompetitorsArea(
    radar: Radar, lens: Lens?, section: Int, onSection: (Int) -> Unit, onOpenCompany: (String) -> Unit, onAddKey: () -> Unit,
    bottomInset: Dp, openUrl: (String) -> Unit = {},
) {
    var role by rememberSaveable { mutableStateOf(0) }
    var market by rememberSaveable { mutableStateOf(0) }
    var mapScope by rememberSaveable { mutableStateOf(0) }
    var seg by rememberSaveable { mutableStateOf(0) }
    // No company is named here: until a pick is made, the two rivals that rank highest in the private view lead.
    var first by rememberSaveable { mutableStateOf("") }
    var second by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(section) { list.scrollToItem(0) }
    LazyColumn(Modifier.fillMaxSize().background(Palette.Paper), state = list, contentPadding = PaddingValues(bottom = bottomInset + 28.dp)) {
        item { Top() }
        item { Titles(listOf("Rivals", "Map", "Companies", "Compare"), section, onSection) }
        when (section) {
            0 -> rivals(radar, lens, role, { role = it }, market, { market = it }, onOpenCompany, onAddKey)
            1 -> map(radar, lens, mapScope, { mapScope = it }, onOpenCompany)
            2 -> {
                item { CompanyCarousel(radar, onOpenCompany) }
                companyRows(radar, seg, { seg = it }, onOpenCompany)
            }
            else -> compare(radar, lens, first, { first = it }, second, { second = it }, onOpenCompany, openUrl, onAddKey)
        }
    }
}

private fun LazyListScope.rivals(
    radar: Radar, lens: Lens?, role: Int, onRole: (Int) -> Unit, market: Int, onMarket: (Int) -> Unit,
    onOpen: (String) -> Unit, onAddKey: () -> Unit,
) {
    if (lens == null) {
        item {
            KeyPrompt("Who competes with AXON, how hard and where comes from AXON's private view, kept off the public radar.", onAddKey)
        }
        item { Heading("Most active companies") }
        val since = radar.windowStart ?: ""
        items(radar.companies.sortedByDescending { c -> radar.movesFor(c.id).count { it.date >= since } * 100 + c.moves }.take(15), key = { "a-" + it.id }) { c ->
            ListRow({ CompanyThumb(c.name, radar.latestMove(c.id)?.type) }, c.name, listOfNotNull(c.segment, c.hqCountry).joinToString(" · "),
                Text.plural(c.moves, "Move", "Moves"), { onOpen(c.id) })
        }
        return
    }
    val known = radar.companies.mapNotNull { c -> lens.stance(c.id)?.let { c to it } }
    val mk = MARKET_FILTERS[market].takeIf { market > 0 }
    val pick = ROLE_FILTERS[role].second
    val shown = known.filter { (_, s) -> pick(s) && (mk == null || mk in s.markets) }
        .sortedWith(compareBy<Pair<Company, Stance>> { Lens.THREAT_ORDER.indexOf(it.second.threat) }.thenByDescending { it.first.moves }.thenBy { it.first.name })
    item {
        val hi = known.count { it.second.rival && it.second.threat == "high" }
        val gulf = known.count { (_, s) -> "competitor" in s.roles && s.markets.any { it in setOf("UAE", "Saudi Arabia", "Bahrain") } }
        Intro("${known.count { it.second.rival }} rivals on the radar, $hi of them a high threat; $gulf competitors are active in the Gulf.")
    }
    stickyHeader {
        Column(Modifier.background(Palette.Paper)) {
            Filters(ROLE_FILTERS.map { (l, p) -> l to known.count { p(it.second) && (mk == null || mk in it.second.markets) } }, role, onRole)
            SmallFilters(MARKET_FILTERS, market, onMarket)
        }
    }
    if (shown.isEmpty()) item { T("No company matches. Try another filter.", Type.Row, Palette.Muted, Modifier.padding(16.dp)) }
    shown.groupBy { it.second.threat }.forEach { (threat, group) ->
        item(key = "t-$threat") { MonthHeader(Lens.threatLabel(threat), group.size) }
        items(group, key = { "r-" + it.first.id }) { (c, s) -> RivalRow(c, s, radar.latestMove(c.id)) { onOpen(c.id) } }
    }
}

/** A company as AXON sees it: name and threat, its role, where it matters, and the private note. */
@Composable
private fun RivalRow(c: Company, s: Stance, latest: Move?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 1.dp).clip(RoundedCornerShape(2.dp)).background(Palette.Row)
            .tap(onClick).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CompanyThumb(c.name, latest?.type)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(c.name, Type.Name, Palette.Black, Modifier.weight(1f), maxLines = 1)
                Spacer(Modifier.width(8.dp))
                ThreatTag(s.threat)
            }
            Spacer(Modifier.height(2.dp))
            T(listOfNotNull(s.roles.joinToString(", ") { Lens.roleLabel(it) }, c.hqCountry).joinToString(" · "), Type.Small, Palette.Muted, maxLines = 1)
            if (s.note.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                T(s.note, Type.Small, Palette.Ink2, maxLines = 3)
            }
            if (s.markets.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                T("MATTERS IN " + s.markets.joinToString(" · ").uppercase(), Type.Caps, Palette.Black, maxLines = 1)
            }
        }
    }
}

private val LAYERS = listOf(
    "Settlement rails" to "Settlement rails", "On/off-ramp" to "Ramps", "Custody & wallets" to "Custody and wallets",
    "Merchant gateway" to "Merchant gateways", "Issuer" to "Issuers",
)
private val MAP_REGIONS = listOf("Europe" to "EUROPE", "Middle East" to "MIDDLE EAST", "North America" to "N. AMERICA", "Global" to "GLOBAL")

/**
 * The market map: every company by the layer it works in and its home region, shaded by its threat to AXON,
 * with AXON among the settlement rails of the Middle East.
 */
private fun LazyListScope.map(radar: Radar, lens: Lens?, scope: Int, onScope: (Int) -> Unit, onOpen: (String) -> Unit) {
    item { Intro("Every company on the radar by the layer it works in and its home region. AXON sits with the settlement rails of the Middle East.") }
    if (lens != null) {
        item {
            Filters(listOf("Everyone" to radar.companies.size, "Rivals" to radar.companies.count { lens.stance(it.id)?.rival == true }), scope, onScope)
        }
        item { MapLegend() }
    }
    LAYERS.forEach { (layer, label) ->
        val cos = radar.companies.filter { it.segment == layer && (scope == 0 || lens?.stance(it.id)?.rival == true) }
        if (cos.isNotEmpty() || layer == "Settlement rails") {
            item(key = "layer-$layer") { MapLayer(label, cos, lens, layer == "Settlement rails", onOpen) }
        }
    }
}

@Composable
private fun MapLegend() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf("high" to "High", "medium" to "Medium", "low" to "Low", "none" to "No threat").forEach { (k, l) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (fill, _, edge) = chipStyle(k)
                Box(Modifier.size(11.dp).clip(RoundedCornerShape(2.dp)).background(fill).then(if (edge != null) Modifier.border(1.dp, edge, RoundedCornerShape(2.dp)) else Modifier))
                Spacer(Modifier.width(4.dp))
                T(l, Type.Small, Palette.Ink2, maxLines = 1)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val mono = LocalInk.current.mono
            Box(Modifier.size(11.dp).clip(RoundedCornerShape(2.dp)).background(axonFill()).then(if (mono) Modifier.border(2.dp, Palette.Black, RoundedCornerShape(2.dp)) else Modifier))
            Spacer(Modifier.width(4.dp))
            T("AXON", Type.Small, Palette.Ink2, maxLines = 1)
        }
    }
}

/** Fill, text and edge for a company on the map, by its threat to AXON. */
private fun chipStyle(threat: String?): Triple<Color, Color, Color?> = when (threat) {
    "high" -> Triple(Palette.Black, Palette.White, null)
    "medium" -> Triple(Palette.Bar, Palette.White, null)
    "low" -> Triple(Palette.Muted, Palette.Black, null)
    "none" -> Triple(Palette.White, Palette.Black, null)
    else -> Triple(Palette.Row, Palette.Black, Palette.Black.copy(alpha = 0.12f))
}

@Composable
private fun axonFill(): Color = if (LocalInk.current.mono) Palette.White else Palette.Brand

@Composable
private fun MapLayer(label: String, cos: List<Company>, lens: Lens?, axonHere: Boolean, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 22.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            T(label, Type.display(26f), Palette.Black, Modifier.weight(1f), maxLines = 1)
            T((cos.size + if (axonHere) 1 else 0).toString(), Type.Caps, Palette.Black)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            MAP_REGIONS.forEach { (region, short) ->
                Column(Modifier.weight(1f)) {
                    T(short, Type.Caps.copy(fontSize = 9.sp, letterSpacing = 0.04.em), Palette.Muted, maxLines = 1)
                    Spacer(Modifier.height(5.dp))
                    if (axonHere && region == "Middle East") AxonChip()
                    cos.filter { it.region == region }
                        .sortedWith(compareBy<Company> { Lens.THREAT_ORDER.indexOf(lens?.stance(it.id)?.threat ?: "none") }.thenBy { it.name })
                        .forEach { c -> MapChip(c, lens?.stance(c.id)) { onOpen(c.id) } }
                }
            }
        }
    }
}

@Composable
private fun MapChip(c: Company, s: Stance?, onClick: () -> Unit) {
    val (fill, fg, edge) = chipStyle(s?.threat)
    val shape = RoundedCornerShape(2.dp)
    Box(
        Modifier.padding(bottom = 4.dp).fillMaxWidth().height(24.dp).clip(shape).background(fill)
            .then(if (edge != null) Modifier.border(1.dp, edge, shape) else Modifier).tap(onClick).padding(horizontal = 5.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        T(c.name, Type.Small.copy(fontSize = 11.sp, lineHeight = 13.sp), fg, maxLines = 1)
    }
}

@Composable
private fun AxonChip() {
    val ink = LocalInk.current
    val shape = RoundedCornerShape(2.dp)
    Box(
        Modifier.padding(bottom = 4.dp).fillMaxWidth().height(24.dp).clip(shape).background(axonFill())
            .then(if (ink.mono) Modifier.border(2.dp, Palette.Black, shape) else Modifier).padding(horizontal = 5.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        T("AXON", Type.Name.copy(fontSize = 12.sp), if (ink.mono) Palette.Black else Palette.White, maxLines = 1)
    }
}

// ---- Compare -----------------------------------------------------------------------------------

/** Assessed rivals in the order they matter: threat level first, then name. */
private fun ordered(g: Grid, lens: Lens): List<Assessed> =
    g.rivals.sortedWith(compareBy<Assessed> { Lens.THREAT_ORDER.indexOf(lens.stance(it.id)?.threat ?: "none") }.thenBy { it.name })

/**
 * AXON against two competitors, pillar by pillar, the way AXON's July 2026 assessment set the grid out: a mark
 * for each pillar, what exists behind it, and the sources. Every mark rests on a source; marketing alone never counts.
 */
private fun LazyListScope.compare(
    radar: Radar, lens: Lens?, first: String, onFirst: (String) -> Unit, second: String, onSecond: (String) -> Unit,
    onOpen: (String) -> Unit, openUrl: (String) -> Unit, onAddKey: () -> Unit,
) {
    if (lens == null) {
        item { KeyPrompt("AXON against its competitors, pillar by pillar, with the evidence behind every mark, comes from AXON's private view.", onAddKey) }
        return
    }
    val g = lens.grid
    val axon = g?.axon
    val rivals = g?.let { ordered(it, lens) }.orEmpty()
    if (g == null || axon == null || rivals.size < 2) {
        item { Intro("The side-by-side comparison arrives with the next update of AXON's private view. Refresh it in AXON, under Settings.") }
        return
    }
    val a = rivals.firstOrNull { it.id == first } ?: rivals[0]
    val others = rivals.filter { it.id != a.id }
    val b = others.firstOrNull { it.id == second } ?: others[0]
    val three = listOf(axon, a, b)
    item { Intro("AXON against two competitors on the six pillars of orchestration, regulation and public engineering. Every mark rests on a source.") }
    item { PickRow("AXON AGAINST", rivals, a.id, onFirst) }
    item { PickRow("AND", others, b.id, onSecond) }
    item { PillarLegend() }
    item { PillarMatrix(g, three) }
    listOf(a, b).forEach { c ->
        item(key = "v-" + c.id) { VerdictRow(c, lens.stance(c.id), radar.companyById.containsKey(c.id)) { onOpen(c.id) } }
    }
    g.pillars.forEach { p ->
        item(key = "p-" + p.id + a.id + b.id) { PillarBlock(p, three, openUrl) }
    }
    item {
        val dates = three.joinToString(" · ") { "${it.name} ${Text.day(it.checked)}" }
        T("Checked: $dates. ${axon.basis ?: ""}", Type.Small, Palette.Muted, Modifier.padding(start = 14.dp, end = 14.dp, top = 18.dp))
    }
    if (g.method.isNotBlank()) item { T(g.method, Type.Small, Palette.Muted, Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp)) }
    val next = g.next.mapNotNull { radar.companyById[it]?.name }
    if (next.isNotEmpty()) item {
        T("Next to assess: " + next.joinToString(", ") + ".", Type.Small, Palette.Muted, Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp))
    }
}

/** A labelled row of names to pick one company from. */
@Composable
private fun PickRow(label: String, cos: List<Assessed>, selected: String, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Palette.Paper)) {
        T(label, Type.Caps, Palette.Black, Modifier.padding(start = 14.dp, top = 4.dp, bottom = 6.dp), maxLines = 1)
        SmallFilters(cos.map { it.name }, cos.indexOfFirst { it.id == selected }.coerceAtLeast(0)) { i -> onPick(cos[i].id) }
    }
}

/** ● documented, ◐ claimed only, ○ absent: a filled dot, a half-filled ring and an empty ring. */
@Composable
fun PillarSign(mark: String, color: Color, size: Dp = 12.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val r = w * 0.42f
        val c = Offset(w / 2, w / 2)
        when (mark) {
            "full" -> drawCircle(color, radius = r, center = c)
            "claimed" -> {
                drawArc(color, 90f, 180f, true, topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2))
                drawCircle(color, radius = r - w * 0.06f, center = c, style = Stroke(w * 0.12f))
            }
            else -> drawCircle(color, radius = r - w * 0.06f, center = c, style = Stroke(w * 0.12f))
        }
    }
}

@Composable
private fun PillarLegend() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf("full", "claimed", "absent").forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillarSign(m, Palette.Black, 11.dp)
                Spacer(Modifier.width(4.dp))
                T(Lens.pillarShort(m), Type.Small, Palette.Ink2, maxLines = 1)
            }
        }
    }
}

/** The whole grid at a glance: a row per pillar, a column per company. */
@Composable
private fun PillarMatrix(g: Grid, cos: List<Assessed>) {
    Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(2.dp)).background(Palette.Row).padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1.5f))
            cos.forEach { c -> T(c.name.uppercase(), Type.Caps, Palette.Black, Modifier.weight(1f), maxLines = 1) }
        }
        g.pillars.forEach { p ->
            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(1.dp).background(Palette.Rule))
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                T(p.name, Type.Small, Palette.Ink2, Modifier.weight(1.5f).padding(end = 6.dp), maxLines = 2)
                cos.forEach { c ->
                    Box(Modifier.weight(1f)) { PillarSign(c.cells[p.id]?.mark ?: "absent", Palette.Black, 14.dp) }
                }
            }
        }
    }
}

/** What a rival is, in AXON's reading, with its threat level; opens the company. */
@Composable
private fun VerdictRow(c: Assessed, s: Stance?, known: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 1.dp).clip(RoundedCornerShape(2.dp)).background(Palette.Row)
            .then(if (known) Modifier.tap(onClick) else Modifier).padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(c.name, Type.Name, Palette.Black, Modifier.weight(1f), maxLines = 1)
            if (s != null) { Spacer(Modifier.width(8.dp)); ThreatTag(s.threat) }
        }
        Spacer(Modifier.height(4.dp))
        T(c.verdict, Type.Small, Palette.Ink2)
        Spacer(Modifier.height(5.dp))
        T("${c.count("full")} DOCUMENTED · ${c.count("claimed")} CLAIMED ONLY · ${c.count("absent")} ABSENT", Type.Caps, Palette.Black, maxLines = 1)
    }
}

/** One pillar: its test, then each company's mark, what exists behind it, and the sources. */
@Composable
private fun PillarBlock(p: Pillar, cos: List<Assessed>, openUrl: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 24.dp)) {
        T(p.name, Type.display(26f), Palette.Black)
        if (p.test.isNotBlank()) { Spacer(Modifier.height(4.dp)); T(p.test.uppercase(), Type.Caps, Palette.Muted) }
        cos.forEach { c ->
            val cell = c.cells[p.id]
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Rule))
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillarSign(cell?.mark ?: "absent", Palette.Black, 13.dp)
                Spacer(Modifier.width(7.dp))
                T(c.name, Type.Name, Palette.Black, Modifier.weight(1f), maxLines = 1)
                T(Lens.pillarShort(cell?.mark).uppercase(), Type.Caps, Palette.Ink2, maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
            T(cell?.note ?: "Not assessed.", Type.Small, Palette.Ink2)
            val src = cell?.sources.orEmpty()
            if (src.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    src.zip(sourceLabels(src)).forEach { (s, label) ->
                        val url = s.startsWith("http")
                        T(label, Type.Caps.copy(fontSize = 10.sp), if (url) Palette.Black else Palette.Muted,
                            Modifier.clip(RoundedCornerShape(2.dp)).background(Palette.Row).then(if (url) Modifier.tap { openUrl(s) } else Modifier)
                                .padding(horizontal = 7.dp, vertical = 5.dp),
                            maxLines = 1)
                    }
                }
            }
        }
    }
}

/** A source as its site's name: "docs.ripple.com", "vara.ae". */
internal fun sourceName(url: String): String =
    runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull() ?: url

/**
 * Labels for a cell's sources: the site's name, and where two share a site, the page's own name after it
 * ("docs.ripple.com › funding-methods"). Sources that are not links keep their words.
 */
internal fun sourceLabels(sources: List<String>): List<String> {
    val hosts = sources.map { if (it.startsWith("http")) sourceName(it) else null }
    return sources.mapIndexed { i, s ->
        val h = hosts[i] ?: return@mapIndexed s
        if (hosts.count { it == h } < 2) h
        else {
            val page = runCatching { java.net.URI(s).path }.getOrNull().orEmpty().trimEnd('/').substringAfterLast('/')
                .substringBeforeLast('.').let { if (it.length > 30) it.take(29) + "…" else it }
            if (page.isBlank()) h else "$h › $page"
        }
    }
}

// ---- Deals -------------------------------------------------------------------------------------

private val STAGES: List<Pair<String, Set<String>?>> = listOf(
    "All" to null,
    "Seed" to setOf("Pre-seed", "Seed", "Seed extension"),
    "Series A" to setOf("Pre-Series A", "Series A"),
    "Series B+" to setOf("Series B", "Series C", "Series D", "Series E", "Series F", "Growth"),
    "Strategic" to setOf("Strategic", "Private placement"),
    "Listings" to setOf("IPO"),
    "Other" to setOf("Debt", "Token sale", "Undisclosed", ""),
)
private val STATUSES = listOf("All" to null, "Completed" to "completed", "Announced" to "announced", "Reported" to "reported", "Called off" to "called off")
private val STATUS_LABEL = mapOf("completed" to "Completed", "announced" to "Announced", "reported" to "Reported", "called off" to "Called off")

private fun stageOf(m: Move): String = m.round?.stage ?: ""
private fun median(xs: List<Double>): Double? {
    if (xs.isEmpty()) return null
    val s = xs.sorted()
    return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
}

val DEALS_SECTIONS = listOf("Funding", "Acquisitions", "Partnerships", "Investors")

@Composable
fun DealsArea(
    radar: Radar, section: Int, onSection: (Int) -> Unit, onOpenMoves: (List<Move>, Int) -> Unit, bottomInset: Dp,
    lens: Lens? = null, onOpenInvestor: (String) -> Unit = {},
) {
    var stage by rememberSaveable { mutableStateOf(0) }
    var status by rememberSaveable { mutableStateOf(0) }
    var kind by rememberSaveable { mutableStateOf(0) }
    var region by rememberSaveable { mutableStateOf(0) }
    var invFilter by rememberSaveable { mutableStateOf(0) }
    var invQuery by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(section) { list.scrollToItem(0) }
    LazyColumn(Modifier.fillMaxSize().background(Palette.Paper), state = list, contentPadding = PaddingValues(bottom = bottomInset + 28.dp)) {
        item { Top() }
        item { Titles(DEALS_SECTIONS, section, onSection) }
        when (section) {
            0 -> funding(radar, stage, { stage = it }, region, { region = it }, onOpenMoves, onOpenInvestor) { onSection(3) }
            1 -> acquisitions(radar, status, { status = it }, onOpenMoves)
            2 -> partnerships(radar, kind, { kind = it }, onOpenMoves)
            else -> investorsList(radar, lens, invFilter, { invFilter = it }, invQuery, { invQuery = it }, onOpenInvestor)
        }
    }
}

/** Everyone named as a lead or other investor, most active first, with filters and a search over names and portfolios. */
private fun LazyListScope.investorsList(
    radar: Radar, lens: Lens?, filter: Int, onFilter: (Int) -> Unit, query: String, onQuery: (String) -> Unit, onOpen: (String) -> Unit,
) {
    val all = radar.investors
    val rounds = all.flatMap { v -> v.rounds.map { it.id } }.toSet().size
    val since = all.flatMap { v -> v.rounds.map { it.date.take(7) } }.minOrNull()
    val tests: List<Pair<String, (inc.axon.radar.data.Investor) -> Boolean>> = listOf<Pair<String, (inc.axon.radar.data.Investor) -> Boolean>>(
        "All" to { _ -> true }, "Led a round" to { v -> v.led.isNotEmpty() }, "Two or more" to { v -> v.rounds.size > 1 },
    ) + (if (lens != null) listOf<Pair<String, (inc.axon.radar.data.Investor) -> Boolean>>("Back AXON rivals" to { v -> Investors.rivalsBacked(v, lens).isNotEmpty() }) else emptyList())
    val f = filter.coerceIn(0, tests.lastIndex)
    item {
        Figures(
            "${Text.plural(all.size, "investor", "investors")} in $rounds rounds since ${Text.monthYear(since)}",
            listOf(all.count(tests[1].second).toString() to "Led a round", all.count(tests[2].second).toString() to "Two or more") +
                (if (lens != null) listOf(all.count(tests[3].second).toString() to "Back AXON rivals") else emptyList()),
        )
    }
    item { T("Everyone named as a lead or other investor in a round on the radar, most active first. The money is each round's size, not what one investor put in.", Type.Small, Palette.Muted, Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 4.dp)) }
    stickyHeader {
        Column(Modifier.background(Palette.Paper)) {
            Filters(tests.map { (l, t) -> l to all.count(t) }, f, onFilter)
            SearchField(query, onQuery, "Search investors and the companies they backed")
        }
    }
    val q = query.trim().lowercase()
    val shown = all.filter(tests[f].second).filter { v ->
        q.isEmpty() || (listOfNotNull(v.name, v.country) + v.portfolio.map { it.name }).joinToString(" ").lowercase().contains(q)
    }
    if (shown.isEmpty()) item { T("No investor matches.", Type.Row, Palette.Muted, Modifier.padding(16.dp)) }
    items(shown, key = { "inv-" + it.id }) { v -> InvestorRow(v, Investors.rivalsBacked(v, lens).size) { onOpen(v.id) } }
}

@Composable
private fun InvestorRow(v: inc.axon.radar.data.Investor, rivals: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 1.dp).clip(RoundedCornerShape(2.dp)).background(Palette.Row)
            .tap(onClick).padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompanyThumb(v.name, "Funding")
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            T(v.name, Type.Name, Palette.Black, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            T(
                listOfNotNull(
                    v.led.size.takeIf { it > 0 }?.let { "led $it" }, "latest ${Text.monthYear(v.rounds.first().date.take(7))}",
                    rivals.takeIf { it > 0 }?.let { "backs " + Text.plural(it, "AXON rival", "AXON rivals") },
                    v.portfolio.take(3).joinToString(", ") { it.name } + if (v.portfolio.size > 3) " +${v.portfolio.size - 3}" else "",
                ).joinToString(" · "),
                Type.Small, Palette.Muted, maxLines = 1,
            )
        }
        Spacer(Modifier.width(10.dp))
        T(Text.plural(v.rounds.size, "round", "rounds"), Type.Name, Palette.Black, maxLines = 1)
    }
}

/** A headline figure and up to four small ones, as on the Trends page. */
@Composable
private fun Figures(headline: String, stats: List<Pair<String, String>>) {
    Column(Modifier.padding(horizontal = 14.dp)) {
        T(headline, Type.display(34f), Palette.Black)
        if (stats.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth()) {
                stats.forEachIndexed { i, (n, l) ->
                    if (i > 0) Box(Modifier.width(1.dp).height(58.dp).background(Palette.Rule))
                    Column(Modifier.weight(1f).padding(start = if (i > 0) 10.dp else 0.dp)) {
                        T(n, Type.display(34f), Palette.Black, maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        T(l, Type.Small, Palette.Muted, maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun LazyListScope.funding(
    radar: Radar, stage: Int, onStage: (Int) -> Unit, region: Int, onRegion: (Int) -> Unit, onOpen: (List<Move>, Int) -> Unit,
    onOpenInvestor: (String) -> Unit, onEveryInvestor: () -> Unit,
) {
    val all = radar.moves.filter { it.type == "Funding" }
    val recent = all.filter { !it.history }
    item {
        Figures(
            "${Text.money(recent.mapNotNull { it.amount }.sum()) ?: "$0"} disclosed across ${recent.size} rounds since ${Text.monthYear(radar.trends.since)}",
            listOf(
                (median(recent.mapNotNull { it.amount })?.let { Text.money(it) } ?: "–") to "Median round",
                recent.count { it.leads.isNotEmpty() }.toString() to "With a lead",
                recent.count { it.round?.valuation != null }.toString() to "Valued",
            ),
        )
    }
    item { Heading("By stage") }
    item { T("Every round on the radar since ${Text.monthYear(all.minOfOrNull { it.date.take(7) })}, including the deal history.", Type.Small, Palette.Muted, Modifier.padding(start = 14.dp, end = 14.dp, bottom = 8.dp)) }
    items(STAGES.drop(1), key = { "s-" + it.first }) { (label, set) ->
        val rs = all.filter { stageOf(it) in set!! }
        val amt = median(rs.mapNotNull { it.amount })
        // One valuation per company, its latest, so repeated reports of the same raise count once.
        val vals = rs.filter { it.round?.valuation != null }.sortedByDescending { it.date }.distinctBy { it.companyId ?: it.id }.mapNotNull { it.round?.valuation }
        PaperRow(
            label, amt?.let { "median ${Text.money(it)}" } ?: "–",
            listOfNotNull(Text.plural(rs.size, "round", "rounds"), median(vals)?.let { "median valuation ${Text.money(it)} (${vals.size})" }).joinToString(" · "),
        ) { if (rs.isNotEmpty()) onOpen(rs, 0) }
    }
    item { Heading("Most active investors") }
    items(radar.investors.take(12), key = { "i-" + it.id }) { v ->
        PaperRow(
            v.name, Text.plural(v.rounds.size, "round", "rounds"),
            listOfNotNull(v.led.size.takeIf { it > 0 }?.let { "led $it" }, "latest ${Text.monthYear(v.rounds.first().date.take(7))}").joinToString(" · "),
        ) { onOpenInvestor(v.id) }
    }
    item { PaperRow("Every investor", Text.plural(radar.investors.size, "investor", "investors"), null, onEveryInvestor) }
    item { Heading("Every round", all.size) }
    val set = STAGES[stage].second
    val r = REGIONS[region].takeIf { region > 0 }
    val shown = all.filter { (set == null || stageOf(it) in set) && (r == null || it.region == r) }
    stickyHeader {
        Column(Modifier.background(Palette.Paper)) {
            Filters(STAGES.map { (l, s) -> l to all.count { s == null || stageOf(it) in s } }, stage, onStage)
            SmallFilters(REGIONS, region, onRegion)
        }
    }
    if (shown.isEmpty()) item { T("No round matches. Try another stage or region.", Type.Row, Palette.Muted, Modifier.padding(16.dp)) }
    shown.groupBy { Text.monthFull(it.date) }.forEach { (month, ms) ->
        item(key = "fm-$month") { MonthHeader(month, ms.size) }
        items(ms, key = { "f-" + it.id }) { m ->
            DealRow(
                m, listOfNotNull(m.companyName ?: m.title, m.round?.stage).joinToString(" · "),
                listOfNotNull(
                    m.leads.takeIf { it.isNotEmpty() }?.let { "Led by " + it.joinToString(", ") { p -> p.name } },
                    m.investors.takeIf { it.isNotEmpty() }?.let { "with " + it.take(3).joinToString(", ") { p -> p.name } + if (it.size > 3) " +${it.size - 3}" else "" },
                    m.round?.valuation?.let { "valued at ${Text.money(it)}" },
                ).joinToString(", ").ifEmpty { m.title },
                Text.money(m.amount) ?: "",
            ) { onOpen(shown, shown.indexOf(m)) }
        }
    }
}

private fun LazyListScope.acquisitions(radar: Radar, status: Int, onStatus: (Int) -> Unit, onOpen: (List<Move>, Int) -> Unit) {
    val all = radar.moves.filter { it.type == "M&A" }
    item {
        Figures(
            "${all.size} acquisitions, bids and sales since ${Text.monthYear(all.minOfOrNull { it.date.take(7) })}",
            listOf(
                all.count { it.deal?.status == "completed" }.toString() to "Completed",
                all.count { it.deal?.status == "announced" }.toString() to "Announced",
                all.count { it.deal?.status == "reported" }.toString() to "Reported",
                all.count { it.deal?.status == "called off" }.toString() to "Called off",
            ),
        )
    }
    val st = STATUSES[status].second
    val shown = all.filter { st == null || it.deal?.status == st }
    stickyHeader { Filters(STATUSES.map { (l, s) -> l to all.count { s == null || it.deal?.status == s } }, status, onStatus) }
    if (shown.isEmpty()) item { T("No deal matches.", Type.Row, Palette.Muted, Modifier.padding(16.dp)) }
    shown.groupBy { Text.monthFull(it.date) }.forEach { (month, ms) ->
        item(key = "am-$month") { MonthHeader(month, ms.size) }
        items(ms, key = { "a-" + it.id }) { m ->
            DealRow(
                m, m.title,
                listOfNotNull(m.deal?.status?.let { STATUS_LABEL[it] ?: it }, m.deal?.rationale).joinToString(" · ").replaceFirstChar { it.uppercase() },
                Text.money(m.amount) ?: "",
            ) { onOpen(shown, shown.indexOf(m)) }
        }
    }
}

private fun LazyListScope.partnerships(radar: Radar, kind: Int, onKind: (Int) -> Unit, onOpen: (List<Move>, Int) -> Unit) {
    val all = radar.moves.filter { it.type == "Partnership" }
    val kinds = listOf("All" to null) + all.mapNotNull { it.kind }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key to it.key }
    item {
        val ids = all.flatMap { m -> (listOfNotNull(m.companyId) + m.parties.map { it.id } + m.related).distinct() }
        Figures(
            "${all.size} partnerships since ${Text.monthYear(all.minOfOrNull { it.date.take(7) })}",
            listOf(ids.distinct().size.toString() to "Organisations", kinds.size.minus(1).toString() to "Kinds"),
        )
    }
    item { Heading("Most connected") }
    val counts = all.flatMap { m -> (listOfNotNull(m.companyId) + m.parties.map { it.id } + m.related).distinct().map { it to m } }
        .groupBy({ it.first }, { it.second })
        .filterKeys { radar.companyById.containsKey(it) }
        .entries.sortedByDescending { it.value.size }.take(8)
    items(counts, key = { "c-" + it.key }) { (id, ms) ->
        PaperRow(radar.nameOf(id) ?: id, Text.plural(ms.size, "partnership", "partnerships"), ms.mapNotNull { it.kind }.distinct().take(3).joinToString(" · ")) { onOpen(ms, 0) }
    }
    item { Heading("Every partnership", all.size) }
    val k = kinds[kind.coerceIn(0, kinds.lastIndex)].second
    val shown = all.filter { k == null || it.kind == k }
    stickyHeader { Filters(kinds.map { (l, kk) -> l to all.count { kk == null || it.kind == kk } }, kind.coerceIn(0, kinds.lastIndex), onKind) }
    shown.groupBy { Text.monthFull(it.date) }.forEach { (month, ms) ->
        item(key = "pm-$month") { MonthHeader(month, ms.size) }
        items(ms, key = { "p-" + it.id }) { m ->
            DealRow(
                m, m.title,
                listOfNotNull(m.kind, m.parties.takeIf { it.isNotEmpty() }?.joinToString(", ") { p -> "${p.name} (${p.role})" }).joinToString(" · "),
                Text.day(m.date),
            ) { onOpen(shown, shown.indexOf(m)) }
        }
    }
}

/** A deal on light grey: its square, a bold line, the details and the figure on the right. */
@Composable
private fun DealRow(m: Move, title: String, sub: String, right: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 1.dp).clip(RoundedCornerShape(2.dp)).background(Palette.Row)
            .tap(onClick).padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MoveThumb(m)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            T(title, Type.Name, Palette.Black, maxLines = 2)
            if (sub.isNotBlank()) { Spacer(Modifier.height(2.dp)); T(sub, Type.Small, Palette.Muted, maxLines = 2) }
        }
        if (right.isNotBlank()) {
            Spacer(Modifier.width(10.dp))
            T(right, Type.Name, Palette.Black, maxLines = 1)
        }
    }
}

// ---- Market ------------------------------------------------------------------------------------

@Composable
fun MarketArea(radar: Radar, section: Int, onSection: (Int) -> Unit, onOpenMoves: (List<Move>, Int) -> Unit, bottomInset: Dp) {
    var licKind by rememberSaveable { mutableStateOf(0) }
    var region by rememberSaveable { mutableStateOf(0) }
    val list = rememberLazyListState()
    LaunchedEffect(section) { list.scrollToItem(0) }
    LazyColumn(Modifier.fillMaxSize().background(Palette.Paper), state = list, contentPadding = PaddingValues(bottom = bottomInset + 28.dp)) {
        item { Top() }
        item { Titles(listOf("Trends", "Licences"), section, onSection) }
        if (section == 0) trendsItems(radar)
        else {
            item { RegulatorCarousel(radar, onOpenMoves) }
            val rules = radar.moves.filter { it.type == "License" || it.type == "Regulation" }
            movesRows(rules, KINDS.filter { it.second == null || it.second == "License" || it.second == "Regulation" }, licKind, { licKind = it }, region, { region = it }, onOpenMoves)
        }
    }
}

// ---- AXON --------------------------------------------------------------------------------------

/** Everything the Settings page reads and does. */
class AxonSettings(
    val connected: Boolean, val fetchedAt: String?, val busy: Boolean, val message: String?,
    val onConnect: (String) -> Unit, val onForget: () -> Unit, val onCheck: () -> Unit,
    val layout: Layout, val onLayout: (Layout) -> Unit, val look: Look, val onLook: (Look) -> Unit,
    val refreshing: Boolean, val onRefresh: () -> Unit, val onOpenWeb: () -> Unit,
    /** AXON's private view switched off on this phone, without removing the key. */
    val hidden: Boolean = false, val onHidden: (Boolean) -> Unit = {},
    val onOpenSources: () -> Unit = {},
)

val AXON_SECTIONS = listOf("Watching", "Next steps", "Settings")

@Composable
fun AxonNextSteps(radar: Radar, lens: Lens?, onSection: (Int) -> Unit, onOpenMoves: (List<Move>, Int) -> Unit, onAddKey: () -> Unit, bottomInset: Dp) {
    LazyColumn(Modifier.fillMaxSize().background(Palette.Paper), contentPadding = PaddingValues(bottom = bottomInset + 28.dp)) {
        item { Top() }
        item { Titles(AXON_SECTIONS, 1, onSection) }
        if (lens == null) {
            item { KeyPrompt("The open steps for AXON, each tied to the move that prompted it, and the decisions taken, come from AXON's private view.", onAddKey) }
            return@LazyColumn
        }
        val steps = lens.steps.mapNotNull { (id, mk) -> radar.moveById[id]?.let { it to mk } }.sortedByDescending { it.first.date }
        item { Intro("${Text.plural(steps.size, "open step", "open steps")} for AXON, each tied to the move that prompted it. Newest first within each owner.") }
        Lens.OWNERS.forEach { owner ->
            val mine = steps.filter { it.second.owner == owner }
            if (mine.isNotEmpty()) {
                item(key = "o-$owner") { MonthHeader(owner, mine.size) }
                items(mine, key = { "st-" + it.first.id }) { (m, mk) ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 1.dp).clip(RoundedCornerShape(2.dp)).background(Palette.Row)
                            .tap { val ms = mine.map { it.first }; onOpenMoves(ms, ms.indexOf(m)) }.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        MarkSign(mk.mark, Palette.Black, 10.dp, Modifier.padding(top = 4.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            T(mk.next ?: "", Type.Name, Palette.Black)
                            Spacer(Modifier.height(3.dp))
                            T("${mk.label} · ${m.title}", Type.Small, Palette.Muted, maxLines = 2)
                        }
                        Spacer(Modifier.width(8.dp))
                        T(Text.day(m.date), Type.Small, Palette.Muted, maxLines = 1)
                    }
                }
            }
        }
        if (lens.decisions.isNotEmpty()) {
            item { Heading("Decisions", lens.decisions.size) }
            items(lens.decisions.sortedByDescending { it.date }, key = { "d-" + it.date + it.text.take(12) }) { d ->
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 1.dp).clip(RoundedCornerShape(2.dp)).background(Palette.Row)
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                ) {
                    T(Text.dayYear(d.date).uppercase(), Type.Caps, Palette.Muted, maxLines = 1)
                    Spacer(Modifier.height(3.dp))
                    T(d.text, Type.Row, Palette.Black)
                    if (d.evidence.isNotBlank()) { Spacer(Modifier.height(3.dp)); T(d.evidence, Type.Small, Palette.Muted) }
                }
            }
        }
    }
}

@Composable
fun AxonSettingsPage(radar: Radar, lens: Lens?, s: AxonSettings, onSection: (Int) -> Unit, bottomInset: Dp) {
    LazyColumn(Modifier.fillMaxSize().background(Palette.Paper), contentPadding = PaddingValues(bottom = bottomInset + 28.dp)) {
        item { Top() }
        item { Titles(AXON_SECTIONS, 2, onSection) }
        item { Heading("Private view") }
        item { KeyPanel(lens, s) }
        item { Heading("News sources") }
        item {
            Column(Modifier.padding(horizontal = 14.dp)) {
                T("The daily update reads only the sources the team has switched on, and never cites one switched off. The list is kept on the web page, where each change is made by a signed-in person whose role allows it.", Type.Row, Palette.Ink2)
                Spacer(Modifier.height(12.dp))
                Button("Manage news sources on the web", Icons.ArrowOut, dark = false, onClick = s.onOpenSources)
            }
        }
        item { Heading("Layout") }
        item { LayoutPicker(s.layout, s.onLayout) }
        item { Heading("Look") }
        item { LookPicker(radar, s.look, s.onLook) }
        item { Heading("Data") }
        item { PaperRow("Updated", Text.updated(radar.updatedAt)) }
        item { PaperRow("Next update", radar.nextRun?.let { Text.updated(it) + ", 06:54 Riyadh" } ?: "Daily") }
        item { PaperRow("Moves tracked", radar.moves.size.toString(), "${radar.moves.count { !it.history }} since ${Text.monthYear(radar.trends.since)}, the rest deal history") }
        item { PaperRow("Companies", radar.companies.size.toString()) }
        item { PaperRow("Regulators", radar.regulators.size.toString()) }
        item {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 20.dp)) {
                Button(if (s.refreshing) "Checking for news…" else "Refresh now", Icons.Refresh, dark = true, onClick = s.onRefresh)
                Spacer(Modifier.height(8.dp))
                Button("Open the full radar on the web", Icons.ArrowOut, dark = false, onClick = s.onOpenWeb)
                Spacer(Modifier.height(24.dp))
                T(
                    "Market news comes from the radar's public file. AXON's marks, roles, threat levels and next steps come from the private repository with the key, and stay on this phone, sealed by its keystore. Type is Inter Tight, under the SIL Open Font License.",
                    Type.Small, Palette.Muted,
                )
            }
        }
    }
}

/** A row that is one switch: the label and what it does on the left, the knob on the right. */
@Composable
private fun SwitchRow(label: String, sub: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(Palette.Row)
            .selectable(selected = on, role = Role.Switch, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            T(label, Type.Name, Palette.Black, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            T(sub, Type.Small, Palette.Muted, maxLines = 2)
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(42.dp, 24.dp).clip(RoundedCornerShape(12.dp)).background(if (on) Palette.Black else Palette.Faint)) {
            Box(Modifier.padding(3.dp).size(18.dp).align(if (on) Alignment.CenterEnd else Alignment.CenterStart).clip(CircleShape).background(Palette.White))
        }
    }
}

@Composable
private fun Button(text: String, icon: ImageVector, dark: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(3.dp)).background(if (dark) Palette.Black else Palette.Row).tap(onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(text, Type.Tab, if (dark) Palette.White else Palette.Black, Modifier.weight(1f), maxLines = 1)
        Ico(icon, if (dark) Palette.White else Palette.Black, 18.dp)
    }
}

/** Connects the phone to AXON's private view with the read-only key, or shows what it holds. */
@Composable
private fun KeyPanel(lens: Lens?, s: AxonSettings) {
    // Never kept in saved state: the key lives only in this field until it is sealed.
    var key by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    Column(Modifier.padding(horizontal = 14.dp)) {
        if (s.connected && lens != null) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(Palette.Black).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ico(Icons.Key, Palette.White, 18.dp)
                    Spacer(Modifier.width(8.dp))
                    T(if (s.hidden) "CONNECTED · HIDDEN ON THIS PHONE" else "CONNECTED", Type.Caps, Palette.White, maxLines = 1)
                }
                Spacer(Modifier.height(10.dp))
                T(
                    if (s.hidden) "AXON's marks, roles, threat levels, notes, Compare and next steps are hidden here. The key stays sealed on the phone."
                    else "${lens.marks.size} marks, ${lens.stances.size} companies and ${lens.steps.size} open steps.",
                    Type.Lead, Palette.White,
                )
                Spacer(Modifier.height(8.dp))
                T("Read from ${LensStore.REPO}${s.fetchedAt?.let { " · checked ${Text.updated(it)}" } ?: ""}", Type.Small, Palette.White.copy(alpha = 0.65f))
            }
            Spacer(Modifier.height(8.dp))
            SwitchRow("Show on this phone", if (s.hidden) "Off: this phone shows the public radar only" else "On: switch off to show the public radar only here", !s.hidden) { s.onHidden(!s.hidden) }
            Spacer(Modifier.height(8.dp))
            Button(if (s.busy) "Checking…" else "Check for changes", Icons.Refresh, dark = false, onClick = s.onCheck)
            Spacer(Modifier.height(8.dp))
            Button("Remove the key from this phone", Icons.Close, dark = false, onClick = s.onForget)
        } else {
            T("AXON's marks, roles, threat levels and next steps live in the private repository. Paste a read-only key once: it is sealed with this phone's keystore and only ever sent to GitHub.", Type.Row, Palette.Ink2)
            Spacer(Modifier.height(12.dp))
            listOf(
                "In GitHub: profile picture, Settings, Developer settings, Personal access tokens, Fine-grained tokens, Generate new token.",
                "Owner TEAM-HAT; only the axon-radar-private repository; Contents set to Read-only.",
                "Copy the key and paste it here.",
            ).forEachIndexed { i, step ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    T("${i + 1}", Type.Name, Palette.Black, Modifier.width(18.dp))
                    T(step, Type.Small.copy(fontSize = 13.sp, lineHeight = 17.sp), Palette.Ink2)
                }
            }
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                key, { key = it }, Modifier.fillMaxWidth(), singleLine = true,
                textStyle = Type.Row.copy(color = Palette.Black), cursorBrush = SolidColor(Palette.Black),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrect = false),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                decorationBox = { inner ->
                    Row(
                        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFF2F2F2)).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Ico(Icons.Key, Palette.Ink2, 18.dp)
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f)) {
                            if (key.isEmpty()) T("Paste the read-only key", Type.Row, Palette.Muted, maxLines = 1)
                            inner()
                        }
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            Button(if (s.busy) "Connecting…" else "Connect", Icons.Check, dark = true) {
                focus.clearFocus()
                if (!s.busy) s.onConnect(key)
            }
        }
        if (s.message != null) {
            Spacer(Modifier.height(10.dp))
            T(s.message, Type.Small.copy(fontSize = 13.sp, lineHeight = 17.sp), Palette.Black)
        }
        // Once the key is accepted the field is cleared, so it is never shown again.
        LaunchedEffect(s.connected) { if (s.connected) key = "" }
    }
}

/** The five areas or the original tabs, each shown as a small copy of its bar. The chosen one is framed in black. */
@Composable
fun LayoutPicker(layout: Layout, onLayout: (Layout) -> Unit) {
    Column(Modifier.padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LayoutOption("Five areas", Area.entries.map { it.icon }, null, layout == Layout.Areas, Modifier.weight(1f)) { onLayout(Layout.Areas) }
            LayoutOption("Original", Tab.entries.map { it.icon }, null, layout == Layout.Original, Modifier.weight(1f)) { onLayout(Layout.Original) }
        }
        Spacer(Modifier.height(10.dp))
        T("The original tabs stay one switch away, with every screen they had. Kept on this phone.", Type.Small, Palette.Muted)
    }
}

@Composable
private fun LayoutOption(label: String, icons: List<ImageVector>, names: List<String>?, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(3.dp)
    Column(
        modifier.clip(shape).background(Palette.Row)
            .then(if (selected) Modifier.border(2.dp, Palette.Black, shape) else Modifier)
            .selectable(selected, interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(Modifier.fillMaxWidth().height(58.dp).background(LocalInk.current.bar).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            icons.forEachIndexed { i, ic ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Ico(ic, Palette.White.copy(alpha = if (i == 0) 1f else 0.6f), 15.dp)
                    if (names != null) {
                        Spacer(Modifier.height(2.dp))
                        T(names[i].take(5).uppercase(), Type.Caps.copy(fontSize = 6.sp, lineHeight = 7.sp), Palette.White.copy(alpha = if (i == 0) 1f else 0.6f), maxLines = 1)
                    }
                }
            }
        }
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

/** The row above Watching that adds AXON's watchlist in one tap, while some of it is not watched yet. */
@Composable
fun WatchlistOffer(radar: Radar, lens: Lens, watched: Set<String>, onWatchAll: (Set<String>) -> Unit) {
    val ids = lens.watchIds.filter { radar.companyById.containsKey(it) }.toSet()
    val missing = ids - watched
    if (missing.isEmpty()) return
    Row(
        Modifier.padding(horizontal = 14.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(Palette.Black)
            .tap { onWatchAll(ids) }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            T("AXON'S WATCHLIST", Type.Caps, Palette.White, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            T("Watch all ${ids.size} companies AXON follows${if (missing.size < ids.size) " (${missing.size} not watched yet)" else ""}", Type.Row, Palette.White)
        }
        Spacer(Modifier.width(10.dp))
        Ico(Icons.Plus, Palette.White, 20.dp)
    }
}
