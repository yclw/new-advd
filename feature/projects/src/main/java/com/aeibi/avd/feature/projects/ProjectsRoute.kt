package com.aeibi.avd.feature.projects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aeibi.avd.core.common.ProjectId
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ProjectsRoute(
    onProjectSelected: (ProjectId) -> Unit,
    onProjectSetupRequested: (ProjectId, Boolean) -> Unit,
    onInitializationProgressRequested: (ProjectId) -> Unit,
    viewModel: ProjectsViewModel = viewModel()
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is ProjectsEffect.NavigateToProject -> onProjectSelected(effect.projectId)
                is ProjectsEffect.NavigateToProjectSetup -> onProjectSetupRequested(
                    effect.projectId,
                    effect.retry
                )
                is ProjectsEffect.NavigateToInitializationProgress ->
                    onInitializationProgressRequested(effect.projectId)
            }
        }
    }
    ProjectsScreen(uiState = uiState, onAction = viewModel::onAction)
}
