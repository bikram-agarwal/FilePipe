package dev.bikram.filepipe.update

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayStorePlayUpdateModule {

    @Binds
    @Singleton
    abstract fun bindPlayUpdateSessionHandle(session: PlayInAppUpdateSession): PlayUpdateSessionHandle

    @Binds
    @Singleton
    abstract fun bindPlayInAppUpdateStarter(
        impl: PlayStorePlayInAppUpdateStarter
    ): PlayInAppUpdateStarter

    @Binds
    @Singleton
    abstract fun bindPlayInAppUpdateProgressController(
        impl: PlayStorePlayInAppUpdateCoordinator
    ): PlayInAppUpdateProgressController

    @Binds
    @Singleton
    abstract fun bindAppReviewLauncher(impl: PlayStoreAppReviewLauncher): AppReviewLauncher
}
