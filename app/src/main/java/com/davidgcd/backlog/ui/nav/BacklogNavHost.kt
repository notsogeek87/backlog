package com.davidgcd.backlog.ui.nav

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.davidgcd.backlog.R
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.davidgcd.backlog.data.csv.CsvExportService
import com.davidgcd.backlog.data.csv.CsvImportService
import com.davidgcd.backlog.data.repository.BacklogRepository
import com.davidgcd.backlog.data.repository.MetacriticService
import com.davidgcd.backlog.data.repository.SteamService
import com.davidgcd.backlog.notifications.NotificationPreferences
import com.davidgcd.backlog.ui.backlog.BacklogScreen
import com.davidgcd.backlog.ui.backlog.BacklogViewModel
import com.davidgcd.backlog.ui.backlog.BacklogViewModelFactory
import com.davidgcd.backlog.ui.discover.DiscoverScreen
import com.davidgcd.backlog.ui.discover.DiscoverViewModel
import com.davidgcd.backlog.ui.discover.DiscoverViewModelFactory
import com.davidgcd.backlog.ui.gamedetail.GameDetailScreen
import com.davidgcd.backlog.ui.gamedetail.GameDetailViewModel
import com.davidgcd.backlog.ui.gamedetail.GameDetailViewModelFactory
import com.davidgcd.backlog.ui.settings.SettingsScreen
import com.davidgcd.backlog.ui.settings.SettingsViewModel
import com.davidgcd.backlog.ui.settings.SettingsViewModelFactory

private object Routes {
    const val BACKLOG = "backlog"
    const val SETTINGS = "settings"
    const val DISCOVER = "discover"
    const val GAME_DETAIL = "game/{gameId}"
    fun gameDetail(gameId: Long) = "game/$gameId"
}

private data class TopLevelDestination(val route: String, val icon: ImageVector, val labelRes: Int)

private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.BACKLOG, Icons.Filled.Bookmarks, R.string.backlog_title),
    TopLevelDestination(Routes.DISCOVER, Icons.Filled.Explore, R.string.discover_title),
    TopLevelDestination(Routes.SETTINGS, Icons.Filled.Settings, R.string.settings_title),
)

/**
 * The app's one NavHost — mirrors the iOS app's rule of a single navigation
 * stack per top-level surface, kept as one stack here since there is a
 * single tab for this first pass (no Accueil/Backlog split yet, see README).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacklogNavHost(
    repository: BacklogRepository,
    notificationPreferences: NotificationPreferences,
    steamService: SteamService,
    metacriticService: MetacriticService,
    csvExportService: CsvExportService,
    csvImportService: CsvImportService,
) {
    val navController = rememberNavController()
    val navEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navEntry?.destination
    // The bar only shows on the three top-level surfaces; game detail is a pushed screen with a back arrow.
    val showBottomBar = topLevelDestinations.any { top -> currentDestination?.hierarchy?.any { it.route == top.route } == true }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevelDestinations.forEach { top ->
                        val label = stringResource(top.labelRes)
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any { it.route == top.route } == true,
                            onClick = {
                                navController.navigate(top.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(top.icon, contentDescription = null) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { outerPadding ->
    NavHost(
        navController = navController,
        startDestination = Routes.BACKLOG,
        modifier = Modifier.padding(outerPadding).consumeWindowInsets(outerPadding),
    ) {
        composable(Routes.BACKLOG) {
            val viewModel: BacklogViewModel = viewModel(factory = BacklogViewModelFactory(repository))
            BacklogScreen(
                viewModel = viewModel,
                onGameClick = { gameId -> navController.navigate(Routes.gameDetail(gameId)) },
            )
        }
        composable(Routes.DISCOVER) {
            val viewModel: DiscoverViewModel = viewModel(factory = DiscoverViewModelFactory(repository))
            DiscoverScreen(
                viewModel = viewModel,
                onGameClick = { gameId -> navController.navigate(Routes.gameDetail(gameId)) },
            )
        }
        composable(
            route = Routes.GAME_DETAIL,
            arguments = listOf(navArgument("gameId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val gameId = backStackEntry.arguments?.getLong("gameId") ?: return@composable
            val viewModel: GameDetailViewModel = viewModel(
                factory = GameDetailViewModelFactory(gameId, repository, steamService, metacriticService),
                key = "game_detail_$gameId",
            )
            GameDetailScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            val appContext = LocalContext.current.applicationContext
            val viewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModelFactory(
                    notificationPreferences,
                    appContext,
                    repository,
                    csvExportService,
                    csvImportService,
                ),
            )
            SettingsScreen(viewModel = viewModel)
        }
    }
    }
}
