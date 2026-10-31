package com.davidgcd.backlog

import android.content.Context
import com.davidgcd.backlog.config.Backend
import com.davidgcd.backlog.config.Secrets
import com.davidgcd.backlog.data.csv.CsvExportService
import com.davidgcd.backlog.data.csv.CsvImportService
import com.davidgcd.backlog.data.googlebooks.GoogleBooksService
import com.davidgcd.backlog.data.library.DataStoreLibraryAccountStore
import com.davidgcd.backlog.data.library.LibraryAccountStore
import com.davidgcd.backlog.data.library.LibraryProviders
import com.davidgcd.backlog.data.library.LibrarySyncService
import com.davidgcd.backlog.data.library.WishlistSyncService
import com.davidgcd.backlog.data.library.android.AndroidLibraryProvider
import com.davidgcd.backlog.data.library.android.PackageManagerInstalledApps
import com.davidgcd.backlog.data.library.steam.SteamAuthService
import com.davidgcd.backlog.data.library.steam.SteamLibraryProvider
import com.davidgcd.backlog.data.local.AppDatabase
import com.davidgcd.backlog.data.local.GameSourceDao
import com.davidgcd.backlog.data.openlibrary.OpenLibraryService
import com.davidgcd.backlog.data.remote.DebugLogInterceptor
import com.davidgcd.backlog.data.remote.IgdbApi
import com.davidgcd.backlog.data.remote.IgdbAuthInterceptor
import com.davidgcd.backlog.data.remote.IgdbAuthenticator
import com.davidgcd.backlog.data.remote.IgdbTokenApi
import com.davidgcd.backlog.data.remote.IgdbTokenProvider
import com.davidgcd.backlog.data.remote.MetacriticApi
import com.davidgcd.backlog.data.remote.SteamApi
import com.davidgcd.backlog.data.remote.SteamApiKeyInterceptor
import com.davidgcd.backlog.data.remote.SteamOpenIdApi
import com.davidgcd.backlog.data.remote.SteamPriceService
import com.davidgcd.backlog.data.remote.SteamWebApi
import com.davidgcd.backlog.data.repository.BacklogRepository
import com.davidgcd.backlog.data.repository.BookRepository
import com.davidgcd.backlog.data.repository.IgdbService
import com.davidgcd.backlog.data.repository.MetacriticService
import com.davidgcd.backlog.data.repository.MovieRepository
import com.davidgcd.backlog.data.repository.SteamService
import com.davidgcd.backlog.data.share.ShareLinkService
import com.davidgcd.backlog.data.tmdb.FileTmdbSessionStore
import com.davidgcd.backlog.data.tmdb.TmdbClient
import com.davidgcd.backlog.data.tmdb.TmdbSyncService
import com.davidgcd.backlog.ui.prefs.UiPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Composition root: every service of the app, built on first use (`lazy`) rather than all at launch.
 * No Hilt/Dagger: the graph is small enough that plain Kotlin stays clearer. [BacklogApplication] exposes these
 * services to screens and workers; tests can build their own pieces directly.
 *
 * Every HTTP client derives from [baseHttp] (`newBuilder()`), so they all share ONE connection pool and ONE
 * dispatcher instead of one pair per service.
 */
class AppContainer(private val context: Context) {

    val uiPreferences: UiPreferences by lazy { UiPreferences(context) }

    private val moshi: Moshi by lazy { Moshi.Builder().add(KotlinJsonAdapterFactory()).build() }

