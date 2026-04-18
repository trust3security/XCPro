package com.trust3.xcpro.livefollow.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LiveFollowRuntimeScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class XcAccountScope
