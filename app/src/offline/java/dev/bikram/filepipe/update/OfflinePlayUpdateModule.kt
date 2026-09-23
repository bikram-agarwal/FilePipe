package dev.bikram.filepipe.update

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OfflinePlayUpdateModule {
    @Binds
    @Singleton
    abstract fun bindPlayUpdateSessionHandle(impl: OfflinePlayUpdateNoOp): PlayUpdateSessionHandle

    @Binds
    @Singleton
    abstract fun bindPlayInAppUpdateStarter(impl: OfflinePlayUpdateNoOp): PlayInAppUpdateStarter

    @Binds
    @Singleton
    abstract fun bindPlayInAppUpdateProgressController(
        impl: OfflinePlayUpdateNoOp,
    ): PlayInAppUpdateProgressController

    @Binds
    @Singleton
    abstract fun bindAppReviewLauncher(impl: OfflinePlayUpdateNoOp): AppReviewLauncher
}
