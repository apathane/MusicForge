package dev.musicforge.ui

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.musicforge.data.prefs.Keys
import dev.musicforge.graph
import dev.musicforge.ui.player.MiniPlayer
import dev.musicforge.ui.player.PlayerSheet
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.musicforge.ui.components.ForgeIcons
import dev.musicforge.ui.components.GlassStyle
import dev.musicforge.ui.backdrop.backdrops.layerBackdrop
import dev.musicforge.ui.components.GlassTier
import dev.musicforge.ui.theme.Ambience
import dev.musicforge.ui.components.LocalGlassStyle
import dev.musicforge.ui.components.glass
import androidx.navigation.NavType
import androidx.navigation.navArgument
import dev.musicforge.ui.screens.ArtistScreen
import dev.musicforge.ui.screens.CollectionScreen
import dev.musicforge.ui.screens.CustomiseHomeScreen
import dev.musicforge.ui.screens.DownloadsScreen
import dev.musicforge.ui.screens.LibraryAlbumsScreen
import dev.musicforge.ui.screens.LoginScreen
import dev.musicforge.ui.screens.LibraryArtistsScreen
import dev.musicforge.ui.LocalSort
import dev.musicforge.ui.screens.LocalMusicScreen
import dev.musicforge.ui.home.HomeRoot
import dev.musicforge.ui.screens.LibraryScreen
import dev.musicforge.ui.screens.SearchScreen
import dev.musicforge.ui.screens.SettingsScreen

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", ForgeIcons.Home),
    Search("search", "Search", ForgeIcons.Search),
    Library("library", "Library", ForgeIcons.Library),
    Downloads("downloads", "Downloads", ForgeIcons.Download),
}

const val SETTINGS_ROUTE = "settings"

/** Space screens leave at the bottom so content can scroll out from under the nav pill. */
val LocalBottomInset = staticCompositionLocalOf { PaddingValues(bottom = 96.dp) }

val LocalNavController = staticCompositionLocalOf<NavHostController> { error("no nav controller") }

/** When true, the top bar (scrim + screen headers) collapses to just the status-bar inset. */
val LocalHideTopBar = staticCompositionLocalOf { false }

