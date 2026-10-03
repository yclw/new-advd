package com.aeibi.avd.feature.workbench

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkbenchRoute(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.workbench_title)) },
                navigationIcon = {
                    TextButton(onClick = dropUnlessResumed { onNavigateBack() }) {
                        Text(stringResource(R.string.workbench_back))
                    }
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
