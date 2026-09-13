package com.promptforge.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.promptforge.AppContainer
import com.promptforge.R
import com.promptforge.ui.components.AuroraBackground
import com.promptforge.ui.screens.AppBuilderScreen
import com.promptforge.ui.screens.AppScannerScreen
import com.promptforge.ui.screens.DocsScreen
import com.promptforge.ui.screens.ExtensionsScreen
import com.promptforge.ui.screens.BuilderScreen
import com.promptforge.ui.screens.HomeScreen
import com.promptforge.ui.screens.LibraryScreen
import com.promptforge.ui.screens.LocalModelsScreen
import com.promptforge.ui.screens.PlaygroundScreen
import com.promptforge.ui.screens.SettingsScreen
import com.promptforge.ui.screens.TemplatesScreen
import com.promptforge.ui.theme.Palette

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer not provided")
}

object Routes {
    const val HOME = "home"
    const val BUILDER = "builder"
    const val TEMPLATES = "templates"
    const val PLAYGROUND = "playground"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val LOCALMODELS = "localmodels"
    const val APPSCANNER = "appscanner"
    const val APPBUILDER = "appbuilder"
    const val DOCS = "docs"
    const val EXTENSIONS = "extensions"
}

private data class BarItem(val route: String, val icon: Int, val label: Int)

@Composable
fun ForgeRootApp() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: Routes.HOME
    val barItems = listOf(
        BarItem(Routes.HOME, R.drawable.ic_nav_home, R.string.nav_home),
        BarItem(Routes.TEMPLATES, R.drawable.ic_nav_templates, R.string.nav_templates),
        BarItem(Routes.BUILDER, R.drawable.ic_nav_builder, R.string.nav_builder),
        BarItem(Routes.PLAYGROUND, R.drawable.ic_nav_playground, R.string.nav_playground),
        BarItem(Routes.LIBRARY, R.drawable.ic_nav_library, R.string.nav_library),
        BarItem(Routes.SETTINGS, R.drawable.ic_settings, R.string.nav_settings),
    )

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { _ ->
        Box(Modifier.fillMaxSize().background(Palette.Bg)) {
            AuroraBackground(Modifier.fillMaxSize())

            NavHost(
                navController = nav,
                startDestination = Routes.HOME,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 40 } },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(160)) },
            ) {
                composable(Routes.HOME) {
                    HomeScreen(onNavigate = { nav.navigateSingleTop(it) })
                }
                composable(Routes.BUILDER) { BuilderScreen(onNavigate = { nav.navigateSingleTop(it) }) }
                composable(Routes.TEMPLATES) { TemplatesScreen(onNavigate = { nav.navigateSingleTop(it) }) }
                composable(Routes.PLAYGROUND) { PlaygroundScreen(onNavigate = { nav.navigateSingleTop(it) }) }
                composable(Routes.LIBRARY) { LibraryScreen(onNavigate = { nav.navigateSingleTop(it) }) }
                composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }, onNavigate = { nav.navigateSingleTop(it) }) }
                composable(Routes.LOCALMODELS) { LocalModelsScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.APPSCANNER) { AppScannerScreen(onNavigate = { nav.navigateSingleTop(it) }) }
                composable(Routes.APPBUILDER) { AppBuilderScreen(onNavigate = { nav.navigateSingleTop(it) }) }
                composable(Routes.DOCS) { DocsScreen(onNavigate = { nav.navigateSingleTop(it) }) }
                composable(Routes.EXTENSIONS) { ExtensionsScreen(onNavigate = { nav.navigateSingleTop(it) }) }
            }

            AnimatedVisibility(
                visible = currentRoute != Routes.LOCALMODELS && currentRoute != Routes.APPSCANNER &&
                    currentRoute != Routes.APPBUILDER && currentRoute != Routes.DOCS &&
                    currentRoute != Routes.EXTENSIONS,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(tween(250)) { it } + fadeIn(),
                exit = slideOutVertically(tween(200)) { it } + fadeOut(),
            ) {
                ForgeBottomBar(
                    items = barItems,
                    current = currentRoute,
                    onSelect = { nav.navigateSingleTop(it) },
                )
            }
        }
    }
}

private fun androidx.navigation.NavController.navigateSingleTop(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun ForgeBottomBar(items: List<BarItem>, current: String, onSelect: (String) -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    val barShadow = if (!Palette.isDark) Modifier.shadow(
        22.dp, shape, ambientColor = Palette.shadowColor, spotColor = Palette.shadowColor
    ) else Modifier
    Row(
        barShadow
            .navigationBarsPadding()
            .padding(bottom = 14.dp)
            .clip(shape)
            .background(Palette.barBg)
            .border(1.dp, Palette.glassBorder, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        items.forEach { item ->
            val selected = item.route == current
            val scale by animateFloatAsState(if (selected) 1f else 0.94f, tween(220), label = "scale")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onSelect(item.route) }
                    .padding(horizontal = 9.dp, vertical = 6.dp),
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .scale(scale)
                        .clip(RoundedCornerShape(13.dp))
                        .background(if (selected) Brush.linearGradient(Palette.brandColors) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(item.icon),
                        contentDescription = stringResource(item.label),
                        tint = if (selected) Color.White else Palette.Sub,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    stringResource(item.label),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) Palette.Ink else Palette.Faint,
                )
            }
        }
    }
}
