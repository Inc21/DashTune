package com.example.dashtune.di

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.room.Room
import com.example.dashtune.data.api.RadioBrowserService
import com.example.dashtune.data.dao.RadioStationDao
import com.example.dashtune.data.local.RadioDatabase
import com.example.dashtune.data.remote.RadioBrowserApi
import com.example.dashtune.data.remote.RadioBrowserApiImpl
import com.example.dashtune.data.remote.RetryableRadioBrowserService
import com.example.dashtune.data.repository.RadioStationRepository
import com.example.dashtune.playback.StationValidator
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    @Provides
    @Singleton
    fun provideRadioBrowserService(
        okHttpClient: OkHttpClient,
        moshi: Moshi
    ): RadioBrowserService {
        return RetryableRadioBrowserService(okHttpClient, moshi)
    }

    @Provides
    @Singleton
    fun provideRadioBrowserApi(
        service: RadioBrowserService
    ): RadioBrowserApi = RadioBrowserApiImpl(service)

    @Provides
    @Singleton
    fun provideRadioDatabase(
        @ApplicationContext context: Context
    ): RadioDatabase = Room.databaseBuilder(
        context,
        RadioDatabase::class.java,
        "radio_database"
    )
    .addMigrations(
        RadioDatabase.MIGRATION_1_2,
        RadioDatabase.MIGRATION_2_3,
        RadioDatabase.MIGRATION_3_4
    )
    .build()

    @Provides
    @Singleton
    fun provideRadioStationDao(
        database: RadioDatabase
    ): RadioStationDao = database.radioStationDao()

    @Provides
    @Singleton
    fun provideExoPlayer(
        @ApplicationContext context: Context
    ): ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true
        )
        .build()

    @Provides
    @Singleton
    fun provideStationValidator(
        @ApplicationContext appContext: Context,
        radioStationRepository: RadioStationRepository
    ): StationValidator {
        return StationValidator(appContext, radioStationRepository)
    }
} 