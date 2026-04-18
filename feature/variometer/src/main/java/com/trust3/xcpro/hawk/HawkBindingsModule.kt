package com.trust3.xcpro.hawk

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
abstract class HawkBindingsModule {
    @Binds
    abstract fun bindHawkVarioPreviewReadPort(
        impl: HawkVarioUseCase
    ): HawkVarioPreviewReadPort
}
