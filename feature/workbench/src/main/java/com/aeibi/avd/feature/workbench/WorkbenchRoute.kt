package com.aeibi.avd.feature.workbench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.aeibi.avd.domain.version.GetInitialVersionStateUseCase
import com.aeibi.avd.domain.version.RecordInitialVersionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun WorkbenchRoute(
    projectId: ProjectId,
    onNavigateBack: () -> Unit,
    viewModel: WorkbenchViewModel = viewModel(key = projectId.value)
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    LaunchedEffect(projectId) { viewModel.load(projectId) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
        ) {
            Text(stringResource(R.string.workbench_project, projectId.value))
            when {
                state.loading || state.working -> CircularProgressIndicator()
                state.recorded -> Text(
                    stringResource(
                        if (state.hasUnrecordedChanges) {
                            R.string.workbench_unrecorded_changes
                        } else {
                            R.string.workbench_version_recorded
                        }
                    )
                )
                else -> {
                    Text(stringResource(R.string.workbench_version_off))
                    TextButton(onClick = { viewModel.record(projectId) }) {
                        Text(stringResource(R.string.workbench_record_first_version))
                    }
                }
            }
            if (state.error) Text(stringResource(R.string.workbench_version_error))
            TextButton(onClick = onNavigateBack) { Text(stringResource(R.string.workbench_back)) }
        }
    }

    if (state.showPrompt && !state.working) {
        AlertDialog(
            onDismissRequest = viewModel::postpone,
            title = { Text(stringResource(R.string.workbench_version_prompt_title)) },
            text = {
                Text(
                    stringResource(
                        if (state.error) {
                            R.string.workbench_version_error
                        } else {
                            R.string.workbench_version_prompt_message
                        }
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.record(projectId) }) {
                    Text(stringResource(R.string.workbench_record_first_version))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::postpone) {
                    Text(stringResource(R.string.workbench_later))
                }
            }
        )
    }
}

data class WorkbenchVersionState(
    val loading: Boolean = true,
    val working: Boolean = false,
    val recorded: Boolean = false,
    val hasUnrecordedChanges: Boolean = false,
    val showPrompt: Boolean = false,
    val error: Boolean = false
)

@HiltViewModel
class WorkbenchViewModel @Inject constructor(
    private val getInitialVersionState: GetInitialVersionStateUseCase,
    private val recordInitialVersion: RecordInitialVersionUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(WorkbenchVersionState())
    val state: StateFlow<WorkbenchVersionState> = _state

    fun load(projectId: ProjectId) {
        viewModelScope.launch {
            _state.value = WorkbenchVersionState()
            _state.value = when (val result = getInitialVersionState(projectId)) {
                is OperationResult.Success -> WorkbenchVersionState(
                    loading = false,
                    recorded = result.value.recorded,
                    hasUnrecordedChanges = result.value.hasUnrecordedChanges,
                    showPrompt = !result.value.recorded
                )
                is OperationResult.Failure -> WorkbenchVersionState(loading = false, error = true)
            }
        }
    }

    fun record(projectId: ProjectId) {
        if (_state.value.working) return
        viewModelScope.launch {
            _state.value = _state.value.copy(working = true, error = false)
            _state.value = when (recordInitialVersion(projectId)) {
                is OperationResult.Success -> {
                    val state = getInitialVersionState(projectId)
                    when (state) {
                        is OperationResult.Success -> WorkbenchVersionState(
                            loading = false,
                            recorded = true,
                            hasUnrecordedChanges = state.value.hasUnrecordedChanges
                        )
                        is OperationResult.Failure -> WorkbenchVersionState(
                            loading = false,
                            recorded = true,
                            error = true
                        )
                    }
                }
                is OperationResult.Failure -> _state.value.copy(working = false, error = true)
            }
        }
    }

    fun postpone() {
        if (_state.value.working) return
        _state.value = _state.value.copy(showPrompt = false)
    }
}
