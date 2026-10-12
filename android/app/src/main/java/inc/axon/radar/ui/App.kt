package inc.axon.radar.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.drawToBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import inc.axon.radar.RadarWidget
import inc.axon.radar.data.Lens
import inc.axon.radar.data.LensResult
import inc.axon.radar.data.LensStore
import inc.axon.radar.data.Move
import inc.axon.radar.data.Radar
import inc.axon.radar.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A place a widget or notification can send the app to. */
data class Link(val dest: String, val id: String? = null) {
    companion object {
        const val EXTRA_DEST = "inc.axon.radar.DEST"
        const val EXTRA_ID = "inc.axon.radar.ID"
        fun from(i: Intent?): Link? = i?.getStringExtra(EXTRA_DEST)?.let { Link(it, i.getStringExtra(EXTRA_ID)) }
    }
}

/** Pages that open over the tabs. */
sealed interface Page {
    data class Moves(val ids: List<String>, val index: Int) : Page
    data class Company(val id: String) : Page
    data class Investor(val id: String) : Page
    /** The list of every company, to add to or remove from Watching. */
    data object Picker : Page
}

@Composable
fun RadarApp(
    link: MutableState<Link?>, preload: Radar? = null, autoRefresh: Boolean = true, startLook: Look? = null,
    startLayout: Layout? = null, preloadLens: Lens? = null,
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var look by remember { mutableStateOf(startLook ?: Store.look(ctx)) }
    // While the look changes, a picture of the old screen fades out over the new one.
    var before by remember { mutableStateOf<ImageBitmap?>(null) }
    val fade = remember { Animatable(0f) }

    fun switchLook(next: Look) {
        if (next == look) return
        before = runCatching { view.drawToBitmap().asImageBitmap() }.getOrNull()
        look = next
        Store.setLook(ctx, next)
        runCatching { RadarWidget.updateAll(ctx) }
        scope.launch {
            fade.snapTo(1f)
            fade.animateTo(0f, tween(560))
            before = null
        }
    }

    var layout by remember { mutableStateOf(startLayout ?: Store.layout(ctx)) }

    // The layout switches the same way the look does: a picture of the old screen fades over the new one.
    fun switchLayout(next: Layout) {
        if (next == layout) return
        before = runCatching { view.drawToBitmap().asImageBitmap() }.getOrNull()
        layout = next
        Store.setLayout(ctx, next)
        scope.launch {
            fade.snapTo(1f)
            fade.animateTo(0f, tween(560))
            before = null
        }
    }

    CompositionLocalProvider(LocalInk provides Ink.of(look)) {
        Box(Modifier.fillMaxSize()) {
            RadarContent(link, preload, autoRefresh, look, ::switchLook, layout, ::switchLayout, preloadLens)
            before?.let { Image(it, null, Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }, contentScale = ContentScale.FillBounds) }
        }
    }
}

