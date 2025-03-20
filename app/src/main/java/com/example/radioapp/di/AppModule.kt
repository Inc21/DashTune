package com.example.radioapp.di

import android.content.Context
import androidx.room.Room
import com.example.radioapp.data.local.RadioDatabase
import com.example.radioapp.data.remote.RadioBrowserApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideRadioDatabase(
        @ApplicationContext context: Context
    ): RadioDatabase {
        return Room.databaseBuilder(
            context,
            RadioDatabase::class.java,
            "radio_database"
        ).build()
    }
    
    @Provides
    @Singleton
    fun provideRadioBrowserApi(): RadioBrowserApi {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "RadioApp/1.0") // Required by Radio Browser API
                    .build()
                chain.proceed(request)
            }
            .build()
            
        return Retrofit.Builder()
            .baseUrl("https://fr1.api.radio-browser.info/json/") // Using French server for better reliability
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RadioBrowserApi::class.java)
    }
} 