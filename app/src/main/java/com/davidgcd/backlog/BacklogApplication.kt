package com.davidgcd.backlog

import android.app.Application
import com.davidgcd.backlog.config.Secrets
import com.davidgcd.backlog.data.csv.CsvExportService
import com.davidgcd.backlog.data.csv.CsvImportService
import com.davidgcd.backlog.data.library.DataStoreLibraryAccountStore
import com.davidgcd.backlog.data.library.LibraryAccountStore
import com.davidgcd.backlog.data.library.LibraryProviders
import com.davidgcd.backlog.data.library.LibrarySyncService
import com.davidgcd.backlog.data.library.steam.SteamAuthService
import com.davidgcd.backlog.data.library.steam.SteamLibraryProvider
import com.davidgcd.backlog.data.local.AppDatabase
import com.davidgcd.backlog.data.local.GameSourceDao
import com.davidgcd.backlog.data.remote.IgdbApi
import com.davidgcd.backlog.data.remote.IgdbAuthInterceptor
import com.davidgcd.backlog.data.remote.IgdbTokenProvider
import com.davidgcd.backlog.data.remote.MetacriticApi
import com.davidgcd.backlog.data.remote.SteamApi
import com.davidgcd.backlog.data.remote.SteamApiKeyInterceptor
import com.davidgcd.backlog.data.remote.SteamOpenIdApi
import com.davidgcd.backlog.data.remote.SteamWebApi
import com.davidgcd.backlog.data.remote.TwitchAuthApi
import com.davidgcd.backlog.data.repository.BacklogRepository
import com.davidgcd.backlog.data.repository.IgdbService
import com.davidgcd.backlog.data.repository.MetacriticService
import com.davidgcd.backlog.data.repository.SteamService
import com.davidgcd.backlog.notifications.ReleaseReminderScheduler
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import java.util.concurrent.TimeUnit
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Hand-rolled composition root (no Hilt/Dagger yet — the app is small enough
 * that a manual graph is clearer while the project is a skeleton).
 */
class BacklogApplication : Application() {

    lateinit var repository: BacklogRepository
        private set

    lateinit var steamService: SteamService
        private set

    lateinit var metacriticService: MetacriticService
        private set

    lateinit var csvExportService: CsvExportService
        private set

    lateinit var csvImportService: CsvImportService
        private set

    lateinit var libraryAccountStore: LibraryAccountStore
        private set

    lateinit var librarySyncService: LibrarySyncService
        private set

    lateinit var steamAuthService: SteamAuthService
        private set

    lateinit var gameSourceDao: GameSourceDao
        private set

    override fun onCreate() {
        super.onCreate()

        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }

        val twitchAuthApi = Retrofit.Builder()
            .baseUrl("https://id.twitch.tv/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TwitchAuthApi::class.java)

        val tokenProvider = IgdbTokenProvider(twitchAuthApi)

        val igdbHttpClient = OkHttpClient.Builder()
            .addInterceptor(IgdbAuthInterceptor(tokenProvider))
            .addInterceptor(com.davidgcd.backlog.data.remote.DebugLogInterceptor())
            .addInterceptor(loggingInterceptor)
            .build()

        val igdbApi = Retrofit.Builder()
            .baseUrl("https://api.igdb.com/")
            .client(igdbHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(IgdbApi::class.java)

        val steamApi = Retrofit.Builder()
            .baseUrl("https://store.steampowered.com/")
            .client(OkHttpClient.Builder().addInterceptor(loggingInterceptor).build())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SteamApi::class.java)

        val metacriticHttpClient = OkHttpClient.Builder()
            .addInterceptor(RapidApiHeadersInterceptor())
            .addInterceptor(loggingInterceptor)
            .build()

        val metacriticApi = Retrofit.Builder()
            .baseUrl("https://${Secrets.METACRITIC_RAPIDAPI_HOST}/")
            .client(metacriticHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(MetacriticApi::class.java)

        // Steam Web API (library). The key interceptor goes AFTER logging so the key is never logged.
        val steamKeyConfigured = Secrets.STEAM_API_KEY.isNotBlank() && !Secrets.STEAM_API_KEY.startsWith("YOUR_")
        val steamWebHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(SteamApiKeyInterceptor(if (steamKeyConfigured) Secrets.STEAM_API_KEY else ""))
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
        val steamWebApi = Retrofit.Builder()
            .baseUrl(Secrets.STEAM_API_BASE_URL)
            .client(steamWebHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SteamWebApi::class.java)
        val steamOpenIdApi = Retrofit.Builder()
            .baseUrl("https://steamcommunity.com/")
            .client(OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SteamOpenIdApi::class.java)

        val database = AppDatabase.get(this)
        gameSourceDao = database.gameSourceDao()

        val igdbService = IgdbService(igdbApi)
        repository = BacklogRepository(
            gameDao = database.gameDao(),
            igdbService = igdbService,
            moshi = moshi,
        )

        // A proxy (custom base URL) may hold the key itself, so "configured" = key present OR non-default host.
        val steamUsable = steamKeyConfigured || Secrets.STEAM_API_BASE_URL != "https://api.steampowered.com/"
        val steamProvider = SteamLibraryProvider(steamWebApi, isConfigured = steamUsable)
        libraryAccountStore = DataStoreLibraryAccountStore(this)
        librarySyncService = LibrarySyncService(
            providers = mapOf(LibraryProviders.STEAM to steamProvider),
            catalog = igdbService,
            repository = repository,
            sourceDao = gameSourceDao,
            accounts = libraryAccountStore,
        )
        steamAuthService = SteamAuthService(steamOpenIdApi, steamProvider)
        steamService = SteamService(steamApi)
        metacriticService = MetacriticService(metacriticApi)
        csvExportService = CsvExportService(this)
        csvImportService = CsvImportService(this, repository)

        ReleaseReminderScheduler.schedule(this)
    }
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
