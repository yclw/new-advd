package com.aeibi.avd.feature.workbench

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.core.ui.NavigateBackButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkbenchRoute(projectId: ProjectId, onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.workbench_title)) },
                navigationIcon = {
                    NavigateBackButton(
                        contentDescription = stringResource(R.string.workbench_back),
                        onClick = dropUnlessResumed { onNavigateBack() }
                    )
                }
            )
        }
    ) { paddingValues ->
        Text(
            text = stringResource(R.string.workbench_placeholder),
            modifier = Modifier.padding(paddingValues).padding(24.dp)
        )
    }
}
