package com.davidgcd.backlog

import android.app.Application
import com.davidgcd.backlog.config.Secrets
import com.davidgcd.backlog.data.local.AppDatabase
import com.davidgcd.backlog.data.remote.IgdbApi
import com.davidgcd.backlog.data.remote.IgdbAuthInterceptor
import com.davidgcd.backlog.data.remote.IgdbTokenProvider
import com.davidgcd.backlog.data.remote.MetacriticApi
import com.davidgcd.backlog.data.remote.SteamApi
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

        val database = AppDatabase.get(this)

        repository = BacklogRepository(
            gameDao = database.gameDao(),
            igdbService = IgdbService(igdbApi),
            moshi = moshi,
        )
        steamService = SteamService(steamApi)
        metacriticService = MetacriticService(metacriticApi)

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