    private val loggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
    }

    private val baseHttp: OkHttpClient by lazy { OkHttpClient.Builder().build() }

    /** A client sharing [baseHttp]'s pool, with its own call timeout and interceptors. */
    private fun http(timeoutSeconds: Long, vararg interceptors: Interceptor): OkHttpClient =
        baseHttp.newBuilder()
            .apply { interceptors.forEach { addInterceptor(it) } }
            .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build()

    private fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    // --- Room ---------------------------------------------------------------------------------------------

    private val database: AppDatabase by lazy { AppDatabase.get(context) }
    val gameSourceDao: GameSourceDao by lazy { database.gameSourceDao() }

    // --- Jeux : IGDB (jeton fourni par le serveur de l'app, jamais de secret Twitch dans l'APK) ----------------

    private val igdbTokenProvider by lazy {
        IgdbTokenProvider(retrofit("${Backend.BASE_URL}/", http(15, loggingInterceptor)).create(IgdbTokenApi::class.java))
    }

    private val igdbService: IgdbService by lazy {
        val client = baseHttp.newBuilder()
            .addInterceptor(IgdbAuthInterceptor(igdbTokenProvider))
            .addInterceptor(DebugLogInterceptor())
            .addInterceptor(loggingInterceptor)
            .authenticator(IgdbAuthenticator(igdbTokenProvider))
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
        IgdbService(retrofit("https://api.igdb.com/", client).create(IgdbApi::class.java))
    }

    val repository: BacklogRepository by lazy {
        BacklogRepository(gameDao = database.gameDao(), igdbService = igdbService, moshi = moshi)
    }

    val steamService: SteamService by lazy {
        SteamService(retrofit("https://store.steampowered.com/", http(15, loggingInterceptor)).create(SteamApi::class.java))
    }

    val metacriticService: MetacriticService by lazy {
        val client = http(15, RapidApiHeadersInterceptor(), loggingInterceptor)
        MetacriticService(retrofit("https://${Secrets.METACRITIC_RAPIDAPI_HOST}/", client).create(MetacriticApi::class.java))
    }

    val steamPriceService: SteamPriceService by lazy { SteamPriceService(http(15)) }

    // --- Bibliothèques (Steam, Android) ---------------------------------------------------------------------

    private val steamKeyConfigured = Secrets.STEAM_API_KEY.isNotBlank() && !Secrets.STEAM_API_KEY.startsWith("YOUR_")

    private val steamProvider: SteamLibraryProvider by lazy {
        // The key interceptor goes AFTER logging so the key is never logged.
        val client = http(30, loggingInterceptor, SteamApiKeyInterceptor(if (steamKeyConfigured) Secrets.STEAM_API_KEY else ""))
        val steamWebApi = retrofit(Secrets.STEAM_API_BASE_URL, client).create(SteamWebApi::class.java)
        // A proxy (custom base URL) may hold the key itself, so "configured" = key present OR non-default host.
        val usable = steamKeyConfigured || Secrets.STEAM_API_BASE_URL != "https://api.steampowered.com/"
        SteamLibraryProvider(steamWebApi, isConfigured = usable)
    }

    val libraryAccountStore: LibraryAccountStore by lazy { DataStoreLibraryAccountStore(context) }

    val librarySyncService: LibrarySyncService by lazy {
        LibrarySyncService(
            providers = mapOf(
                LibraryProviders.STEAM to steamProvider,
                LibraryProviders.ANDROID to AndroidLibraryProvider(PackageManagerInstalledApps(context)),
            ),
            catalog = igdbService,
            repository = repository,
            sourceDao = gameSourceDao,
            accounts = libraryAccountStore,
        )
    }

    val wishlistSyncService: WishlistSyncService by lazy {
        WishlistSyncService(steamProvider, igdbService, repository, gameSourceDao, libraryAccountStore)
    }

    val steamAuthService: SteamAuthService by lazy {
        SteamAuthService(retrofit("https://steamcommunity.com/", http(30)).create(SteamOpenIdApi::class.java), steamProvider)
    }

    // --- Films & séries (TMDB) ----------------------------------------------------------------------------

    /** The key ships in the APK like the other keys; a user's session lives in no-backup storage, never in it. */
    private val tmdbClient: TmdbClient by lazy {
        TmdbClient(http = http(20), apiKey = Secrets.TMDB_API_KEY, sessions = FileTmdbSessionStore(context))
    }

    val movieRepository: MovieRepository by lazy { MovieRepository(database.movieDao(), tmdbClient) }
    val tmdbSyncService: TmdbSyncService by lazy { TmdbSyncService(tmdbClient, database.movieDao(), libraryAccountStore) }

    // --- Livres (Open Library, Google Books en secours ; aucune clé) ---------------------------------------------

    val bookRepository: BookRepository by lazy {
        val client = http(15)
        BookRepository(database.bookDao(), OpenLibraryService(client), GoogleBooksService(client))
    }

    // --- Partage, CSV ---------------------------------------------------------------------------------------

    val shareLinkService: ShareLinkService by lazy { ShareLinkService(context, http(12)) }
    val csvExportService: CsvExportService by lazy { CsvExportService(context) }
    val csvImportService: CsvImportService by lazy { CsvImportService(context, repository, bookRepository) }
}

private class RapidApiHeadersInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request().newBuilder()
            .addHeader("X-RapidAPI-Host", Secrets.METACRITIC_RAPIDAPI_HOST)
            .addHeader("X-RapidAPI-Key", Secrets.METACRITIC_RAPIDAPI_KEY)
            .build()
        return chain.proceed(request)
    }
}
