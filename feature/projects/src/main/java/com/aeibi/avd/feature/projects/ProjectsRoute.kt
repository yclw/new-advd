package com.aeibi.avd.feature.projects

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aeibi.avd.core.common.ProjectId
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ProjectsRoute(
    onProjectSelected: (ProjectId) -> Unit,
    onProjectSetupRequested: (ProjectId, Boolean) -> Unit,
    onInitializationProgressRequested: (ProjectId) -> Unit,
    onSettingsRequested: () -> Unit,
    viewModel: ProjectsViewModel = viewModel()
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val resources = LocalResources.current
    LaunchedEffect(viewModel, context, resources) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is ProjectsEffect.NavigateToProject -> onProjectSelected(effect.projectId)
                is ProjectsEffect.NavigateToProjectSetup -> onProjectSetupRequested(
                    effect.projectId,
                    effect.retry
                )
                is ProjectsEffect.NavigateToInitializationProgress ->
                    onInitializationProgressRequested(effect.projectId)
                is ProjectsEffect.ShowMessage -> Toast.makeText(
                    context,
                    resources.getString(projectsErrorResource(effect.message.error)),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    ProjectsScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        onSettingsRequested = onSettingsRequested
    )
}
