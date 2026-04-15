package dev.bikram.filepipe.update

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GithubPlayUpdateModule {

    @Binds
    @Singleton
    abstract fun bindPlayUpdateSessionHandle(impl: GithubPlayUpdateNoOp): PlayUpdateSessionHandle

    @Binds
    @Singleton
    abstract fun bindPlayInAppUpdateStarter(impl: GithubPlayUpdateNoOp): PlayInAppUpdateStarter

    @Binds
    @Singleton
    abstract fun bindPlayInAppUpdateProgressController(
        impl: GithubPlayUpdateNoOp
    ): PlayInAppUpdateProgressController

    @Binds
    @Singleton
    abstract fun bindAppReviewLauncher(impl: GithubPlayUpdateNoOp): AppReviewLauncher
}
