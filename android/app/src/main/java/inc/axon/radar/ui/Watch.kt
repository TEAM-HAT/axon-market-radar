@file:OptIn(ExperimentalFoundationApi::class)

package inc.axon.radar.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import inc.axon.radar.data.Company
import inc.axon.radar.data.Move
import inc.axon.radar.data.Radar
import inc.axon.radar.data.Text

private val BoxHeight = 176.dp

/**
 * The companies you follow, each as a box in the colour of its latest move, the colour its page opens
 * in. Tap a box for the company; tap Add companies to pick more. Their moves follow below.
 */
@Composable
fun WatchScreen(
    radar: Radar, watched: Set<String>, onOpenCompany: (String) -> Unit, onOpenMoves: (List<Move>, Int) -> Unit,
    onExplore: () -> Unit, onAdd: () -> Unit, onToggle: (String) -> Unit, bottomInset: Dp,
) {
    // Most recently active first, so the box that changed lately leads.
    val cos = radar.companies.filter { it.id in watched }.sortedByDescending { it.lastDate ?: "" }
    val moves = radar.moves.filter { m -> cos.any { it.id == m.companyId || it.id in m.related } }
    LazyColumn(Modifier.fillMaxSize().background(Palette.Paper), contentPadding = PaddingValues(bottom = bottomInset + 28.dp)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item { Titles(listOf("Watching", "Companies"), 0) { if (it == 1) onExplore() } }
        if (cos.isEmpty()) {
            item {
                T("Add the companies you follow. Each gets a box here with its latest move, and a tap opens its page. Your list stays on this phone.",
                    Type.Lead, Palette.Ink2, Modifier.padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 16.dp))
            }
        }
        val tiles: List<Company?> = cos + listOf(null)
        tiles.chunked(2).forEachIndexed { r, row ->
            item(key = "row-$r-" + row.joinToString { it?.id ?: "add" }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { c ->
                        if (c != null) CompanyBox(c, radar.latestMove(c.id), radar.movesFor(c.id).size, Modifier.weight(1f)) { onOpenCompany(c.id) }
                        else AddBox(radar.companies.size, Modifier.weight(1f), onAdd)
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (cos.isEmpty()) {
            // A head start: the companies moving most right now, one tap away.
            val since = radar.windowStart ?: ""
            val busy = radar.companies.sortedByDescending { c -> radar.movesFor(c.id).count { it.date >= since } * 100 + c.moves }.take(5)
            item { Heading("Most active now") }
            items(busy, key = { "s-" + it.id }) { c -> PickRow(c, radar.latestMove(c.id)?.type, c.id in watched) { onToggle(c.id) } }
        } else {
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

/** One company: its name, segment and country, the initials huge, and its latest move. */
@Composable
private fun CompanyBox(c: Company, latest: Move?, moves: Int, modifier: Modifier, onClick: () -> Unit) {
    val ink = LocalInk.current
    val bg = ink.page(latest?.type ?: "Partnership")
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier.height(BoxHeight).clip(shape).background(bg)
            .then(if (ink.dark(bg)) Modifier.border(1.dp, Tones.Edge, shape) else Modifier)
            .tap(onClick).padding(12.dp),
    ) {
        Column(Modifier.align(Alignment.TopStart)) {
            // The move count set small and raised after the name, as the filters show theirs.
            T(buildAnnotatedString {
                append(c.name)
                withStyle(SpanStyle(fontSize = 9.sp, baselineShift = BaselineShift(0.6f), color = ink.secondary(bg))) { append(" $moves") }
            }, Type.Name, ink.display(bg), maxLines = 2)
            Spacer(Modifier.height(2.dp))
            T(listOfNotNull(c.segment, c.hqCountry).joinToString(" · "), Type.Small, ink.secondary(bg), maxLines = 1)
        }
        Column(Modifier.align(Alignment.BottomStart)) {
            T(Text.initials(c.name), Type.display(58f), ink.display(bg), maxLines = 1)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (latest != null) {
                    Glyph(glyphOf(latest.type), ink.display(bg), 7.dp, hollow = ink.lined(latest.type))
                    Spacer(Modifier.width(5.dp))
                }
                T(latest?.let { "${it.typeLabel} · ${Text.day(it.date)}".uppercase() } ?: "NO MOVES YET", Type.Caps, ink.secondary(bg), maxLines = 1)
            }
        }
    }
}

/** The empty box at the end of the grid that opens the picker. */
@Composable
private fun AddBox(onRadar: Int, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier.height(BoxHeight).clip(shape).background(Palette.Row)
            .drawBehind {
                val w = 1.5.dp.toPx()
                drawRoundRect(
                    Palette.Black.copy(alpha = 0.35f), topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w), cornerRadius = CornerRadius(3.dp.toPx()),
                    style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))),
                )
            }
            .tap(onClick).padding(12.dp),
    ) {
        Box(Modifier.size(34.dp).background(Palette.Black, RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
            Ico(Icons.Plus, Palette.White, 20.dp)
        }
        Column(Modifier.align(Alignment.BottomStart)) {
            T("Add\ncompanies", Type.display(27f), Palette.Black)
            Spacer(Modifier.height(6.dp))
            T("$onRadar on the radar", Type.Small, Palette.Muted, maxLines = 1)
        }
    }
}

