package com.example.xcpro.weglide.di

import android.content.Context
import androidx.room.Room
import com.example.xcpro.weglide.BuildConfig
import com.example.xcpro.weglide.api.WeGlideApi
import com.example.xcpro.weglide.auth.WeGlideAccountApi
import com.example.xcpro.weglide.auth.WeGlideAuthApi
import com.example.xcpro.weglide.auth.WeGlideAuthManager
import com.example.xcpro.weglide.auth.WeGlideOAuthConfig
import com.example.xcpro.weglide.auth.WeGlideTokenStore
import com.example.xcpro.weglide.auth.WeGlideTokenStoreImpl
import com.example.xcpro.weglide.data.WeGlideAircraftDao
import com.example.xcpro.weglide.data.WeGlideAircraftMappingDao
import com.example.xcpro.weglide.data.WeGlideDatabase
import com.example.xcpro.weglide.data.WeGlideLocalStateRepositoryImpl
import com.example.xcpro.weglide.data.WeGlidePreferencesRepository
import com.example.xcpro.weglide.data.WeGlideUploadQueueDao
import com.example.xcpro.weglide.domain.WeGlideAccountStore
import com.example.xcpro.weglide.domain.WeGlideAircraftMappingReadRepository
import com.example.xcpro.weglide.domain.WeGlideLocalStateRepository
import com.example.xcpro.weglide.domain.WeGlidePreferencesStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private const val WEGLIDE_DATABASE_NAME = "weglide.db"
private const val WEGLIDE_RETROFIT_BASE_URL = "https://api.weglide.org/"

@Module
@InstallIn(SingletonComponent::class)
object WeGlideRoomModule {

    @Provides
    @Singleton
    fun provideWeGlideDatabase(
        @ApplicationContext context: Context
    ): WeGlideDatabase {
        return Room.databaseBuilder(
            context,
            WeGlideDatabase::class.java,
            WEGLIDE_DATABASE_NAME
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideWeGlideAircraftDao(database: WeGlideDatabase): WeGlideAircraftDao {
        return database.aircraftDao()
    }

    @Provides
    fun provideWeGlideAircraftMappingDao(database: WeGlideDatabase): WeGlideAircraftMappingDao {
        return database.aircraftMappingDao()
    }

    @Provides
    fun provideWeGlideUploadQueueDao(database: WeGlideDatabase): WeGlideUploadQueueDao {
        return database.uploadQueueDao()
    }

    @Provides
    @Singleton
    fun provideWeGlideOAuthConfig(): WeGlideOAuthConfig {
        return WeGlideOAuthConfig(
            authorizationEndpoint = BuildConfig.WEGLIDE_AUTHORIZATION_ENDPOINT,
            tokenEndpoint = BuildConfig.WEGLIDE_TOKEN_ENDPOINT,
            clientId = BuildConfig.WEGLIDE_CLIENT_ID,
            redirectUri = BuildConfig.WEGLIDE_REDIRECT_URI,
            scope = BuildConfig.WEGLIDE_SCOPE,
            userInfoEndpoint = BuildConfig.WEGLIDE_USERINFO_ENDPOINT
        )
    }

    @Provides
    @Singleton
    fun provideWeGlideAuthApi(): WeGlideAuthApi {
        return Retrofit.Builder()
            .baseUrl(WEGLIDE_RETROFIT_BASE_URL)
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeGlideAuthApi::class.java)
    }

    @Provides
    @Singleton
    fun provideWeGlideApi(): WeGlideApi {
        return Retrofit.Builder()
            .baseUrl(WEGLIDE_RETROFIT_BASE_URL)
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeGlideApi::class.java)
    }

    @Provides
    @Singleton
    fun provideWeGlideAccountApi(): WeGlideAccountApi {
        return Retrofit.Builder()
            .baseUrl(WEGLIDE_RETROFIT_BASE_URL)
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeGlideAccountApi::class.java)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WeGlideBindingsModule {

    @Binds
    @Singleton
    abstract fun bindWeGlideAccountStore(
        repository: WeGlidePreferencesRepository
    ): WeGlideAccountStore

    @Binds
    @Singleton
    abstract fun bindWeGlidePreferencesStore(
        repository: WeGlidePreferencesRepository
    ): WeGlidePreferencesStore

    @Binds
    @Singleton
    abstract fun bindWeGlideLocalStateRepository(
        repository: WeGlideLocalStateRepositoryImpl
    ): WeGlideLocalStateRepository

    @Binds
    @Singleton
    abstract fun bindWeGlideAircraftMappingReadRepository(
        repository: WeGlideLocalStateRepositoryImpl
    ): WeGlideAircraftMappingReadRepository

    @Binds
    @Singleton
    abstract fun bindWeGlideTokenStore(
        repository: WeGlideTokenStoreImpl
    ): WeGlideTokenStore
}