@Composable
fun ForgeRoot(glassTier: GlassTier, hideTopBar: Boolean = false, ambience: Ambience) {
    val nav = rememberNavController()
    val windowWidth = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width.coerceAtLeast(1)
    // Liquid needs RenderEffect (API 31); below that it falls back to the artwork slice.
    val liquid = glassTier == GlassTier.LIQUID && dev.musicforge.ui.backdrop.isRenderEffectSupported()
    val canvas = MaterialTheme.colorScheme.background
    // The root Box paints the canvas, not the screens, so the recording paints it too:
    // otherwise the glass would refract a transparent hole wherever a screen draws nothing.
    val live = if (liquid) {
        dev.musicforge.ui.backdrop.backdrops.rememberLayerBackdrop(onDraw = remember(canvas) { { drawRect(canvas); drawContent() } })
    } else null
    val glass = GlassStyle(
        tier = glassTier,
        backdrop = ambience.backdrop.takeIf { glassTier != GlassTier.SOLID },
        backdropScale = (ambience.backdrop?.width ?: 54).toFloat() / windowWidth,
        live = live,
    )
    val context = LocalContext.current
    val connection = context.graph.player
    val now by connection.nowPlaying.collectAsStateWithLifecycle()
    val inset = PaddingValues(bottom = if (now != null) 172.dp else 96.dp)
    var playerOpen by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var covered by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    // Which tab the nav host opens on (Metrolist's defaultOpenTab key).
    val prefs by context.graph.settings.data.collectAsStateWithLifecycle(initialValue = null)
    val keepScreenOn = prefs?.get(Keys.KeepScreenOn) == true
    // The start tab is applied once, by navigating like a tab tap: changing NavHost's
    // startDestination after the first frame would rebuild the graph and drop the back stack.
    // Metrolist stores the tab as its enum name ("LIBRARY"), we as the route ("library"): take both.
    var startApplied by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(prefs != null) {
        val raw = prefs?.get(Keys.StartScreen) ?: return@LaunchedEffect
        if (startApplied) return@LaunchedEffect
        startApplied = true
        val tab = Tab.entries.firstOrNull { it.route.equals(raw, true) || it.name.equals(raw, true) } ?: return@LaunchedEffect
        if (tab != Tab.Home) nav.navigate(tab.route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    // enableEdgeToEdge picks icon colours from the *system* theme; follow what we paint instead.
    val lightContent = MaterialTheme.colorScheme.background.luminance() > 0.5f && !playerOpen
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        // FLAG_KEEP_SCREEN_ON is window-scoped: it follows the sheet open/close automatically,
        // no need to toggle in onStart/onStop.
        if (keepScreenOn && playerOpen) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightContent
            isAppearanceLightNavigationBars = lightContent
        }
    }
    CompositionLocalProvider(LocalGlassStyle provides glass, LocalNavController provides nav, LocalBottomInset provides inset, LocalHideTopBar provides hideTopBar) {
        // Under a fully open player the screen behind is never seen: don't paint it at all.
        Box(Modifier.fillMaxSize().then(if (covered) Modifier else Modifier.background(MaterialTheme.colorScheme.background))) {
            // Under a fully open player nothing here is visible; an alpha-0 layer is skipped by the renderer.
            NavHost(
                nav,
                startDestination = Tab.Home.route,
                // Pages push in from the side a little; tabs, which aren't a hierarchy, only crossfade.
                // Navigation's default is a 700 ms fade, slow enough to feel like lag.
                // Spatial: forward pushes slide in from the right / slide out to the left,
                // back pops come in from the left / out to the right. Tab switches crossfade only.
                // Pop exit always animates — the surface must never vanish on back.
                enterTransition = {
                    if (targetState.destination.route in tabRoutes) fadeIn(tween(160))
                    else fadeIn(tween(220)) + slideInHorizontally(tween(260, easing = EaseOutCubic)) { it / 10 }
                },
                exitTransition = {
                    if (targetState.destination.route in tabRoutes) fadeOut(tween(140))
                    else fadeOut(tween(140)) + slideOutHorizontally(tween(200, easing = EaseInCubic)) { -it / 10 }
                },
                popEnterTransition = {
                    if (initialState.destination.route in tabRoutes) fadeIn(tween(180))
                    else fadeIn(tween(200)) + slideInHorizontally(tween(260, easing = EaseOutCubic)) { -it / 10 }
                },
                popExitTransition = {
                    if (initialState.destination.route in tabRoutes) fadeOut(tween(160))
                    else fadeOut(tween(160)) + slideOutHorizontally(tween(240, easing = EaseInCubic)) { it / 10 }
                },
                modifier = Modifier.graphicsLayer { alpha = if (covered) 0f else 1f }
                    .then(if (live != null) Modifier.layerBackdrop(live) else Modifier),
            ) {
                composable(Tab.Home.route) { HomeRoot() }
                composable(Tab.Search.route) { SearchScreen() }
                composable(Tab.Library.route) { LibraryScreen(onOpenSettings = { nav.navigate(SETTINGS_ROUTE) }) }
                composable(Tab.Downloads.route) { DownloadsScreen() }
                composable(SETTINGS_ROUTE) { SettingsScreen(
                        onBack = { nav.popBackStack() },
                        onSignIn = { nav.navigate(Routes.LOGIN) },
                        onAbout = { nav.navigate(Routes.ABOUT) },
                        onEqualizer = { nav.navigate(Routes.EQUALIZER) },
                        onTaste = { nav.navigate(Routes.TASTE) },
                        onCustomiseHome = { nav.navigate(Routes.CUSTOMISE_HOME) },
                    ) }
                composable(Routes.CUSTOMISE_HOME) { CustomiseHomeScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.LOGIN) { LoginScreen(onDone = { nav.popBackStack() }) }
                composable(Routes.EQUALIZER) { dev.musicforge.ui.screens.EqualizerScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.TASTE) { dev.musicforge.ui.screens.TasteScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.STATS) { dev.musicforge.ui.screens.StatsScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.ABOUT) { dev.musicforge.ui.screens.AboutScreen(onBack = { nav.popBackStack() }) }
                composable(
                    Routes.COLLECTION,
                    arguments = listOf(navArgument("kind") { type = NavType.StringType }, navArgument("id") { type = NavType.StringType }),
                ) { entry ->
                    val kind = CollectionKind.valueOf(entry.arguments!!.getString("kind")!!)
                    val id = android.net.Uri.decode(entry.arguments!!.getString("id")!!)
                    CollectionScreen(kind, id, onBack = { nav.popBackStack() })
                }
                composable(Routes.ARTIST, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                    ArtistScreen(android.net.Uri.decode(entry.arguments!!.getString("id")!!), onBack = { nav.popBackStack() })
                }
                composable(Routes.ALBUMS) { LibraryAlbumsScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.ARTISTS) { LibraryArtistsScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.LOCAL_MUSIC, arguments = listOf(navArgument("sort") { type = NavType.StringType })) { entry ->
                    val sort = runCatching { LocalSort.valueOf(entry.arguments!!.getString("sort")!!) }.getOrDefault(LocalSort.TITLE)
                    LocalMusicScreen(sort, onBack = { nav.popBackStack() })
                }
            }
            val entry by nav.currentBackStackEntryAsState()
            val route = entry?.destination?.route
            // On a page below a tab (album, artist...), keep that tab lit.
            var lastTab by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(Tab.Home) }
            androidx.compose.runtime.LaunchedEffect(route) { Tab.entries.firstOrNull { it.route == route }?.let { lastTab = it } }
            if (route != SETTINGS_ROUTE && route != Routes.LOGIN && route != Routes.ABOUT && route != Routes.EQUALIZER) {
                Column(
                    Modifier.align(Alignment.BottomCenter).graphicsLayer { alpha = if (covered) 0f else 1f }
                        .navigationBarsPadding().padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    now?.let { MiniPlayer(it, connection, onOpen = { playerOpen = true }) }
                    NavPill(
                        current = Tab.entries.firstOrNull { it.route == route } ?: lastTab,
                        onSelect = { tab ->
                            // Tapping the tab you are on goes back to its top (every upstream app does).
                            if (route == tab.route) {
                                nav.currentBackStackEntry?.savedStateHandle?.set(SCROLL_TO_TOP, System.nanoTime())
                                return@NavPill
                            }
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
            // Content scrolls under a transparent status bar; a scrim keeps the clock readable.
            // When hideTopBar is on the scrim collapses to the status-bar inset with an animated transition.
            val scrimAlpha by animateFloatAsState(if (hideTopBar) 0f else 1f, tween(180), label = "scrimAlpha")
            if (scrimAlpha > 0.01f) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                        .graphicsLayer { this.alpha = scrimAlpha }
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f)),
                )
            }
            now?.let { PlayerSheet(it, ambience, open = playerOpen, onClose = { playerOpen = false }, onCovering = { covered = it }) }
        }
    }
}

