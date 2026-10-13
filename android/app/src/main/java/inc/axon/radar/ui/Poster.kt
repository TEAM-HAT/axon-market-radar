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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
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
import inc.axon.radar.data.Investor
import inc.axon.radar.data.Investors
import inc.axon.radar.data.Lens
import inc.axon.radar.data.Party
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
    val openInvestor: (String) -> Unit = {},
)

/** Swipe sideways between moves; the next page slides over the one before, as in the inspiration. */
@Composable
fun MovePager(
    moves: List<Move>, start: Int, radar: Radar, watched: Set<String>, actions: PosterActions, onPage: (Int) -> Unit = {},
    lens: Lens? = null,
) {
    val state = rememberPagerState(initialPage = start.coerceIn(0, (moves.size - 1).coerceAtLeast(0))) { moves.size }
    LaunchedEffect(state) { snapshotFlow { state.currentPage }.collect(onPage) }
    HorizontalPager(state, Modifier.fillMaxSize().background(Palette.Black), beyondBoundsPageCount = 1) { page ->
        val offset = (state.currentPage - page) + state.currentPageOffsetFraction
        Box(Modifier.fillMaxSize().graphicsLayer {
            // The page leaving to the left drifts slower and dims a little, so the new colour seems to slide over it.
            if (offset > 0f) {
                translationX = size.width * offset * 0.65f
                alpha = lerp(1f, 0.55f, offset.coerceIn(0f, 1f))
            }
        }) {
            MovePoster(moves[page], radar, watched, actions, lens)
        }
    }
}

private val STATUS = mapOf("announced" to "Announced", "completed" to "Completed", "called off" to "Called off", "reported" to "Reported")

/** The deal details a move carries: round, valuation, investors, status, what the buyer gains, the parties and themes. */
private fun dealFacts(m: Move, radar: Radar): List<Pair<String, String>> {
    val others = m.parties.filter { it.role != "lead investor" && it.role != "investor" }
    return listOfNotNull(
        m.round?.stage?.let { "Round" to it },
        m.round?.valuation?.let { "Valuation" to (Text.money(it) ?: "") },
        m.round?.useOfFunds?.let { "For" to it.replaceFirstChar { c -> c.uppercase() } },
        m.deal?.status?.let { "Status" to (STATUS[it] ?: it) },
        m.deal?.valuation?.let { "Valuation" to (Text.money(it) ?: "") },
        m.deal?.rationale?.let { "Why" to it.replaceFirstChar { c -> c.uppercase() } },
        m.deal?.gains?.takeIf { it.isNotEmpty() }?.let { "Buyer gains" to it.joinToString(", ").replaceFirstChar { c -> c.uppercase() } },
        m.kind?.let { "Kind" to it },
        others.takeIf { it.isNotEmpty() }?.let { "Parties" to it.joinToString(", ") { p -> "${p.name} (${p.role})" } },
        m.themes.mapNotNull { t -> radar.themes.firstOrNull { it.id == t }?.name }.takeIf { it.isNotEmpty() }?.let { "Themes" to it.joinToString(", ") },
    )
}

