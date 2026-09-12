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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.aeibi.avd.core.common.OperationResult
import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.core.ui.OperationState
import com.aeibi.avd.domain.project.InitializeBlankProjectUseCase
import com.aeibi.avd.domain.project.RecoverProjectInitializationUseCase
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
    viewModel: ProjectSetupViewModel = viewModel()
) {
    val operation = viewModel.operation.collectAsStateWithLifecycle().value
    LaunchedEffect(viewModel) { viewModel.readyProjects.collect { onProjectReady(it) } }
    ProjectSetupScreen(
        retry = retry,
        operation = operation,
        onInitializeBlank = { viewModel.initializeBlank(projectId) },
        onNavigateBack = onNavigateBack
    )
}

@Composable
fun ProjectInitializationRoute(
    projectId: ProjectId,
    onProjectReady: (ProjectId) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ProjectInitializationViewModel = viewModel()
) {
    LaunchedEffect(projectId) { viewModel.recover(projectId) }
    LaunchedEffect(viewModel) { viewModel.readyProjects.collect { onProjectReady(it) } }
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

@HiltViewModel
class ProjectInitializationViewModel @Inject constructor(
    private val recoverProjectInitialization: RecoverProjectInitializationUseCase
) : ViewModel() {
    private val _readyProjects = kotlinx.coroutines.flow.MutableSharedFlow<ProjectId>()
    val readyProjects = _readyProjects

    fun recover(projectId: ProjectId) {
        viewModelScope.launch {
            when (val result = recoverProjectInitialization(projectId)) {
                is OperationResult.Success -> result.value?.takeIf {
                    it.status == com.aeibi.avd.core.model.ProjectStatus.READY
                }?.let { _readyProjects.emit(it.id) }
                is OperationResult.Failure -> Unit
            }
        }
    }
}

@Composable
private fun ProjectSetupScreen(
    retry: Boolean,
    operation: OperationState,
    onInitializeBlank: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val initializing = operation is OperationState.InProgress
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text(stringResource(if (retry) R.string.projects_retry_initialization else R.string.projects_setup))
        Button(enabled = !initializing, onClick = onInitializeBlank) {
            Text(stringResource(R.string.projects_initialize_blank))
        }
        Button(enabled = !initializing, onClick = onNavigateBack) {
            Text(stringResource(R.string.projects_back))
        }
        if (initializing) CircularProgressIndicator()
        if (operation is OperationState.Failed) Text(stringResource(R.string.projects_error_storage))
    }
}

@HiltViewModel
class ProjectSetupViewModel @Inject constructor(
    private val initializeBlankProject: InitializeBlankProjectUseCase
) : ViewModel() {
    private val _operation = MutableStateFlow<OperationState>(OperationState.Idle)
    val operation: StateFlow<OperationState> = _operation
    private val _readyProjects = kotlinx.coroutines.flow.MutableSharedFlow<ProjectId>()
    val readyProjects = _readyProjects

    fun initializeBlank(projectId: ProjectId) {
        if (_operation.value is OperationState.InProgress) return
        viewModelScope.launch {
            _operation.value = OperationState.InProgress
            when (initializeBlankProject(projectId)) {
                is OperationResult.Success -> {
                    _operation.value = OperationState.Idle
                    _readyProjects.emit(projectId)
                }
                is OperationResult.Failure -> _operation.value = OperationState.Failed(
                    com.aeibi.avd.core.ui.UiError("project_initialization_failed", retryable = true)
                )
            }
        }
    }
}
