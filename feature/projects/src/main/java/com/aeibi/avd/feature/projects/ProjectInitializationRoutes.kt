package com.aeibi.avd.feature.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aeibi.avd.core.common.OperationResult
import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.core.ui.OperationState
import com.aeibi.avd.core.ui.UiError
import com.aeibi.avd.domain.project.InitializeBlankProjectUseCase
import com.aeibi.avd.domain.project.RecoverProjectInitializationUseCase
import com.aeibi.avd.feature.projects.mapper.toProjectsUiError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun ProjectSetupRoute(
    projectId: ProjectId,
    retry: Boolean,
    onProjectReady: (ProjectId) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ProjectSetupViewModel = viewModel(key = projectId.value)
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    LaunchedEffect(viewModel) { viewModel.readyProjects.collect { onProjectReady(it) } }
    ProjectSetupScreen(
        retry = retry,
        uiState = uiState,
        onInitializeBlank = { viewModel.initializeBlank(projectId) },
        onNavigateBack = onNavigateBack
    )
}

@Composable
fun ProjectInitializationRoute(
    projectId: ProjectId,
    onProjectReady: (ProjectId) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ProjectInitializationViewModel = viewModel(key = projectId.value)
) {
    LaunchedEffect(projectId) { viewModel.recover(projectId) }
    LaunchedEffect(viewModel) { viewModel.readyProjects.collect { onProjectReady(it) } }
    when (val uiState = viewModel.uiState.collectAsStateWithLifecycle().value) {
        ProjectInitializationUiState.Loading -> InitializationLoadingScreen(onNavigateBack)
        is ProjectInitializationUiState.RecoveryRequired -> RecoveryRequiredScreen(
            error = uiState.error,
            onRetry = { viewModel.recover(projectId) },
            onNavigateBack = onNavigateBack
        )
    }
}

@HiltViewModel
class ProjectInitializationViewModel @Inject constructor(
    private val recoverProjectInitialization: RecoverProjectInitializationUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow<ProjectInitializationUiState>(
        ProjectInitializationUiState.Loading
    )
    val uiState: StateFlow<ProjectInitializationUiState> = _uiState
    private val _readyProjects = kotlinx.coroutines.flow.MutableSharedFlow<ProjectId>()
    val readyProjects = _readyProjects

    fun recover(projectId: ProjectId) {
        viewModelScope.launch {
            _uiState.value = ProjectInitializationUiState.Loading
            when (val result = recoverProjectInitialization(projectId)) {
                is OperationResult.Success -> result.value?.takeIf {
                    it.status == com.aeibi.avd.core.model.ProjectStatus.READY
                }?.let { _readyProjects.emit(it.id) } ?: run {
                    _uiState.value = ProjectInitializationUiState.RecoveryRequired(
                        UiError("project_initialization_failed", retryable = true)
                    )
                }
                is OperationResult.Failure ->
                    _uiState.value =
                        ProjectInitializationUiState.RecoveryRequired(
                            result.error.toProjectsUiError()
                        )
            }
        }
    }
}

sealed interface ProjectInitializationUiState {
    data object Loading : ProjectInitializationUiState
    data class RecoveryRequired(val error: UiError) : ProjectInitializationUiState
}

@Composable
private fun ProjectSetupScreen(
    retry: Boolean,
    uiState: ProjectSetupUiState,
    onInitializeBlank: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val operation = uiState.operation
    val initializing = operation is OperationState.InProgress
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text(
            stringResource(
                if (retry) R.string.projects_retry_initialization else R.string.projects_setup
            )
        )
        Button(enabled = !initializing, onClick = onInitializeBlank) {
            Text(stringResource(R.string.projects_initialize_blank))
        }
        Button(enabled = !initializing, onClick = onNavigateBack) {
            Text(stringResource(R.string.projects_back))
        }
        if (initializing) CircularProgressIndicator()
        (operation as? OperationState.Failed)?.let { failed ->
            Text(stringResource(projectsErrorResource(failed.error)))
        }
    }
}

@Composable
private fun InitializationLoadingScreen(onNavigateBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        CircularProgressIndicator()
        Text(stringResource(R.string.projects_initializing))
        Button(onClick = onNavigateBack) { Text(stringResource(R.string.projects_back)) }
    }
}

@Composable
private fun RecoveryRequiredScreen(
    error: UiError,
    onRetry: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text(stringResource(projectsErrorResource(error)))
        Button(onClick = onRetry) { Text(stringResource(R.string.projects_retry)) }
        Button(onClick = onNavigateBack) { Text(stringResource(R.string.projects_back)) }
    }
}

@HiltViewModel
class ProjectSetupViewModel @Inject constructor(
    private val initializeBlankProject: InitializeBlankProjectUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProjectSetupUiState())
    val uiState: StateFlow<ProjectSetupUiState> = _uiState
    private val _readyProjects = kotlinx.coroutines.flow.MutableSharedFlow<ProjectId>()
    val readyProjects = _readyProjects

    fun initializeBlank(projectId: ProjectId) {
        if (_uiState.value.operation is OperationState.InProgress) return
        viewModelScope.launch {
            _uiState.value = ProjectSetupUiState(OperationState.InProgress)
            when (val result = initializeBlankProject(projectId)) {
                is OperationResult.Success -> {
                    _uiState.value = ProjectSetupUiState()
                    _readyProjects.emit(projectId)
                }
                is OperationResult.Failure -> _uiState.value = ProjectSetupUiState(
                    OperationState.Failed(result.error.toProjectsUiError())
                )
            }
        }
    }
}

data class ProjectSetupUiState(val operation: OperationState = OperationState.Idle)
