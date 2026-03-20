package com.example.xcpro.livefollow.di

import com.example.xcpro.common.di.DefaultDispatcher
import com.example.xcpro.common.di.IoDispatcher
import com.example.xcpro.core.time.Clock
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.livefollow.data.ownship.FlightDataLiveOwnshipSnapshotSource
import com.example.xcpro.livefollow.data.ownship.LiveOwnshipSnapshotSource
import com.example.xcpro.livefollow.data.session.CurrentApiLiveFollowSessionGateway
import com.example.xcpro.livefollow.data.session.LiveFollowSessionGateway
import com.example.xcpro.livefollow.data.session.LiveFollowSessionRepository
import com.example.xcpro.livefollow.data.watch.CurrentApiDirectWatchTrafficSource
import com.example.xcpro.livefollow.data.watch.DirectWatchTrafficSource
import com.example.xcpro.livefollow.data.watch.WatchTrafficRepository
import com.example.xcpro.ogn.OgnTrafficPreferencesRepository
import com.example.xcpro.ogn.OgnTrafficRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object LiveFollowDataModule {
    @Provides
    @Singleton
    @LiveFollowHttpClient
    fun provideLiveFollowHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideLiveFollowSessionGateway(
        @LiveFollowHttpClient httpClient: OkHttpClient,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): LiveFollowSessionGateway {
        return CurrentApiLiveFollowSessionGateway(
            httpClient = httpClient,
            ioDispatcher = ioDispatcher
        )
    }

    @Provides
    @Singleton
    fun provideLiveOwnshipSnapshotSource(
        flightDataRepository: FlightDataRepository,
        ognTrafficPreferencesRepository: OgnTrafficPreferencesRepository,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher
    ): LiveOwnshipSnapshotSource {
        return FlightDataLiveOwnshipSnapshotSource(
            scope = liveFollowScope(defaultDispatcher),
            flightDataRepository = flightDataRepository,
            ownFlarmHexFlow = ognTrafficPreferencesRepository.ownFlarmHexFlow,
            ownIcaoHexFlow = ognTrafficPreferencesRepository.ownIcaoHexFlow
        )
    }

    @Provides
    @Singleton
    fun provideLiveFollowSessionRepository(
        ownshipSnapshotSource: LiveOwnshipSnapshotSource,
        gateway: LiveFollowSessionGateway,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher
    ): LiveFollowSessionRepository {
        return LiveFollowSessionRepository(
            scope = liveFollowScope(defaultDispatcher),
            ownshipSnapshotSource = ownshipSnapshotSource,
            gateway = gateway
        )
    }

    @Provides
    @Singleton
    fun provideDirectWatchTrafficSource(
        clock: Clock,
        sessionRepository: LiveFollowSessionRepository,
        @LiveFollowHttpClient httpClient: OkHttpClient,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher
    ): DirectWatchTrafficSource {
        return CurrentApiDirectWatchTrafficSource(
            scope = liveFollowScope(defaultDispatcher),
            clock = clock,
            sessionState = sessionRepository.state,
            httpClient = httpClient,
            ioDispatcher = ioDispatcher
        )
    }

    @Provides
    @Singleton
    fun provideWatchTrafficRepository(
        clock: Clock,
        sessionRepository: LiveFollowSessionRepository,
        ognTrafficRepository: OgnTrafficRepository,
        directWatchTrafficSource: DirectWatchTrafficSource,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher
    ): WatchTrafficRepository {
        return WatchTrafficRepository(
            scope = liveFollowScope(defaultDispatcher),
            clock = clock,
            sessionState = sessionRepository.state,
            ognTrafficRepository = ognTrafficRepository,
            directWatchTrafficSource = directWatchTrafficSource
        )
    }

    private fun liveFollowScope(
        defaultDispatcher: CoroutineDispatcher
    ): CoroutineScope = CoroutineScope(SupervisorJob() + defaultDispatcher)
}
