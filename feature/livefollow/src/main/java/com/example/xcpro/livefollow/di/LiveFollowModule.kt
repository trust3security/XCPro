package com.example.xcpro.livefollow.di

import com.example.xcpro.common.di.DefaultDispatcher
import com.example.xcpro.common.di.IoDispatcher
import com.example.xcpro.core.time.Clock
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.livefollow.account.XcAccountRepository
import com.example.xcpro.livefollow.data.following.CurrentApiFollowingActivePilotsDataSource
import com.example.xcpro.livefollow.data.following.FollowingActivePilotsDataSource
import com.example.xcpro.livefollow.data.following.FollowingLiveRepository
import com.example.xcpro.livefollow.data.ownship.FlightDataLiveOwnshipSnapshotSource
import com.example.xcpro.livefollow.data.ownship.LiveOwnshipSnapshotSource
import com.example.xcpro.livefollow.data.friends.ActivePilotsDataSource
import com.example.xcpro.livefollow.data.friends.CurrentApiActivePilotsDataSource
import com.example.xcpro.livefollow.data.friends.FriendsFlyingRepository
import com.example.xcpro.livefollow.data.session.CurrentApiLiveFollowSessionGateway
import com.example.xcpro.livefollow.data.session.LiveFollowSessionGateway
import com.example.xcpro.livefollow.data.session.LiveFollowSessionRepository
import com.example.xcpro.livefollow.data.task.LiveFollowTaskSnapshotSource
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
        xcAccountRepository: XcAccountRepository,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): LiveFollowSessionGateway {
        return CurrentApiLiveFollowSessionGateway(
            httpClient = httpClient,
            xcAccountRepository = xcAccountRepository,
            ioDispatcher = ioDispatcher
        )
    }

    @Provides
    @Singleton
    @LiveFollowRuntimeScope
    fun provideLiveFollowRuntimeScope(
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher
    ): CoroutineScope = CoroutineScope(SupervisorJob() + defaultDispatcher)

    @Provides
    @Singleton
    fun provideLiveOwnshipSnapshotSource(
        flightDataRepository: FlightDataRepository,
        ognTrafficPreferencesRepository: OgnTrafficPreferencesRepository,
        @LiveFollowRuntimeScope scope: CoroutineScope
    ): LiveOwnshipSnapshotSource {
        return FlightDataLiveOwnshipSnapshotSource(
            scope = scope,
            flightDataRepository = flightDataRepository,
            ownFlarmHexFlow = ognTrafficPreferencesRepository.ownFlarmHexFlow,
            ownIcaoHexFlow = ognTrafficPreferencesRepository.ownIcaoHexFlow
        )
    }

    @Provides
    @Singleton
    fun provideLiveFollowSessionRepository(
        ownshipSnapshotSource: LiveOwnshipSnapshotSource,
        taskSnapshotSource: LiveFollowTaskSnapshotSource,
        gateway: LiveFollowSessionGateway,
        @LiveFollowRuntimeScope scope: CoroutineScope
    ): LiveFollowSessionRepository {
        return LiveFollowSessionRepository(
            scope = scope,
            ownshipSnapshotSource = ownshipSnapshotSource,
            taskSnapshotSource = taskSnapshotSource,
            gateway = gateway
        )
    }

    @Provides
    @Singleton
    fun provideDirectWatchTrafficSource(
        clock: Clock,
        sessionRepository: LiveFollowSessionRepository,
        xcAccountRepository: XcAccountRepository,
        @LiveFollowHttpClient httpClient: OkHttpClient,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        @LiveFollowRuntimeScope scope: CoroutineScope
    ): DirectWatchTrafficSource {
        return CurrentApiDirectWatchTrafficSource(
            scope = scope,
            clock = clock,
            sessionState = sessionRepository.state,
            xcAccountRepository = xcAccountRepository,
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
        @LiveFollowRuntimeScope scope: CoroutineScope
    ): WatchTrafficRepository {
        return WatchTrafficRepository(
            scope = scope,
            clock = clock,
            sessionState = sessionRepository.state,
            ognTrafficRepository = ognTrafficRepository,
            directWatchTrafficSource = directWatchTrafficSource
        )
    }

    @Provides
    @Singleton
    fun provideActivePilotsDataSource(
        @LiveFollowHttpClient httpClient: OkHttpClient,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): ActivePilotsDataSource {
        return CurrentApiActivePilotsDataSource(
            httpClient = httpClient,
            ioDispatcher = ioDispatcher
        )
    }

    @Provides
    @Singleton
    fun provideFriendsFlyingRepository(
        ownshipSnapshotSource: LiveOwnshipSnapshotSource,
        activePilotsDataSource: ActivePilotsDataSource,
        @LiveFollowRuntimeScope scope: CoroutineScope
    ): FriendsFlyingRepository {
        return FriendsFlyingRepository(
            scope = scope,
            runtimeModeSource = ownshipSnapshotSource,
            dataSource = activePilotsDataSource
        )
    }

    @Provides
    @Singleton
    fun provideFollowingActivePilotsDataSource(
        xcAccountRepository: XcAccountRepository,
        @LiveFollowHttpClient httpClient: OkHttpClient,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): FollowingActivePilotsDataSource {
        return CurrentApiFollowingActivePilotsDataSource(
            xcAccountRepository = xcAccountRepository,
            httpClient = httpClient,
            ioDispatcher = ioDispatcher
        )
    }

    @Provides
    @Singleton
    fun provideFollowingLiveRepository(
        ownshipSnapshotSource: LiveOwnshipSnapshotSource,
        xcAccountRepository: XcAccountRepository,
        followingActivePilotsDataSource: FollowingActivePilotsDataSource,
        @LiveFollowRuntimeScope scope: CoroutineScope
    ): FollowingLiveRepository {
        return FollowingLiveRepository(
            scope = scope,
            runtimeModeSource = ownshipSnapshotSource,
            accountRepository = xcAccountRepository,
            dataSource = followingActivePilotsDataSource
        )
    }
}