@Composable
fun MovePoster(m: Move, radar: Radar, watched: Set<String>, actions: PosterActions, lens: Lens? = null) {
    val ink = LocalInk.current
    val bg = ink.page(m.type)
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
        meta = { Faces(involved.distinct(), ink.chip(bg), ink.reading(ink.chip(bg))); MetaText(listOfNotNull(Text.dayYear(m.date), m.region).joinToString(" · "), bg) },
        panel = { FigurePanel(bg, m.type, m.typeLabel, figure, caption, Text.long(m.date, m.precision)) },
        tile1 = Tile("Read the\nSource", Icons.ArrowOut, ink.tile(bg), ink.onTile(bg), enabled = m.sourceUrl != null) { m.sourceUrl?.let(actions.openUrl) },
        tile2 = if (company != null) {
            val on = company.id in watched
            Tile(if (on) "Watching\n${company.name}" else "Watch\nCompany", if (on) Icons.Check else Icons.Plus, ink.panel(bg), ink.onPanel(bg)) { actions.toggleWatch(company.id) }
        } else Tile("More from\n${m.regulator ?: m.typeLabel}", Icons.Grid, ink.panel(bg), ink.onPanel(bg)) {
            val list = radar.timelineFor(m); actions.openMoves(list, list.indexOf(m).coerceAtLeast(0))
        },
        label = m.typeLabel, lead = m.title,
        timeline = { Timeline(radar.timelineFor(m), m, bg) { list, i -> actions.openMoves(list, i) } },
        pdfName = "${m.subject} - ${m.typeLabel} - ${Text.dayYear(m.date)}", again = { MovePoster(m, radar, watched, actions, lens) },
    ) {
        T(m.summary, Type.Body.copy(fontSize = 17.sp, lineHeight = 25.sp), ink.reading(bg))
        lens?.mark(m.id)?.let { mk ->
            Spacer(Modifier.height(22.dp))
            AxonPanel(
                bg, "For AXON · ${mk.label}", mk.mark, mk.why,
                listOfNotNull(mk.next?.let { "Next step" to it }, mk.owner?.let { "Owner" to it }),
            )
        }
        Spacer(Modifier.height(22.dp))
        Facts(bg, listOfNotNull(
            company?.let { "Company" to it.name },
            m.related.mapNotNull { radar.companyById[it]?.name }.takeIf { it.isNotEmpty() }?.let { "Also involves" to it.joinToString(", ") },
            m.regulator?.let { "Regulator" to (it + (where?.let { w -> " · $w" } ?: "")) },
            m.jurisdiction?.let { "Jurisdiction" to it },
            m.region?.let { "Region" to it },
            "Date" to Text.long(m.date, m.precision),
        ) + dealFacts(m, radar) + listOfNotNull(
            m.sourceName?.let { "Source" to "$it · ${Text.host(m.sourceUrl)}" },
        ))
        val backers = (m.leads + m.investors).distinctBy { it.id }
        if (backers.isNotEmpty()) {
            Spacer(Modifier.height(26.dp))
            T("Investors", Type.display(30f), ink.display(bg))
            Spacer(Modifier.height(10.dp))
            backers.forEach { p -> InvestorLine(p, m.leads.any { it.id == p.id }, radar, bg, actions) }
            Rule(bg)
        }
        Spacer(Modifier.height(22.dp))
        if (m.sourceUrl != null) BigButton("Read the source", Icons.ArrowOut, bg = bg) { actions.openUrl(m.sourceUrl) }
        if (company != null) {
            Spacer(Modifier.height(8.dp))
            BigButton("Open ${company.name}", Icons.ChevronLeft, ghost = true, bg = bg) { actions.openCompany(company.id) }
        }
    }
}

/** Which of AXON's markets a licence counts in, from its regulator and jurisdiction. */
fun licenceMarket(l: inc.axon.radar.data.Licence): String {
    val s = listOfNotNull(l.key, l.regulator, l.jurisdiction, l.type).joinToString(" ").uppercase()
    return when {
        Regex("VARA|ADGM|FSRA|DFSA|CBUAE|UAE|DUBAI|ABU DHABI").containsMatchIn(s) -> "UAE"
        Regex("SAMA|\\bCMA\\b|SAUDI").containsMatchIn(s) -> "Saudi Arabia"
        Regex("\\bCBB\\b|BAHRAIN").containsMatchIn(s) -> "Bahrain"
        Regex("\\bFCA\\b|UNITED KINGDOM|\\bUK\\b").containsMatchIn(s) -> "UK"
        Regex("FINTRAC|\\bCSA\\b|\\bOSC\\b|CIRO|FCAC|CANADA|ONTARIO|QUEBEC|ALBERTA|BRITISH COLUMBIA").containsMatchIn(s) -> "Canada"
        Regex("NYDFS|\\bOCC\\b|FINCEN|FEDERAL RESERVE|FDIC|UNITED STATES|\\bUSA?\\b|NEW YORK|STATE").containsMatchIn(s) -> "US"
        Regex("MICA|ESMA|CSSF|\\bAFM\\b|\\bDNB\\b|MFSA|BAFIN|\\bAMF\\b|\\bCBI\\b|LITHUANIA|CYSEC|FIN-FSA|EEA|LUXEMBOURG|NETHERLANDS|MALTA|GERMANY|FRANCE|IRELAND|CYPRUS|AUSTRIA|FINLAND|SPAIN|ITALY|BELGIUM|POLAND|EU\\b").containsMatchIn(s) -> "EU"
        else -> "Elsewhere"
    }
}

