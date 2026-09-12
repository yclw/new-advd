package com.aeibi.avd.feature.workbench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.aeibi.avd.core.common.ProjectId

/** Temporary entry point for a selected project until the workbench vertical slice is implemented. */
@Composable
fun WorkbenchRoute(projectId: ProjectId, onNavigateBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Project: ${projectId.value}")
        TextButton(onClick = onNavigateBack) { Text("Back to projects") }
    }
}
