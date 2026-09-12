package com.aeibi.avd.app

import com.aeibi.avd.contract.projectruntime.AgentProjectRuntimeControl
import com.aeibi.avd.contract.projectruntime.PreviewProjectRuntimeControl
import com.aeibi.avd.domain.agent.AgentRuntimeCoordinator
import com.aeibi.avd.domain.preview.PreviewBackend
import com.aeibi.avd.domain.preview.PreviewRuntimeCoordinator
import com.aeibi.avd.domain.preview.UnavailablePreviewBackend
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** App is the composition root; these bindings do not give it runtime ownership. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ProjectRuntimeBindingsModule {
    @Binds
    abstract fun bindAgentProjectRuntimeControl(
        implementation: AgentRuntimeCoordinator
    ): AgentProjectRuntimeControl

    @Binds
    abstract fun bindPreviewProjectRuntimeControl(
        implementation: PreviewRuntimeCoordinator
    ): PreviewProjectRuntimeControl

    @Binds
    abstract fun bindPreviewBackend(implementation: UnavailablePreviewBackend): PreviewBackend
}