@Composable
private fun RadarContent(
    link: MutableState<Link?>, preload: Radar?, autoRefresh: Boolean, look: Look, onLook: (Look) -> Unit,
    layout: Layout, onLayout: (Layout) -> Unit, preloadLens: Lens?,
) {
    val ctx = LocalContext.current
    val ink = LocalInk.current
    val scope = rememberCoroutineScope()
    var radar by remember { mutableStateOf(preload ?: Store.cached(ctx)) }
    var watched by remember { mutableStateOf(Store.watched(ctx)) }
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var section by rememberSaveable { mutableStateOf(Section.Moves) }
    var focus by rememberSaveable { mutableStateOf(0) }
    // The five areas and the page each one last showed.
    var area by rememberSaveable { mutableStateOf(Area.Today) }
    var todayList by rememberSaveable { mutableStateOf(false) }
    var rivalsAt by rememberSaveable { mutableStateOf(0) }
    var dealsAt by rememberSaveable { mutableStateOf(0) }
    var marketAt by rememberSaveable { mutableStateOf(0) }
    var axonAt by rememberSaveable { mutableStateOf(0) }
    // AXON's private view: read from the private repository with the key, kept sealed on the phone.
    var lens by remember { mutableStateOf(preloadLens ?: LensStore.cached(ctx)) }
    var lensBusy by remember { mutableStateOf(false) }
    var lensMsg by remember { mutableStateOf<String?>(null) }
    var lensFetched by remember { mutableStateOf(LensStore.fetchedAt(ctx)) }
    // The private view can be switched off on this phone without removing the key.
    var lensHidden by remember { mutableStateOf(Store.lensHidden(ctx)) }
    val viewLens = if (lensHidden) null else lens
    // Bumped when a widget asks for a given card, so the deck opens there.
    var deckKey by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var lastCheck by remember { mutableStateOf(0L) }
    val stack = remember { mutableStateListOf<Page>() }
    var pagerAt by remember { mutableStateOf(-1) }

    fun refresh() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { Store.refresh(ctx) } }
            ok.getOrNull()?.let { changed -> if (changed || radar == null) radar = withContext(Dispatchers.IO) { Store.cached(ctx) } }
            failed = ok.isFailure && radar == null
            if (preloadLens == null) {
                val lr = withContext(Dispatchers.IO) { runCatching { LensStore.refresh(ctx) }.getOrNull() }
                if (lr is LensResult.Updated) lens = lr.lens
                lensFetched = LensStore.fetchedAt(ctx)
            }
            lastCheck = System.currentTimeMillis()
            refreshing = false
        }
    }

    fun connectKey(key: String) {
        if (lensBusy) return
        lensBusy = true
        lensMsg = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { LensStore.connect(ctx, key) }
            if (r is LensResult.Updated) lens = r.lens
            lensMsg = LensStore.explain(r)
            lensFetched = LensStore.fetchedAt(ctx)
            lensBusy = false
        }
    }

    fun checkLens() {
        if (lensBusy) return
        lensBusy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { LensStore.refresh(ctx) }
            if (r is LensResult.Updated) lens = r.lens
            lensMsg = if (r is LensResult.Updated || r is LensResult.Unchanged) "Up to date: ${lens?.marks?.size ?: 0} marks." else LensStore.explain(r)
            lensFetched = LensStore.fetchedAt(ctx)
            lensBusy = false
        }
    }

    fun forgetKey() {
        LensStore.forget(ctx)
        lens = null
        lensFetched = null
        lensMsg = "The key and AXON's private view were removed from this phone."
    }

    // Check for news on start and whenever the app comes back after half an hour.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (autoRefresh && e == Lifecycle.Event.ON_RESUME && System.currentTimeMillis() - lastCheck > 30 * 60_000L) refresh()
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val r = radar
    // Status icons follow the page in view: dark on light pages, light on the deck and on black pages.
    val top = stack.lastOrNull()
    val pageBg = when {
        r == null -> null
        top is Page.Moves -> top.ids.getOrNull(if (pagerAt >= 0) pagerAt else top.index)?.let { r.moveById[it] }?.let { ink.page(it.type) }
        top is Page.Company -> ink.page(r.latestMove(top.id)?.type ?: "Partnership")
        top is Page.Investor -> ink.page("Funding")
        top is Page.Picker -> Palette.Paper
        else -> null
    }
    val onDeck = if (layout == Layout.Original) tab == Tab.Home else area == Area.Today && !todayList
    SystemBars(darkIcons = r != null && (if (top != null) ink.display(pageBg ?: Palette.Paper) == Palette.Black else !onDeck))
    LaunchedEffect(top) { pagerAt = -1 }
    if (r == null) {
        Loading(failed) { failed = false; refresh() }
        return
    }

    fun openMoves(list: List<Move>, i: Int) { if (list.isNotEmpty()) stack.add(Page.Moves(list.map { it.id }, i.coerceIn(0, list.lastIndex))) }

    LaunchedEffect(link.value, r) {
        val l = link.value ?: return@LaunchedEffect
        link.value = null
        stack.clear()
        if (layout == Layout.Original) when (l.dest) {
            "home" -> tab = Tab.Home
            "deck" -> {
                tab = Tab.Home
                focus = r.deck.indexOfFirst { it.id == l.id }.coerceAtLeast(0)
                deckKey++
            }
            "moves" -> { tab = Tab.Explore; section = Section.Moves }
            "companies" -> { tab = Tab.Explore; section = Section.Companies }
            "licences" -> { tab = Tab.Explore; section = Section.Licences }
            "trends" -> tab = Tab.Trends
            "watch" -> tab = Tab.Watch
            "about" -> tab = Tab.About
        } else when (l.dest) {
            // The same links land in the five areas: the deck in Today, lists where their questions now live.
            "home" -> { area = Area.Today; todayList = false }
            "deck" -> {
                area = Area.Today; todayList = false
                focus = todayDeck(r, viewLens).indexOfFirst { it.id == l.id }.coerceAtLeast(0)
                deckKey++
            }
            "moves" -> { area = Area.Today; todayList = true }
            "companies" -> { area = Area.Competitors; rivalsAt = 2 }
            "rivals" -> { area = Area.Competitors; rivalsAt = 0 }
            "map" -> { area = Area.Competitors; rivalsAt = 1 }
            "compare" -> { area = Area.Competitors; rivalsAt = 3 }
            "funding" -> { area = Area.Deals; dealsAt = 0 }
            "acquisitions" -> { area = Area.Deals; dealsAt = 1 }
            "partnerships" -> { area = Area.Deals; dealsAt = 2 }
            "investors" -> { area = Area.Deals; dealsAt = 3 }
            "licences" -> { area = Area.Market; marketAt = 1 }
            "trends" -> { area = Area.Market; marketAt = 0 }
            "watch" -> { area = Area.Axon; axonAt = 0 }
            "steps" -> { area = Area.Axon; axonAt = 1 }
            "about", "settings" -> { area = Area.Axon; axonAt = 2 }
        }
        when (l.dest) {
            "move" -> r.moveById[l.id]?.let { m ->
                val deck = if (layout == Layout.Original) r.deck else todayDeck(r, viewLens)
                val list = if (m in deck) deck else r.moves
                openMoves(list, list.indexOf(m))
            }
            "company" -> if (l.id != null && r.companyById.containsKey(l.id)) stack.add(Page.Company(l.id))
            "investor" -> if (l.id != null && r.investorById.containsKey(l.id)) stack.add(Page.Investor(l.id))
        }
    }

    val actions = PosterActions(
        close = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) },
        openUrl = { url -> runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
        openCompany = { id -> stack.add(Page.Company(id)) },
        openMoves = ::openMoves,
        toggleWatch = { id -> watched = if (id in watched) watched - id else watched + id; Store.setWatched(ctx, watched) },
        openInvestor = { id -> if (r.investorById.containsKey(id)) stack.add(Page.Investor(id)) },
    )

    BackHandler(enabled = stack.isNotEmpty()) { actions.close() }
    BackHandler(enabled = stack.isEmpty() && layout == Layout.Original && tab != Tab.Home) { tab = Tab.Home }
    BackHandler(enabled = stack.isEmpty() && layout == Layout.Areas && (area != Area.Today || todayList)) { area = Area.Today; todayList = false }

    // The private view shows only in the five areas; the original tabs stay as they were.
    val shownLens = if (layout == Layout.Areas) viewLens else null

    Box(Modifier.fillMaxSize()) {
        val bottom = 58.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        if (layout == Layout.Original) {
            when (tab) {
                Tab.Home -> key(deckKey) { Deck(r, focus, ::openMoves, onExplore = { tab = Tab.Explore }, onFocus = { focus = it }, bottomInset = bottom) }
                Tab.Explore -> Explore(r, section, { section = it }, ::openMoves, actions.openCompany, bottom)
                Tab.Watch -> WatchScreen(
                    r, watched, actions.openCompany, ::openMoves, { tab = Tab.Explore; section = Section.Companies },
                    onAdd = { stack.add(Page.Picker) }, onToggle = actions.toggleWatch, bottomInset = bottom,
                )
                Tab.Trends -> TrendsScreen(r, bottom)
                Tab.About -> AboutScreen(r, refreshing, ::refresh, { actions.openUrl(r.radarUrl) }, look, onLook, bottom, layout, onLayout)
            }
            TabBar(tab, dark = tab == Tab.Home, onTab = { tab = it }, modifier = Modifier.align(Alignment.BottomCenter))
        } else {
            val toKey = { area = Area.Axon; axonAt = 2 }
            when (area) {
                Area.Today -> TodayArea(r, viewLens, focus, deckKey, todayList, { todayList = it }, ::openMoves, { focus = it }, bottom)
                Area.Competitors -> CompetitorsArea(r, viewLens, rivalsAt, { rivalsAt = it }, actions.openCompany, toKey, bottom, actions.openUrl)
                Area.Deals -> DealsArea(r, dealsAt, { dealsAt = it }, ::openMoves, bottom, viewLens, actions.openInvestor)
                Area.Market -> MarketArea(r, marketAt, { marketAt = it }, ::openMoves, bottom)
                Area.Axon -> when (axonAt) {
                    0 -> WatchScreen(
                        r, watched, actions.openCompany, ::openMoves, { area = Area.Competitors; rivalsAt = 2 },
                        onAdd = { stack.add(Page.Picker) }, onToggle = actions.toggleWatch, bottomInset = bottom,
                        header = { Titles(AXON_SECTIONS, 0) { axonAt = it } },
                        offer = viewLens?.let { l -> { WatchlistOffer(r, l, watched) { ids -> watched = watched + ids; Store.setWatched(ctx, watched) } } },
                    )
                    1 -> AxonNextSteps(r, viewLens, { axonAt = it }, ::openMoves, toKey, bottom)
                    else -> AxonSettingsPage(
                        r, lens,
                        AxonSettings(
                            connected = lens != null, fetchedAt = lensFetched, busy = lensBusy, message = lensMsg,
                            onConnect = ::connectKey, onForget = ::forgetKey, onCheck = ::checkLens,
                            layout = layout, onLayout = onLayout, look = look, onLook = onLook,
                            refreshing = refreshing, onRefresh = ::refresh, onOpenWeb = { actions.openUrl(r.radarUrl) },
                            hidden = lensHidden, onHidden = { lensHidden = it; Store.setLensHidden(ctx, it) },
                            onOpenSources = { actions.openUrl(r.radarUrl + "#sources") },
                        ),
                        { axonAt = it }, bottom,
                    )
                }
            }
            AreaBar(area, dark = area == Area.Today && !todayList, onArea = { a -> if (a == Area.Today && area == Area.Today) todayList = false; area = a }, modifier = Modifier.align(Alignment.BottomCenter))
        }

        AnimatedContent(
            targetState = stack.lastOrNull(),
            transitionSpec = {
                if (targetState != null && (initialState == null || stack.size > 1)) slideInVertically(tween(320)) { it / 3 } + fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                else fadeIn(tween(160)) togetherWith slideOutVertically(tween(260)) { it / 3 } + fadeOut(tween(220))
            },
            label = "page",
        ) { page ->
            when (page) {
                null -> Box(Modifier)
                is Page.Moves -> MovePager(page.ids.mapNotNull { r.moveById[it] }, page.index, r, watched, actions, { if (page == stack.lastOrNull()) pagerAt = it }, shownLens)
                is Page.Company -> r.companyById[page.id]?.let { CompanyPoster(it, r, watched, actions, shownLens) }
                is Page.Investor -> r.investorById[page.id]?.let { InvestorPoster(it, r, actions, shownLens) }
                Page.Picker -> CompanyPicker(r, watched, actions.toggleWatch, actions.close)
            }
        }
    }
}

/** Dark status and navigation icons on light pages, light icons on the black deck. */
@Composable
private fun SystemBars(darkIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = darkIcons
            isAppearanceLightNavigationBars = false
        }
    }
}
