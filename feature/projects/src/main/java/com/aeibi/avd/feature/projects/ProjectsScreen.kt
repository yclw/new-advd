package com.aeibi.avd.feature.projects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import com.aeibi.avd.core.ui.ContentState
import com.aeibi.avd.core.ui.OperationState
import com.aeibi.avd.core.ui.UiError
import com.aeibi.avd.domain.project.ProjectIconChange
import com.aeibi.avd.feature.projects.component.ProjectList
import com.aeibi.avd.feature.projects.component.ProjectProfileDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(uiState: ProjectsUiState, onAction: (ProjectsAction) -> Unit) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var editProject by remember { mutableStateOf<ProjectItem?>(null) }
    var deleteProject by remember { mutableStateOf<ProjectItem?>(null) }
    val submitting = uiState.operation is OperationState.InProgress

    LaunchedEffect(uiState.operation) {
        if (uiState.operation is OperationState.Succeeded) {
            showCreateDialog = false
            editProject = null
            deleteProject = null
            onAction(ProjectsAction.AcknowledgeOperation)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.projects_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { if (!submitting) showCreateDialog = true }) {
                Text(stringResource(R.string.projects_create))
            }
        }
    ) { paddingValues ->
        when (val content = uiState.content) {
            ContentState.Loading -> LoadingContent(Modifier.padding(paddingValues))
            ContentState.Empty -> EmptyContent(Modifier.padding(paddingValues))
            is ContentState.Content -> ProjectList(
                projects = content.value,
                enabled = !submitting,
                modifier = Modifier.padding(paddingValues),
                onSelect = { onAction(ProjectsAction.SelectProject(it.id)) },
                onUpdate = { editProject = it },
                onDelete = { deleteProject = it }
            )
            is ContentState.Error -> ErrorContent(content.error, Modifier.padding(paddingValues)) {
                onAction(ProjectsAction.RetryListLoad)
            }
        }
    }

    if (showCreateDialog) {
        ProjectProfileDialog(
            title = stringResource(R.string.projects_create_title),
            confirmLabel = stringResource(R.string.projects_create),
            initialName = "",
            initialDescription = "",
            existingIcon = null,
            isSubmitting = submitting,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, description, iconChange ->
                onAction(
                    ProjectsAction.CreateConfirmed(
                        name,
                        description,
                        (iconChange as? ProjectIconChange.Replace)?.icon
                    )
                )
            },
            onIconPreparationResult = { onAction(ProjectsAction.IconPreparationCompleted(it)) }
        )
    }
    editProject?.let { project ->
        ProjectProfileDialog(
            title = stringResource(R.string.projects_edit_title),
            confirmLabel = stringResource(R.string.projects_save),
            initialName = project.name,
            initialDescription = project.description,
            existingIcon = project.icon,
            isSubmitting = submitting,
            onDismiss = { editProject = null },
            onConfirm = { name, description, iconChange ->
                onAction(ProjectsAction.UpdateConfirmed(project.id, name, description, iconChange))
            },
            onIconPreparationResult = { onAction(ProjectsAction.IconPreparationCompleted(it)) }
        )
    }
    deleteProject?.let { project ->
        AlertDialog(
            onDismissRequest = { if (!submitting) deleteProject = null },
            title = { Text(stringResource(R.string.projects_delete_title)) },
            text = { Text(stringResource(R.string.projects_delete_message, project.name)) },
            confirmButton = {
                TextButton(
                    enabled = !submitting,
                    onClick = { onAction(ProjectsAction.DeleteConfirmed(project.id)) },
                    modifier = Modifier.semantics { testTag = "project_delete_confirm" }
                ) { Text(stringResource(R.string.projects_delete)) }
            },
            dismissButton = {
                TextButton(enabled = !submitting, onClick = { deleteProject = null }) {
                    Text(stringResource(R.string.projects_cancel))
                }
            }
        )
    }
    (uiState.operation as? OperationState.Failed)?.let { failed ->
        ErrorDialog(failed.error) { onAction(ProjectsAction.AcknowledgeOperation) }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun EmptyContent(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.projects_empty))
    }
}

@Composable
private fun ErrorContent(error: UiError, modifier: Modifier = Modifier, onRetry: () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        TextButton(onClick = onRetry) { Text(projectsErrorText(error)) }
    }
}

@Composable
private fun ErrorDialog(error: UiError, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.projects_error_title)) },
        text = { Text(projectsErrorText(error)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.projects_cancel)) }
        }
    )
}

@Composable
private fun projectsErrorText(error: UiError): String = stringResource(
    projectsErrorResource(error)
)
