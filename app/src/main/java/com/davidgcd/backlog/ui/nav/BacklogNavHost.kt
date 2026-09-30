package com.davidgcd.backlog.ui.nav

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import com.davidgcd.backlog.ui.components.glassRailItemColors
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRail
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.ui.unit.dp
import com.davidgcd.backlog.R
import com.davidgcd.backlog.ui.components.GlassNavBarColor
import com.davidgcd.backlog.ui.components.glassNavigationItemColors
import com.davidgcd.backlog.ui.theme.Glass
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.davidgcd.backlog.data.csv.CsvExportService
import com.davidgcd.backlog.data.tmdb.TmdbSyncService
import com.davidgcd.backlog.data.repository.MovieRepository
import com.davidgcd.backlog.ui.components.MediaSwitch
import com.davidgcd.backlog.ui.components.MediaType
import com.davidgcd.backlog.ui.moviedetail.MovieDetailScreen
import com.davidgcd.backlog.ui.moviedetail.MovieDetailViewModel
import com.davidgcd.backlog.ui.moviedetail.MovieDetailViewModelFactory
import com.davidgcd.backlog.ui.movies.MovieDiscoverScreen
import com.davidgcd.backlog.ui.movies.MovieDiscoverViewModel
import com.davidgcd.backlog.ui.movies.MovieDiscoverViewModelFactory
import com.davidgcd.backlog.ui.movies.MovieRankingScreen
import com.davidgcd.backlog.ui.movies.MovieRankingViewModel
import com.davidgcd.backlog.ui.movies.MovieRankingViewModelFactory
import com.davidgcd.backlog.ui.movies.MoviesScreen
import com.davidgcd.backlog.ui.movies.MoviesViewModel
import com.davidgcd.backlog.ui.movies.MoviesViewModelFactory
import com.davidgcd.backlog.ui.platforms.TmdbImportScreen
import com.davidgcd.backlog.ui.platforms.TmdbImportViewModel
import com.davidgcd.backlog.ui.platforms.TmdbImportViewModelFactory
import com.davidgcd.backlog.ui.platforms.TmdbLoginScreen
import com.davidgcd.backlog.ui.platforms.TmdbLoginViewModel
import com.davidgcd.backlog.ui.platforms.TmdbLoginViewModelFactory
import com.davidgcd.backlog.data.csv.CsvImportService
import com.davidgcd.backlog.data.library.LibraryAccountStore
import com.davidgcd.backlog.data.library.LibraryProviders
import com.davidgcd.backlog.data.library.LibrarySyncService
import com.davidgcd.backlog.data.library.WishlistSyncService
import com.davidgcd.backlog.data.library.steam.SteamAuthService
import com.davidgcd.backlog.data.local.GameSourceDao
import com.davidgcd.backlog.data.repository.BacklogRepository
import com.davidgcd.backlog.data.share.ShareLinkService
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
import com.davidgcd.backlog.ui.platforms.LibraryImportScreen
import com.davidgcd.backlog.ui.platforms.LibraryImportViewModel
import com.davidgcd.backlog.ui.platforms.LibraryImportViewModelFactory
import com.davidgcd.backlog.ui.platforms.PlatformsSection
import com.davidgcd.backlog.ui.platforms.PlatformsViewModel
import com.davidgcd.backlog.ui.platforms.PlatformsViewModelFactory
import com.davidgcd.backlog.ui.platforms.SteamLoginScreen
import com.davidgcd.backlog.ui.platforms.SteamLoginViewModel
import com.davidgcd.backlog.ui.platforms.SteamLoginViewModelFactory
import com.davidgcd.backlog.data.csv.AutoExportPreferences
import com.davidgcd.backlog.ui.settings.SettingsScreen
import com.davidgcd.backlog.ui.settings.SettingsViewModel
import com.davidgcd.backlog.ui.ranking.RankingScreen
import com.davidgcd.backlog.ui.ranking.RankingViewModel
import com.davidgcd.backlog.ui.ranking.RankingViewModelFactory
import com.davidgcd.backlog.ui.settings.SettingsViewModelFactory

