package com.davidgcd.backlog

import android.app.Application
import com.davidgcd.backlog.data.local.AppDatabase
import com.davidgcd.backlog.data.remote.IgdbAuthInterceptor
import com.davidgcd.backlog.data.remote.IgdbApi
import com.davidgcd.backlog.data.remote.IgdbTokenProvider
import com.davidgcd.backlog.data.remote.TwitchAuthApi
import com.davidgcd.backlog.data.repository.BacklogRepository
import com.davidgcd.backlog.data.repository.IgdbService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
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

    override fun onCreate() {
        super.onCreate()

        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

        val twitchAuthApi = Retrofit.Builder()
            .baseUrl("https://id.twitch.tv/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TwitchAuthApi::class.java)

        val tokenProvider = IgdbTokenProvider(twitchAuthApi)

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }

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

        val database = AppDatabase.get(this)

        repository = BacklogRepository(
            gameDao = database.gameDao(),
            igdbService = IgdbService(igdbApi),
            moshi = moshi,
        )
    }
}
