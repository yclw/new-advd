package com.aeibi.avd.feature.projects.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.aeibi.avd.domain.project.ProjectIconChange
import com.aeibi.avd.domain.project.ProjectIconUpload
import com.aeibi.avd.feature.projects.ProjectIconPreview
import com.aeibi.avd.feature.projects.R
import com.aeibi.avd.feature.projects.bridge.IconPreparationResult
import com.aeibi.avd.feature.projects.bridge.ProjectIconPickerBridge

@Composable
internal fun ProjectProfileDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    initialDescription: String,
    existingIcon: ProjectIconPreview?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, ProjectIconChange) -> Unit,
    onIconPreparationResult: (IconPreparationResult) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var description by rememberSaveable { mutableStateOf(initialDescription) }
    var selectedIcon by remember { mutableStateOf<ProjectIconUpload?>(null) }
    var removeIcon by rememberSaveable { mutableStateOf(false) }
    val isNameValid = name.trim().isNotEmpty() && name.codePointCount(0, name.length) <= 60
    val iconChange = when {
        selectedIcon != null -> ProjectIconChange.Replace(selectedIcon!!)
        removeIcon -> ProjectIconChange.Remove
        else -> ProjectIconChange.Keep
    }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProjectIconPickerBridge(
                    existingIcon = if (removeIcon) null else existingIcon,
                    selectedIcon = selectedIcon,
                    onResult = { result ->
                        if (result is IconPreparationResult.Ready) {
                            selectedIcon = result.icon
                            removeIcon = false
                        }
                        onIconPreparationResult(result)
                    }
                )
                if (existingIcon != null || selectedIcon != null) {
                    TextButton(enabled = !isSubmitting, onClick = {
                        selectedIcon = null
                        removeIcon = true
                    }) { Text(stringResource(R.string.projects_remove_icon)) }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    enabled = !isSubmitting,
                    label = { Text(stringResource(R.string.projects_name_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { testTag = "project_profile_name" }
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    enabled = !isSubmitting,
                    label = { Text(stringResource(R.string.projects_description_label)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { testTag = "project_profile_description" }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = isNameValid && !isSubmitting,
                onClick = { onConfirm(name, description, iconChange) },
                modifier = Modifier.semantics { testTag = "project_profile_confirm" }
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(enabled = !isSubmitting, onClick = onDismiss) {
                Text(stringResource(R.string.projects_cancel))
            }
        }
    )
}
