package com.trust3.xcpro.livefollow.di

import com.trust3.xcpro.common.di.DefaultDispatcher
import com.trust3.xcpro.livefollow.BuildConfig
import com.trust3.xcpro.livefollow.account.ConfiguredBuildTokenXcAccountAuthProvider
import com.trust3.xcpro.livefollow.account.ServerExchangeXcGoogleAuthGateway
import com.trust3.xcpro.livefollow.account.XcAccountAuthProvider
import com.trust3.xcpro.livefollow.account.XcAccountSessionStore
import com.trust3.xcpro.livefollow.account.XcAccountSessionStoreImpl
import com.trust3.xcpro.livefollow.account.XcGoogleAuthGateway
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ConfiguredDevBearerToken

@Module
@InstallIn(SingletonComponent::class)
abstract class LiveFollowAccountBindingsModule {

    @Binds
    abstract fun bindXcAccountSessionStore(
        impl: XcAccountSessionStoreImpl
    ): XcAccountSessionStore

    @Binds
    abstract fun bindXcAccountAuthProvider(
        impl: ConfiguredBuildTokenXcAccountAuthProvider
    ): XcAccountAuthProvider

    @Binds
    abstract fun bindXcGoogleAuthGateway(
        impl: ServerExchangeXcGoogleAuthGateway
    ): XcGoogleAuthGateway

    companion object {
        @Provides
        @ConfiguredDevBearerToken
        fun provideConfiguredDevBearerToken(): String {
            return BuildConfig.XCPRO_PRIVATE_FOLLOW_DEV_BEARER_TOKEN.trim()
        }

        @Provides
        @Singleton
        @XcAccountScope
        fun provideXcAccountScope(
            @DefaultDispatcher defaultDispatcher: CoroutineDispatcher
        ): CoroutineScope = CoroutineScope(SupervisorJob() + defaultDispatcher)
    }
}
