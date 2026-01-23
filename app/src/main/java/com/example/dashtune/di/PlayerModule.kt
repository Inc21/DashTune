package com.example.dashtune.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {
    // ExoPlayer is now provided by AppModule
} 