private object Routes {
    const val BACKLOG = "backlog"
    const val SETTINGS = "settings"
    const val DISCOVER = "discover"
    const val RANKING = "ranking"
    const val STEAM_LOGIN = "platforms/steam/login"
    const val LIBRARY_IMPORT = "platforms/{provider}/import"
    fun libraryImport(provider: String) = "platforms/$provider/import"
    const val MOVIES = "movies"
    const val MOVIE_RANKING = "movies/ranking"
    const val MOVIE_DETAIL = "movie/{titleKey}"
    fun movieDetail(titleKey: String) = "movie/$titleKey"
    const val TMDB_LOGIN = "platforms/tmdb/login"
    const val TMDB_IMPORT = "platforms/tmdb/import"
    const val GAME_DETAIL = "game/{gameId}"
    fun gameDetail(gameId: Long) = "game/$gameId"
}

/** Screen width (dp) from which the bottom bar becomes a side rail. */
private const val RAIL_MIN_WIDTH_DP = 600

private data class TopLevelDestination(val route: String, val icon: ImageVector, val labelRes: Int)

private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.BACKLOG, Icons.Filled.Bookmarks, R.string.tab_games),
    TopLevelDestination(Routes.MOVIES, Icons.Filled.Movie, R.string.tab_movies),
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
    libraryAccountStore: LibraryAccountStore,
    librarySyncService: LibrarySyncService,
    wishlistSyncService: WishlistSyncService,
    steamAuthService: SteamAuthService,
    gameSourceDao: GameSourceDao,
    shareLinkService: ShareLinkService,
    movieRepository: MovieRepository,
    tmdbSyncService: TmdbSyncService,
) {
    val navController = rememberNavController()
    // Discover serves both worlds; which one is showing survives rotation and tab switches.
    var discoverMedia by rememberSaveable { mutableStateOf(MediaType.GAMES) }
    val tmdbAccount by libraryAccountStore.observe(LibraryProviders.TMDB).collectAsState(initial = null)
    val navEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navEntry?.destination
    // The bar only shows on the three top-level surfaces; game detail is a pushed screen with a back arrow.
    val showBottomBar = topLevelDestinations.any { top -> currentDestination?.hierarchy?.any { it.route == top.route } == true }

    // Wide windows (unfolded foldable, tablet) get a side rail; phones keep the bottom bar.
    val useRail = LocalConfiguration.current.screenWidthDp >= RAIL_MIN_WIDTH_DP
    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
    if (showBottomBar && useRail) {
        NavigationRail(containerColor = GlassNavBarColor) {
            Spacer(Modifier.weight(1f))
            topLevelDestinations.forEach { top ->
                NavigationRailItem(
                    selected = currentDestination?.hierarchy?.any { it.route == top.route } == true,
                    onClick = { navigateTo(top.route) },
                    icon = { Icon(top.icon, contentDescription = null) },
                    label = { Text(stringResource(top.labelRes)) },
                    colors = glassRailItemColors(),
                )
            }
            Spacer(Modifier.weight(1f))
        }
    }
    Scaffold(
        modifier = Modifier.weight(1f),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = Glass.Text,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar && !useRail) {
                NavigationBar(containerColor = GlassNavBarColor, tonalElevation = 0.dp) {
                    topLevelDestinations.forEach { top ->
                        val label = stringResource(top.labelRes)
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any { it.route == top.route } == true,
                            onClick = { navigateTo(top.route) },
                            icon = { Icon(top.icon, contentDescription = null) },
                            label = { Text(label) },
                            colors = glassNavigationItemColors(),
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
            val viewModel: BacklogViewModel = viewModel(factory = BacklogViewModelFactory(repository, shareLinkService))
            BacklogScreen(
                viewModel = viewModel,
                onGameClick = { gameId -> navController.navigate(Routes.gameDetail(gameId)) },
                onOpenRanking = { navController.navigate(Routes.RANKING) },
            )
        }
        composable(Routes.RANKING) {
            val viewModel: RankingViewModel = viewModel(factory = RankingViewModelFactory(repository))
            RankingScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onGameClick = { gameId -> navController.navigate(Routes.gameDetail(gameId)) },
            )
        }
        composable(Routes.DISCOVER) {
            val mediaSwitch: @Composable () -> Unit = { MediaSwitch(selected = discoverMedia, onSelect = { discoverMedia = it }) }
            if (discoverMedia == MediaType.GAMES) {
                val viewModel: DiscoverViewModel = viewModel(factory = DiscoverViewModelFactory(repository))
                DiscoverScreen(
                    viewModel = viewModel,
                    onGameClick = { gameId -> navController.navigate(Routes.gameDetail(gameId)) },
                    mediaSwitch = mediaSwitch,
                )
            } else {
                val viewModel: MovieDiscoverViewModel = viewModel(factory = MovieDiscoverViewModelFactory(movieRepository))
                MovieDiscoverScreen(
                    viewModel = viewModel,
                    onMovieClick = { titleKey -> navController.navigate(Routes.movieDetail(titleKey)) },
                    mediaSwitch = mediaSwitch,
                )
            }
        }
        composable(Routes.MOVIES) {
            val viewModel: MoviesViewModel = viewModel(factory = MoviesViewModelFactory(movieRepository))
            MoviesScreen(
                viewModel = viewModel,
                onMovieClick = { titleKey -> navController.navigate(Routes.movieDetail(titleKey)) },
                onOpenRanking = { navController.navigate(Routes.MOVIE_RANKING) },
                onOpenTmdbImport = {
                    navController.navigate(if (tmdbAccount != null) Routes.TMDB_IMPORT else Routes.TMDB_LOGIN)
                },
            )
        }
        composable(Routes.MOVIE_RANKING) {
            val viewModel: MovieRankingViewModel = viewModel(factory = MovieRankingViewModelFactory(movieRepository))
            MovieRankingScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onMovieClick = { titleKey -> navController.navigate(Routes.movieDetail(titleKey)) },
            )
        }
        composable(
            route = Routes.MOVIE_DETAIL,
            arguments = listOf(navArgument("titleKey") { type = NavType.StringType }),
        ) { backStackEntry ->
            val titleKey = backStackEntry.arguments?.getString("titleKey") ?: return@composable
            val viewModel: MovieDetailViewModel = viewModel(
                factory = MovieDetailViewModelFactory(titleKey, movieRepository),
                key = "movie_detail_$titleKey",
            )
            MovieDetailScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(Routes.TMDB_LOGIN) {
            val viewModel: TmdbLoginViewModel = viewModel(factory = TmdbLoginViewModelFactory(tmdbSyncService))
            TmdbLoginScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                // Connected: replace the login screen with the import screen (back returns to where we came from).
                onConnected = {
                    navController.navigate(Routes.TMDB_IMPORT) {
                        popUpTo(Routes.TMDB_LOGIN) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.TMDB_IMPORT) {
            val viewModel: TmdbImportViewModel = viewModel(factory = TmdbImportViewModelFactory(tmdbSyncService))
            TmdbImportScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onReconnect = {
                    navController.navigate(Routes.TMDB_LOGIN) {
                        popUpTo(Routes.TMDB_IMPORT) { inclusive = true }
                    }
                },
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
                    AutoExportPreferences(appContext),
                    shareLinkService,
                ),
            )
            val platformsViewModel: PlatformsViewModel = viewModel(
                factory = PlatformsViewModelFactory(libraryAccountStore, gameSourceDao, tmdbSyncService, wishlistSyncService),
            )
            SettingsScreen(
                viewModel = viewModel,
                platformsContent = {
                    PlatformsSection(
                        viewModel = platformsViewModel,
                        onConnectSteam = { navController.navigate(Routes.STEAM_LOGIN) },
                        onSyncSteam = { navController.navigate(Routes.libraryImport(LibraryProviders.STEAM)) },
                        onSyncAndroid = { navController.navigate(Routes.libraryImport(LibraryProviders.ANDROID)) },
                        onConnectTmdb = { navController.navigate(Routes.TMDB_LOGIN) },
                        onSyncTmdb = { navController.navigate(Routes.TMDB_IMPORT) },
                    )
                },
            )
        }
        composable(Routes.STEAM_LOGIN) {
            val viewModel: SteamLoginViewModel = viewModel(
                factory = SteamLoginViewModelFactory(steamAuthService, libraryAccountStore),
            )
            SteamLoginScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                // Connected: replace the login screen with the import screen (back returns to Settings).
                onConnected = {
                    navController.navigate(Routes.libraryImport(LibraryProviders.STEAM)) {
                        popUpTo(Routes.STEAM_LOGIN) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = Routes.LIBRARY_IMPORT,
            arguments = listOf(navArgument("provider") { type = NavType.StringType }),
        ) { backStackEntry ->
            val provider = backStackEntry.arguments?.getString("provider") ?: return@composable
            val viewModel: LibraryImportViewModel = viewModel(
                factory = LibraryImportViewModelFactory(provider, librarySyncService),
                key = "library_import_$provider",
            )
            LibraryImportScreen(viewModel = viewModel, providerId = provider, onBack = { navController.popBackStack() })
        }
    }
    }
    }
}