@Composable
fun CompanyPoster(c: Company, radar: Radar, watched: Set<String>, actions: PosterActions, lens: Lens? = null) {
    val moves = radar.movesFor(c.id)
    val latest = moves.firstOrNull()
    val ink = LocalInk.current
    val bg = ink.page(latest?.type ?: "Partnership")
    val on = c.id in watched
    val stance = lens?.stance(c.id)
    val rounds = radar.moves.filter { it.type == "Funding" && it.companyId == c.id }
    val deals = radar.moves.filter { it.type == "M&A" && (it.companyId == c.id || it.parties.any { p -> p.id == c.id }) }
    val partners = radar.moves.filter { it.type == "Partnership" && (it.companyId == c.id || it.parties.any { p -> p.id == c.id } || c.id in it.related) }
    Poster(
        bg = bg, title = c.name, onClose = actions.close,
        meta = {
            Faces(listOf(c.name) + c.licences.mapNotNull { it.key ?: it.regulator }.distinct().take(2), ink.chip(bg), ink.reading(ink.chip(bg)))
            MetaText(listOf(Text.plural(moves.size, "move", "moves"), Text.plural(c.licences.size, "licence", "licences")).joinToString(" · "), bg)
        },
        panel = {
            FigurePanel(bg, latest?.type, c.segment ?: "Company", Text.initials(c.name),
                listOfNotNull(c.hqCity, c.hqCountry).joinToString(", ").ifEmpty { c.region ?: "" },
                latest?.let { "Latest move · ${Text.dayYear(it.date)}" } ?: "")
        },
        tile1 = Tile("Latest\nMove", Icons.ArrowOut, ink.tile(bg), ink.onTile(bg), enabled = latest != null) { if (latest != null) actions.openMoves(moves, 0) },
        tile2 = Tile(if (on) "Watching" else "Watch\nCompany", if (on) Icons.Check else Icons.Plus, ink.panel(bg), ink.onPanel(bg)) { actions.toggleWatch(c.id) },
        label = c.segment ?: "About", lead = c.blurb ?: "",
        timeline = { Timeline(moves, null, bg) { list, i -> actions.openMoves(list, i) } },
        pdfName = c.name, again = { CompanyPoster(c, radar, watched, actions, lens) },
    ) {
        if (stance != null) {
            AxonPanel(
                bg, "For AXON", null, stance.note.ifBlank { stanceLine(stance) },
                listOfNotNull(
                    "Role" to stance.roles.joinToString(", ") { Lens.roleLabel(it) },
                    "Threat" to Lens.threatLabel(stance.threat),
                    stance.markets.takeIf { it.isNotEmpty() }?.let { "Matters in" to it.joinToString(", ") },
                ),
            )
            Spacer(Modifier.height(26.dp))
        }
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
            T("Licences", Type.display(30f), ink.display(bg))
            Spacer(Modifier.height(10.dp))
            // Grouped by AXON's markets, so the ones that matter to AXON read first.
            val byMarket = c.licences.groupBy { licenceMarket(it) }
            (inc.axon.radar.data.Lens.MARKETS + "Elsewhere").filter { it in byMarket }.forEach { market ->
                Rule(bg)
                T(market.uppercase(), Type.Caps, ink.display(bg), Modifier.padding(top = 12.dp, bottom = 2.dp))
                byMarket.getValue(market).forEach { l ->
                    Column(Modifier.padding(vertical = 7.dp)) {
                        T(listOfNotNull(l.key ?: l.regulator, l.jurisdiction, l.year?.toString()).joinToString(" · "), Type.Caps, ink.secondary(bg))
                        Spacer(Modifier.height(3.dp))
                        T(l.type ?: "", Type.Row, ink.reading(bg))
                    }
                }
            }
        }
        if (rounds.isNotEmpty()) {
            Spacer(Modifier.height(26.dp))
            T("Money raised", Type.display(30f), ink.display(bg))
            Spacer(Modifier.height(10.dp))
            rounds.forEach { m ->
                Rule(bg)
                Row(Modifier.fillMaxWidth().tap { actions.openMoves(rounds, rounds.indexOf(m)) }.padding(vertical = 11.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        T(listOfNotNull(m.round?.stage, Text.dayYear(m.date).takeIf { m.precision != "month" } ?: Text.monthFull(m.date)).joinToString(" · ").uppercase(), Type.Caps, ink.secondary(bg))
                        Spacer(Modifier.height(3.dp))
                        T(listOfNotNull(
                            m.leads.takeIf { it.isNotEmpty() }?.let { "Led by " + it.joinToString(", ") { p -> p.name } },
                            m.investors.takeIf { it.isNotEmpty() }?.let { "with " + it.take(6).joinToString(", ") { p -> p.name } + if (it.size > 6) " and ${it.size - 6} more" else "" },
                        ).joinToString(", ").ifEmpty { m.title }, Type.Row, ink.reading(bg))
                        m.round?.valuation?.let { v -> Spacer(Modifier.height(2.dp)); T("Valued at ${Text.money(v)}", Type.Small, ink.secondary(bg)) }
                    }
                    Spacer(Modifier.width(10.dp))
                    T(Text.money(m.amount) ?: "Undisclosed", Type.Name, ink.reading(bg))
                }
            }
            val backers = rounds.flatMap { it.leads + it.investors }.distinctBy { it.id }
            if (backers.isNotEmpty()) {
                Spacer(Modifier.height(26.dp))
                T("Backers", Type.display(30f), ink.display(bg))
                Spacer(Modifier.height(10.dp))
                backers.forEach { p -> InvestorLine(p, rounds.any { r -> r.leads.any { it.id == p.id } }, radar, bg, actions) }
            }
        }
        if (deals.isNotEmpty()) {
            Spacer(Modifier.height(26.dp))
            T("Deals", Type.display(30f), ink.display(bg))
            Spacer(Modifier.height(10.dp))
            deals.forEach { m ->
                Rule(bg)
                Row(Modifier.fillMaxWidth().tap { actions.openMoves(deals, deals.indexOf(m)) }.padding(vertical = 11.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        T(listOfNotNull(m.deal?.status?.let { STATUS[it] ?: it }, Text.dayYear(m.date)).joinToString(" · ").uppercase(), Type.Caps, ink.secondary(bg))
                        Spacer(Modifier.height(3.dp))
                        T(m.title, Type.Row, ink.reading(bg))
                    }
                    Text.money(m.amount)?.let { Spacer(Modifier.width(10.dp)); T(it, Type.Name, ink.reading(bg)) }
                }
            }
        }
        if (partners.isNotEmpty()) {
            Spacer(Modifier.height(26.dp))
            T("Partners", Type.display(30f), ink.display(bg))
            Spacer(Modifier.height(10.dp))
            partners.forEach { m ->
                val with = (m.parties.filter { it.id != c.id }.map { it.name } + m.related.filter { it != c.id }.mapNotNull { radar.companyById[it]?.name } +
                    listOfNotNull(m.companyName.takeIf { m.companyId != c.id })).distinct()
                Rule(bg)
                Column(Modifier.fillMaxWidth().tap { actions.openMoves(partners, partners.indexOf(m)) }.padding(vertical = 11.dp)) {
                    T(listOfNotNull(m.kind, Text.dayYear(m.date)).joinToString(" · ").uppercase(), Type.Caps, ink.secondary(bg))
                    Spacer(Modifier.height(3.dp))
                    T(if (with.isNotEmpty()) "With " + with.joinToString(", ") else m.title, Type.Row, ink.reading(bg))
                }
            }
        }
        if (moves.isNotEmpty()) {
            Spacer(Modifier.height(26.dp))
            T("Moves", Type.display(30f), ink.display(bg))
            Spacer(Modifier.height(10.dp))
            moves.forEachIndexed { i, m ->
                Rule(bg)
                Row(Modifier.fillMaxWidth().tap { actions.openMoves(moves, i) }.padding(vertical = 11.dp), verticalAlignment = Alignment.Top) {
                    Swatch(m.type, ink.swatch(m.type, bg), 22.dp, 8.dp, ink.edge(bg, 0.25f))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        T("${m.typeLabel.uppercase()} · ${Text.dayYear(m.date).uppercase()}", Type.Caps, ink.secondary(bg))
                        Spacer(Modifier.height(3.dp))
                        T(m.title, Type.Row, ink.reading(bg))
                        lens?.mark(m.id)?.takeIf { it.mark != "none" }?.let { mk ->
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MarkSign(mk.mark, ink.reading(bg), 8.dp)
                                Spacer(Modifier.width(5.dp))
                                T("FOR AXON · ${mk.label.uppercase()}", Type.Caps, ink.reading(bg), maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        if (c.sources.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            T("Profile sources: " + c.sources.joinToString(" · ") { Text.host(it) }, Type.Small, ink.secondary(bg))
        }
    }
}

/** An investor on a move or company page: its name, whether it led, how active it is on the radar; a tap opens its page. */
@Composable
private fun InvestorLine(p: Party, led: Boolean, radar: Radar, bg: Color, actions: PosterActions) {
    val ink = LocalInk.current
    val v = radar.investorById[p.id]
    Rule(bg)
    Row(Modifier.fillMaxWidth().then(if (v != null) Modifier.tap { actions.openInvestor(p.id) } else Modifier).padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            T(listOfNotNull(if (led) "Lead investor" else "Investor", v?.let { Text.plural(it.rounds.size, "round", "rounds") + " on the radar" }).joinToString(" · ").uppercase(), Type.Caps, ink.secondary(bg))
            Spacer(Modifier.height(3.dp))
            T(p.name, Type.Row, ink.reading(bg))
        }
        if (v != null) Ico(Icons.ChevronRight, ink.reading(bg), 18.dp)
    }
}

/** An investor's page: what it backed on the radar, every round it joined, and who it invests with most. */
@Composable
fun InvestorPoster(v: Investor, radar: Radar, actions: PosterActions, lens: Lens? = null) {
    val ink = LocalInk.current
    val bg = ink.page("Funding")
    val latest = v.rounds.first()
    val first = v.rounds.last()
    val company = v.company
    Poster(
        bg = bg, title = v.name, onClose = actions.close,
        meta = {
            Faces(v.portfolio.take(3).map { it.name }.ifEmpty { listOf(v.name) }, ink.chip(bg), ink.reading(ink.chip(bg)))
            MetaText(listOfNotNull(Text.plural(v.rounds.size, "round", "rounds"), v.led.size.takeIf { it > 0 }?.let { "led $it" }).joinToString(" · "), bg)
        },
        panel = {
            FigurePanel(
                bg, "Funding", if (company != null) "Investor" else Investors.kindLabel(v), v.total?.let { Text.money(it) } ?: v.rounds.size.toString(),
                if (v.total != null) "raised in the rounds it joined" else if (v.rounds.size == 1) "round" else "rounds",
                "Latest round · ${Text.dayYear(latest.date)}",
            )
        },
        tile1 = Tile("Latest\nRound", Icons.ArrowOut, ink.tile(bg), ink.onTile(bg)) { actions.openMoves(v.rounds, 0) },
        tile2 = when {
            company != null -> Tile("Open\n${company.name}", Icons.ChevronRight, ink.panel(bg), ink.onPanel(bg)) { actions.openCompany(company.id) }
            v.website != null -> Tile("Website\n${Text.host(v.website)}", Icons.ArrowOut, ink.panel(bg), ink.onPanel(bg)) { actions.openUrl(v.website) }
            else -> Tile("Every\nRound", Icons.Grid, ink.panel(bg), ink.onPanel(bg)) { actions.openMoves(v.rounds, 0) }
        },
        label = Investors.kindLabel(v), lead = Investors.summary(v),
        timeline = { Timeline(v.rounds, null, bg) { list, i -> actions.openMoves(list, i) } },
        pdfName = v.name, again = { InvestorPoster(v, radar, actions, lens) },
    ) {
        val rivals = Investors.rivalsBacked(v, lens)
        if (rivals.isNotEmpty() && lens != null) {
            AxonPanel(
                bg, "For AXON", null,
                (if (rivals.size == 1) "Backs one of AXON's rivals: " else "Backs ${rivals.size} of AXON's rivals, the biggest threats first: ") +
                    rivals.joinToString(", ") { h -> "${h.name} (${lens.stance(h.companyId)?.threat ?: "none"})" } + ".",
                listOf("Rivals backed" to rivals.size.toString(), "Led their rounds" to rivals.count { it.led }.toString()),
            )
            Spacer(Modifier.height(26.dp))
        }
        Facts(bg, listOfNotNull(
            "Kind" to Investors.kindLabel(v),
            v.country?.let { "Headquarters" to it },
            "Rounds" to (v.rounds.size.toString() + if (v.led.isNotEmpty()) ", ${v.led.size} as lead" else ""),
            v.total?.let { "Raised in them" to "${Text.money(it)}, in the rounds with a disclosed size" },
            if (latest === first) ("Round" to Text.long(latest.date, latest.precision)) else ("First and latest" to "${Text.dayYear(first.date)} and ${Text.dayYear(latest.date)}"),
            v.website?.let { "Website" to Text.host(it) },
        ))
        Spacer(Modifier.height(26.dp))
        T("Portfolio", Type.display(30f), ink.display(bg))
        Spacer(Modifier.height(10.dp))
        v.portfolio.forEach { h ->
            val m = h.rounds.first()
            val st = lens?.stance(h.companyId)
            Rule(bg)
            Row(
                Modifier.fillMaxWidth().tap { if (h.companyId != null) actions.openCompany(h.companyId) else actions.openMoves(h.rounds, 0) }.padding(vertical = 11.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    T(listOfNotNull(m.round?.stage, Text.dayYear(m.date)).joinToString(" · ").uppercase(), Type.Caps, ink.secondary(bg))
                    Spacer(Modifier.height(3.dp))
                    T(h.name + (if (h.led) " · led" else "") + (if (h.rounds.size > 1) " · ${h.rounds.size} rounds" else ""), Type.Row, ink.reading(bg))
                    if (st != null && st.rival) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MarkSign("threat", ink.reading(bg), 8.dp)
                            Spacer(Modifier.width(5.dp))
                            T("AXON RIVAL · ${Lens.threatLabel(st.threat).uppercase()}", Type.Caps, ink.reading(bg), maxLines = 1)
                        }
                    }
                }
                Text.money(m.amount)?.let { Spacer(Modifier.width(10.dp)); T(it, Type.Name, ink.reading(bg)) }
            }
        }
        Spacer(Modifier.height(26.dp))
        T("Rounds", Type.display(30f), ink.display(bg))
        Spacer(Modifier.height(10.dp))
        v.rounds.forEachIndexed { i, m ->
            Rule(bg)
            Row(Modifier.fillMaxWidth().tap { actions.openMoves(v.rounds, i) }.padding(vertical = 11.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    T(listOfNotNull(m.round?.stage, Text.dayYear(m.date)).joinToString(" · ").uppercase(), Type.Caps, ink.secondary(bg))
                    Spacer(Modifier.height(3.dp))
                    T((m.companyName ?: m.title) + if (m.id in v.led) " · led" else "", Type.Row, ink.reading(bg))
                }
                Spacer(Modifier.width(10.dp))
                T(Text.money(m.amount) ?: "Undisclosed", Type.Name, ink.reading(bg))
            }
        }
        if (v.with.isNotEmpty()) {
            Spacer(Modifier.height(26.dp))
            T("Invests alongside", Type.display(30f), ink.display(bg))
            Spacer(Modifier.height(10.dp))
            v.with.take(10).forEach { w ->
                Rule(bg)
                Row(Modifier.fillMaxWidth().tap { actions.openInvestor(w.id) }.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T(Text.plural(w.rounds, "round together", "rounds together").uppercase(), Type.Caps, ink.secondary(bg))
                        Spacer(Modifier.height(3.dp))
                        T(w.name, Type.Row, ink.reading(bg))
                    }
                    Ico(Icons.ChevronRight, ink.reading(bg), 18.dp)
                }
            }
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
    pdfName: String, again: @Composable () -> Unit,
    details: @Composable ColumnScope.() -> Unit,
) {
    val ink = LocalInk.current
    // On paper the page is laid out in full, without its close button.
    val printing = LocalPrint.current != null
    val pdf = LocalPdf.current
    BoxWithConstraints(if (printing) Modifier.fillMaxWidth().background(bg) else Modifier.fillMaxSize().background(bg)) {
        val content = maxWidth - 28.dp
        Column(if (printing) Modifier.padding(horizontal = 14.dp) else Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
            if (printing) Spacer(Modifier.height(10.dp)) else Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                FitTitle(title, ink.display(bg), Modifier.weight(1f), maxSize = 60f, minSize = 30f, maxLines = 2)
                Box(Modifier.padding(top = 6.dp, start = 8.dp).size(36.dp).then(if (printing) Modifier else Modifier.tap(onClose)), contentAlignment = Alignment.TopEnd) {
                    if (!printing) Ico(Icons.ChevronDown, ink.display(bg), 26.dp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { meta() }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().height(content * 0.66f).keepWhole()) {
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
                T(label, Type.Small, ink.secondary(bg), Modifier.weight(0.3f).padding(top = 4.dp))
                T(lead, Type.Lead, ink.reading(bg), Modifier.weight(0.7f))
            }
            Spacer(Modifier.height(26.dp))
            Box(Modifier.keepWhole()) { timeline() }
            Spacer(Modifier.height(30.dp))
            details()
            if (pdf != null && !printing) {
                Spacer(Modifier.height(20.dp))
                PdfLink(ink.reading(bg)) { pdf.save(pdfName, bg) { again() } }
            }
            Spacer(Modifier.height(if (printing) 20.dp else 32.dp))
            if (!printing) Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun MetaText(text: String, bg: Color) {
    Spacer(Modifier.width(10.dp))
    T(text, Type.Small, LocalInk.current.secondary(bg), maxLines = 1)
}

/**
 * The block that stands in for the inspiration's photograph: one big figure in the page colour. Black on
 * every colour page; in black and white it turns white on the black pages and carries fine lines for
 * the second kind in each family.
 */
@Composable
fun FigurePanel(bg: Color, type: String?, label: String, figure: String, caption: String, footer: String) {
    val ink = LocalInk.current
    val lined = ink.lined(type)
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(3.dp)).background(ink.panel(bg))
            .then(if (lined) Modifier.lines(ink.panelLines(bg), gap = 6.dp) else Modifier).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (type != null) { Glyph(glyphOf(type), bg, 9.dp, hollow = lined); Spacer(Modifier.width(7.dp)) }
            T(label.uppercase(), Type.Caps, bg, maxLines = 1)
        }
        Column(Modifier.align(Alignment.BottomStart)) {
            FitTitle(figure, bg, Modifier.fillMaxWidth(), maxSize = 84f, minSize = 30f, maxLines = 1)
            if (caption.isNotBlank()) { Spacer(Modifier.height(4.dp)); T(caption, Type.Tile, ink.onPanel(bg), maxLines = 1) }
            if (footer.isNotBlank()) { Spacer(Modifier.height(10.dp)); T(footer, Type.Small, ink.onPanel(bg).copy(alpha = 0.55f), maxLines = 1) }
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
    val ink = LocalInk.current
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
                        Swatch(
                            m.type, if (isCur) ink.panel(bg) else ink.swatch(m.type, bg), 22.dp, 7.dp,
                            ink.edge(bg, if (isCur) 1f else 0.28f), Modifier.padding(top = 3.dp).tap { onOpen(moves, i) },
                            glyphColor = if (isCur) bg else null, lined = !isCur && ink.lined(m.type),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                val strong = ym == curMonth
                T(Text.monthYear(ym).take(1), Type.Small.copy(fontWeight = if (strong) FontWeight.ExtraBold else FontWeight.Medium),
                    if (strong) ink.reading(bg) else ink.secondary(bg))
            }
        }
    }
}

@Composable
private fun Facts(bg: Color, rows: List<Pair<String, String>>) {
    val ink = LocalInk.current
    rows.forEach { (k, v) ->
        Rule(bg)
        Row(Modifier.padding(vertical = 10.dp)) {
            T(k, Type.Small, ink.secondary(bg), Modifier.weight(0.3f).padding(top = 2.dp))
            T(v, Type.Row, ink.reading(bg), Modifier.weight(0.7f))
        }
    }
    Rule(bg)
}

@Composable
private fun Rule(bg: Color) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalInk.current.reading(bg).copy(alpha = 0.22f)))
}

@Composable
private fun BigButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, ghost: Boolean = false, bg: Color, onClick: () -> Unit) {
    val ink = LocalInk.current
    val fill = if (ghost) ink.tile(bg) else ink.panel(bg)
    val fg = if (ghost) ink.onTile(bg) else ink.onPanel(bg)
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(3.dp)).background(fill).tap(onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(text, Type.Tab, fg, Modifier.weight(1f), maxLines = 1)
        Ico(icon, fg, 18.dp, if (ghost) Modifier.graphicsLayer { rotationZ = 180f } else Modifier)
    }
}