/**
 * Every company on the radar with a Watch switch, and a search field that matches names, segments
 * and countries. Changes apply at once.
 */
@Composable
fun CompanyPicker(radar: Radar, watched: Set<String>, onToggle: (String) -> Unit, onClose: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val list = rememberLazyListState()
    LaunchedEffect(list.isScrollInProgress) { if (list.isScrollInProgress) focus.clearFocus() }
    val q = query.trim()
    val shown = radar.companies.filter { c ->
        q.isEmpty() || listOfNotNull(c.name, c.segment, c.hqCountry, c.hqCity).any { it.contains(q, ignoreCase = true) }
    }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(Modifier.fillMaxSize().background(Palette.Paper).imePadding(), state = list, contentPadding = PaddingValues(bottom = bottom + 28.dp)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 6.dp), verticalAlignment = Alignment.Top) {
                T("Add companies", Type.display(46f), Palette.Black, Modifier.weight(1f))
                Box(Modifier.padding(top = 4.dp).size(40.dp).tap { focus.clearFocus(); onClose() }, contentAlignment = Alignment.Center) {
                    Ico(Icons.ChevronDown, Palette.Black, 26.dp)
                }
            }
        }
        item {
            val n = watched.count { id -> radar.companyById.containsKey(id) }
            T(if (n == 0) "Tap a company to watch it. Tap again to stop." else "Watching ${Text.plural(n, "company", "companies")}. Tap a company to add it or remove it.",
                Type.Small, Palette.Muted, Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp))
        }
        stickyHeader { SearchField(query, { query = it }, "Search ${radar.companies.size} companies") }
        items(shown, key = { it.id }) { c -> PickRow(c, radar.latestMove(c.id)?.type, c.id in watched) { onToggle(c.id) } }
        if (shown.isEmpty()) {
            item { T("No company on the radar matches “$q”.", Type.Row, Palette.Muted, Modifier.padding(16.dp)) }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, hint: String) {
    val focus = LocalFocusManager.current
    Box(Modifier.fillMaxWidth().background(Palette.Paper).padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp)) {
        BasicTextField(
            value, onChange, Modifier.fillMaxWidth(), singleLine = true,
            textStyle = Type.Row.copy(color = Palette.Black), cursorBrush = SolidColor(Palette.Black),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            decorationBox = { inner ->
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFF2F2F2)).padding(start = 12.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Ico(Icons.Search, Palette.Ink2, 18.dp)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) T(hint, Type.Row, Palette.Muted, maxLines = 1)
                        inner()
                    }
                    if (value.isNotEmpty()) {
                        Box(Modifier.size(36.dp).tap { onChange("") }, contentAlignment = Alignment.Center) { Ico(Icons.Close, Palette.Black, 16.dp) }
                    }
                }
            },
        )
    }
}

/** A company with its Watch switch on the right. The whole row toggles. */
@Composable
private fun PickRow(c: Company, latestType: String?, on: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 1.dp).clip(RoundedCornerShape(2.dp)).background(Palette.Row)
            .toggleable(on, remember { MutableInteractionSource() }, indication = null, role = Role.Checkbox) { onToggle() }
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompanyThumb(c.name, latestType)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            T(c.name, Type.Name, Palette.Black, maxLines = 1)
            T(listOfNotNull(c.segment, c.hqCountry).joinToString(" · "), Type.Small, Palette.Muted, maxLines = 1)
        }
        Spacer(Modifier.width(10.dp))
        val shape = RoundedCornerShape(3.dp)
        Row(
            Modifier.height(32.dp).clip(shape)
                .then(if (on) Modifier.background(Palette.Black) else Modifier.border(1.5.dp, Palette.Black, shape))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ico(if (on) Icons.Check else Icons.Plus, if (on) Palette.White else Palette.Black, 14.dp)
            Spacer(Modifier.width(5.dp))
            T(if (on) "WATCHING" else "WATCH", Type.Caps, if (on) Palette.White else Palette.Black, maxLines = 1)
        }
    }
}