private val tabRoutes = Tab.entries.map { it.route }.toSet()

const val SCROLL_TO_TOP = "scrollToTop"

/**
 * Settings from the top of every tab (ConvX puts the account button on each tab; ours was only
 * on Library, so it seemed to have gone missing).
 */
@Composable
fun SettingsAction() {
    val nav = LocalNavController.current
    dev.musicforge.ui.components.IconAction(dev.musicforge.ui.components.ForgeIcons.Settings, "Settings") { nav.navigate(SETTINGS_ROUTE) }
}

/** Scrolls [list] to the top when this screen's tab is tapped again. */
@Composable
fun ScrollToTopOnReselect(list: androidx.compose.foundation.lazy.LazyListState) {
    val entry = androidx.lifecycle.compose.LocalLifecycleOwner.current as? androidx.navigation.NavBackStackEntry ?: return
    androidx.compose.runtime.LaunchedEffect(entry) {
        entry.savedStateHandle.getStateFlow(SCROLL_TO_TOP, 0L).collect { if (it != 0L) list.animateScrollToItem(0) }
    }
}

@Composable
private fun NavPill(current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .glass(RoundedCornerShape(percent = 50))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Tab.entries.forEach { tab ->
            val selected = tab == current
            val tint by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                tween(160),
                label = "tabTint",
            )
            val chip by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0f),
                tween(160),
                label = "tabChip",
            )
            Column(
                modifier = Modifier
                    .width(72.dp)
                    .height(52.dp)
                    .background(chip, RoundedCornerShape(percent = 50))
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(tab) },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                Text(tab.label, style = MaterialTheme.typography.labelSmall, color = tint)
            }
        }
    }
}
