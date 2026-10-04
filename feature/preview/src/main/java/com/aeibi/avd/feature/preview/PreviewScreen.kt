package com.aeibi.avd.feature.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import com.aeibi.avd.core.ui.NavigateBackButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PreviewScreen(onNavigateBack: () -> Unit) {
    var showConsole by rememberSaveable { mutableStateOf(true) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.preview_title)) },
                navigationIcon = {
                    NavigateBackButton(
                        contentDescription = stringResource(R.string.preview_back),
                        onClick = dropUnlessResumed { onNavigateBack() }
                    )
                },
                actions = {
                    TextButton(onClick = { showConsole = !showConsole }) {
                        Text(
                            stringResource(
                                if (showConsole) {
                                    R.string.preview_hide_console
                                } else {
                                    R.string.preview_show_console
                                }
                            )
                        )
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
                Box(Modifier.fillMaxSize()) {
                    Text(
                        text = stringResource(R.string.preview_not_connected),
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            if (showConsole) {
                Card(modifier = Modifier.fillMaxWidth().height(136.dp)) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.preview_console_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(stringResource(R.string.preview_console_empty))
                    }
                }
            }
        }
    }
}
