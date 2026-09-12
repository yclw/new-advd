package com.aeibi.avd.data.project.project

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ProjectDataModule {
    @Binds
    abstract fun bindProjectRepository(implementation: DefaultProjectRepository): ProjectRepository
}
