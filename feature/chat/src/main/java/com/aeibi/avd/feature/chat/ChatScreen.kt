package com.aeibi.avd.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
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
internal fun ChatScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPreview: () -> Unit,
    onNavigateToBuild: () -> Unit,
    onNavigateToVersion: () -> Unit
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var pagesExpanded by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chat_title)) },
                navigationIcon = {
                    NavigateBackButton(
                        contentDescription = stringResource(R.string.chat_back),
                        onClick = dropUnlessResumed { onNavigateBack() }
                    )
                },
                actions = {
                    TextButton(onClick = { draft = "" }, enabled = draft.isNotEmpty()) {
                        Text(stringResource(R.string.chat_clear_draft))
                    }
                    TextButton(onClick = { pagesExpanded = true }) {
                        Text(stringResource(R.string.chat_pages))
                    }
                    DropdownMenu(
                        expanded = pagesExpanded,
                        onDismissRequest = { pagesExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_open_preview)) },
                            onClick = dropUnlessResumed {
                                pagesExpanded = false
                                onNavigateToPreview()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_open_build)) },
                            onClick = dropUnlessResumed {
                                pagesExpanded = false
                                onNavigateToBuild()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_open_version)) },
                            onClick = dropUnlessResumed {
                                pagesExpanded = false
                                onNavigateToVersion()
                            }
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
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.chat_empty_title))
                Text(stringResource(R.string.chat_empty_message))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text(stringResource(R.string.chat_prompt_label)) },
                    modifier = Modifier.weight(1f),
                    minLines = 1,
                    maxLines = 4
                )
                Button(onClick = {}, enabled = false, modifier = Modifier.align(Alignment.Bottom)) {
                    Text(stringResource(R.string.chat_send))
                }
            }
        }
    }
}
