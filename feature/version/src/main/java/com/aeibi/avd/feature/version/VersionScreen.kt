package com.aeibi.avd.feature.version

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import com.aeibi.avd.core.ui.NavigateBackButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VersionScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.version_title)) },
                navigationIcon = {
                    NavigateBackButton(
                        contentDescription = stringResource(R.string.version_back),
                        onClick = dropUnlessResumed { onNavigateBack() }
                    )
                },
                actions = {
                    TextButton(onClick = {}, enabled = false) {
                        Text(stringResource(R.string.version_create))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.version_history_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(stringResource(R.string.version_history_empty))
                }
            }
        }
    }
}
