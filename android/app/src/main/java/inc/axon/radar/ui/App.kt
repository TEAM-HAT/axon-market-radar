package inc.axon.radar.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
}

@Composable
fun RadarApp(link: MutableState<Link?>, preload: Radar? = null, autoRefresh: Boolean = true) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var radar by remember { mutableStateOf(preload ?: Store.cached(ctx)) }
    var watched by remember { mutableStateOf(Store.watched(ctx)) }
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var section by rememberSaveable { mutableStateOf(Section.Moves) }
    var focus by rememberSaveable { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var lastCheck by remember { mutableStateOf(0L) }
    val stack = remember { mutableStateListOf<Page>() }

    fun refresh() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { Store.refresh(ctx) } }
            ok.getOrNull()?.let { changed -> if (changed || radar == null) radar = withContext(Dispatchers.IO) { Store.cached(ctx) } }
            failed = ok.isFailure && radar == null
            lastCheck = System.currentTimeMillis()
            refreshing = false
        }
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
    SystemBars(darkIcons = r != null && (stack.isNotEmpty() || tab != Tab.Home))
    if (r == null) {
        Loading(failed) { failed = false; refresh() }
        return
    }

    fun openMoves(list: List<Move>, i: Int) { if (list.isNotEmpty()) stack.add(Page.Moves(list.map { it.id }, i.coerceIn(0, list.lastIndex))) }

    LaunchedEffect(link.value, r) {
        val l = link.value ?: return@LaunchedEffect
        link.value = null
        stack.clear()
        when (l.dest) {
            "home" -> tab = Tab.Home
            "moves" -> { tab = Tab.Explore; section = Section.Moves }
            "companies" -> { tab = Tab.Explore; section = Section.Companies }
            "licences" -> { tab = Tab.Explore; section = Section.Licences }
            "trends" -> tab = Tab.Trends
            "watch" -> tab = Tab.Watch
            "about" -> tab = Tab.About
            "move" -> r.moveById[l.id]?.let { m ->
                val list = if (m in r.deck) r.deck else r.moves
                openMoves(list, list.indexOf(m))
            }
            "company" -> if (l.id != null && r.companyById.containsKey(l.id)) stack.add(Page.Company(l.id))
        }
    }

    val actions = PosterActions(
        close = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) },
        openUrl = { url -> runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
        openCompany = { id -> stack.add(Page.Company(id)) },
        openMoves = ::openMoves,
        toggleWatch = { id -> watched = if (id in watched) watched - id else watched + id; Store.setWatched(ctx, watched) },
    )

    BackHandler(enabled = stack.isNotEmpty()) { actions.close() }
    BackHandler(enabled = stack.isEmpty() && tab != Tab.Home) { tab = Tab.Home }

    Box(Modifier.fillMaxSize()) {
        val bottom = 58.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        when (tab) {
            Tab.Home -> Deck(r, focus, ::openMoves, onExplore = { tab = Tab.Explore }, onFocus = { focus = it }, bottomInset = bottom)
            Tab.Explore -> Explore(r, section, { section = it }, ::openMoves, actions.openCompany, bottom)
            Tab.Watch -> WatchScreen(r, watched, actions.openCompany, ::openMoves, { tab = Tab.Explore; section = Section.Companies }, bottom)
            Tab.Trends -> TrendsScreen(r, bottom)
            Tab.About -> AboutScreen(r, refreshing, ::refresh, { actions.openUrl(r.radarUrl) }, bottom)
        }
        TabBar(tab, dark = tab == Tab.Home, onTab = { tab = it }, modifier = Modifier.align(Alignment.BottomCenter))

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
                is Page.Moves -> MovePager(page.ids.mapNotNull { r.moveById[it] }, page.index, r, watched, actions)
                is Page.Company -> r.companyById[page.id]?.let { CompanyPoster(it, r, watched, actions) }
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
