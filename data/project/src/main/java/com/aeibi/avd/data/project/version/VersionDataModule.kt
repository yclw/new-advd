package com.aeibi.avd.data.project.version

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class VersionDataModule {
    @Binds
    abstract fun bindVersionRepository(implementation: DefaultVersionRepository): VersionRepository

    @Binds
    abstract fun bindProjectGitRepositoryLocator(
        implementation: AndroidProjectGitRepositoryLocator
    ): ProjectGitRepositoryLocator
}
