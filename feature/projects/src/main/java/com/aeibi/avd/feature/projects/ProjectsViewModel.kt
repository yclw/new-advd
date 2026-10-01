package com.aeibi.avd.feature.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeibi.avd.core.common.OperationResult
import com.aeibi.avd.core.ui.ContentState
import com.aeibi.avd.core.ui.OperationState
import com.aeibi.avd.core.ui.UiMessage
import com.aeibi.avd.domain.project.CreateProjectRequest
import com.aeibi.avd.domain.project.CreateProjectUseCase
import com.aeibi.avd.domain.project.DeleteProjectUseCase
import com.aeibi.avd.domain.project.ObserveProjectsUseCase
import com.aeibi.avd.domain.project.RefreshProjectsUseCase
import com.aeibi.avd.domain.project.UpdateProjectProfileRequest
import com.aeibi.avd.domain.project.UpdateProjectProfileUseCase
import com.aeibi.avd.feature.projects.mapper.toProjectItem
import com.aeibi.avd.feature.projects.mapper.toProjectsUiError
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    observeProjects: ObserveProjectsUseCase,
    private val refreshProjects: RefreshProjectsUseCase,
    private val createProject: CreateProjectUseCase,
    private val updateProjectProfile: UpdateProjectProfileUseCase,
    private val deleteProject: DeleteProjectUseCase
) : ViewModel() {
    private val operation = MutableStateFlow<OperationState>(OperationState.Idle)
    private val effectsChannel = Channel<ProjectsEffect>(Channel.BUFFERED)

    private val content = observeProjects().map { result ->
        when (result) {
            is OperationResult.Failure -> ContentState.Error(result.error.toProjectsUiError())
            is OperationResult.Success -> {
                val items = result.value.map { project -> project.toProjectItem() }
                if (items.isEmpty()) ContentState.Empty else ContentState.Content(items)
            }
        }
    }

    val uiState: StateFlow<ProjectsUiState> = combine(content, operation) {
            projects,
            currentOperation
        ->
        ProjectsUiState(content = projects, operation = currentOperation)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProjectsUiState()
    )
    val effects = effectsChannel.receiveAsFlow()

    fun onAction(action: ProjectsAction) {
        when (action) {
            is ProjectsAction.CreateConfirmed -> perform {
                createProject(
                    CreateProjectRequest(action.name, action.description, action.icon)
                )
            }
            is ProjectsAction.UpdateConfirmed -> perform {
                updateProjectProfile(
                    UpdateProjectProfileRequest(
                        action.projectId,
                        action.name,
                        action.description,
                        action.iconChange
                    )
                )
            }
            is ProjectsAction.DeleteConfirmed -> perform { deleteProject(action.projectId) }
            ProjectsAction.RetryListLoad -> viewModelScope.launch { refreshProjects() }
            is ProjectsAction.SelectProject -> navigateToReadyProject(action.projectId)
            is ProjectsAction.IconPreparationCompleted -> when (action.result) {
                is com.aeibi.avd.feature.projects.bridge.IconPreparationResult.Ready -> Unit
                com.aeibi.avd.feature.projects.bridge.IconPreparationResult.Failed -> {
                    effectsChannel.trySend(
                        ProjectsEffect.ShowMessage(
                            UiMessage(
                                com.aeibi.avd.core.ui.UiError(
                                    messageKey = "project_icon_preparation_failed",
                                    retryable = true
                                )
                            )
                        )
                    )
                }
            }
            ProjectsAction.AcknowledgeOperation -> operation.value = OperationState.Idle
        }
    }

    private fun navigateToReadyProject(projectId: com.aeibi.avd.core.common.ProjectId) {
        val project = (uiState.value.content as? ContentState.Content)?.value
            ?.firstOrNull { it.id == projectId }
        if (project != null) {
            effectsChannel.trySend(ProjectsEffect.NavigateToProject(projectId))
        }
    }

    private fun perform(block: suspend () -> OperationResult<*>) {
        if (operation.value is OperationState.InProgress) return
        viewModelScope.launch {
            operation.value = OperationState.InProgress
            operation.value = when (val result = block()) {
                is OperationResult.Success -> OperationState.Succeeded(UUID.randomUUID().toString())
                is OperationResult.Failure -> OperationState.Failed(
                    result.error.toProjectsUiError()
                )
            }
        }
    }
}
