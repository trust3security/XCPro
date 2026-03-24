package com.example.xcpro.livefollow.di

import com.example.xcpro.livefollow.BuildConfig
import com.example.xcpro.livefollow.account.ConfiguredBuildTokenXcAccountAuthProvider
import com.example.xcpro.livefollow.account.ServerExchangeXcGoogleAuthGateway
import com.example.xcpro.livefollow.account.XcAccountAuthProvider
import com.example.xcpro.livefollow.account.XcAccountSessionStore
import com.example.xcpro.livefollow.account.XcAccountSessionStoreImpl
import com.example.xcpro.livefollow.account.XcGoogleAuthGateway
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier

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
    }
}
