package com.davidgcd.backlog.ui.nav

import androidx.compose.material3.ExperimentalMaterial3Api
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

    NavHost(navController = navController, startDestination = Routes.BACKLOG) {
        composable(Routes.BACKLOG) {
            val viewModel: BacklogViewModel = viewModel(factory = BacklogViewModelFactory(repository))
            BacklogScreen(
                viewModel = viewModel,
                onGameClick = { gameId -> navController.navigate(Routes.gameDetail(gameId)) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onDiscoverClick = { navController.navigate(Routes.DISCOVER) },
            )
        }
        composable(Routes.DISCOVER) {
            val viewModel: DiscoverViewModel = viewModel(factory = DiscoverViewModelFactory(repository))
            DiscoverScreen(
                viewModel = viewModel,
                onGameClick = { gameId -> navController.navigate(Routes.gameDetail(gameId)) },
                onBack = { navController.popBackStack() },
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
            SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }
}
