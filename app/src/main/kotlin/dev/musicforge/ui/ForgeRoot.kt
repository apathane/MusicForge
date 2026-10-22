package dev.musicforge.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.musicforge.ui.components.ForgeIcons
import dev.musicforge.ui.components.GlassStyle
import dev.musicforge.ui.components.LocalGlassStyle
import dev.musicforge.ui.components.glass
import dev.musicforge.ui.screens.DownloadsScreen
import dev.musicforge.ui.screens.HomeScreen
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

@Composable
fun ForgeRoot(glass: GlassStyle) {
    val nav = rememberNavController()
    CompositionLocalProvider(LocalGlassStyle provides glass, LocalNavController provides nav) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            NavHost(nav, startDestination = Tab.Home.route) {
                composable(Tab.Home.route) { HomeScreen() }
                composable(Tab.Search.route) { SearchScreen() }
                composable(Tab.Library.route) { LibraryScreen(onOpenSettings = { nav.navigate(SETTINGS_ROUTE) }) }
                composable(Tab.Downloads.route) { DownloadsScreen() }
                composable(SETTINGS_ROUTE) { SettingsScreen(onBack = { nav.popBackStack() }) }
            }
            val entry by nav.currentBackStackEntryAsState()
            val route = entry?.destination?.route
            if (route != SETTINGS_ROUTE) {
                NavPill(
                    current = Tab.entries.firstOrNull { it.route == route } ?: Tab.Home,
                    onSelect = { tab ->
                        nav.navigate(tab.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun NavPill(current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 12.dp)
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
