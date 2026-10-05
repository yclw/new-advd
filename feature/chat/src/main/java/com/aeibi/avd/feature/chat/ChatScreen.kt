package com.aeibi.avd.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import com.aeibi.avd.core.ui.NavigateBackButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatScreen(
    uiState: ChatUiState,
    onDraftChange: (String) -> Unit,
    onClearDraft: () -> Unit,
    onNewSession: () -> Unit,
    onSelectSession: (String) -> Unit,
    onSend: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToPreview: () -> Unit,
    onNavigateToBuild: () -> Unit,
    onNavigateToVersion: () -> Unit
) {
    var pagesExpanded by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.chat_sessions),
                        style = MaterialTheme.typography.titleLarge
                    )
                    TextButton(onClick = {
                        onNewSession()
                        scope.launch { drawerState.close() }
                    }) { Text(stringResource(R.string.chat_new_session)) }
                }
                if (uiState.sessions.isEmpty()) {
                    Text(
                        stringResource(R.string.chat_no_sessions),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn {
                        items(uiState.sessions, key = { it.id }) { session ->
                            Surface(
                                color = if (session.id == uiState.selectedSessionId) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(
                                    horizontal = 12.dp,
                                    vertical = 3.dp
                                )
                                    .clickable {
                                        onSelectSession(session.id)
                                        scope.launch { drawerState.close() }
                                    }
                            ) {
                                Text(
                                    session.title.ifBlank {
                                        stringResource(R.string.chat_new_session)
                                    },
                                    modifier = Modifier.padding(14.dp),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(topBar = {
            TopAppBar(
                title = {
                    Text(
                        uiState.selectedSession?.title?.ifBlank {
                            stringResource(R.string.chat_title)
                        }
                            ?: stringResource(R.string.chat_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    NavigateBackButton(
                        contentDescription = stringResource(R.string.chat_back),
                        onClick = dropUnlessResumed { onNavigateBack() }
                    )
                },
                actions = {
                    TextButton(onClick = { scope.launch { drawerState.open() } }) {
                        Text(stringResource(R.string.chat_sessions))
                    }
                    TextButton(onClick = onNewSession) {
                        Text(stringResource(R.string.chat_new_session))
                    }
                    TextButton(onClick = {
                        pagesExpanded = true
                    }) { Text(stringResource(R.string.chat_pages)) }
                    DropdownMenu(expanded = pagesExpanded, onDismissRequest = {
                        pagesExpanded =
                            false
                    }) {
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
        }) { paddingValues ->
            Column(
                modifier = Modifier.fillMaxSize().padding(
                    paddingValues
                ).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                key(uiState.selectedSessionId) {
                    ChatMessageList(uiState.selectedSession, Modifier.fillMaxWidth().weight(1f))
                }
                Text(
                    stringResource(R.string.chat_demo_notice),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = uiState.draft,
                        onValueChange = onDraftChange,
                        label = { Text(stringResource(R.string.chat_prompt_label)) },
                        modifier = Modifier.weight(1f),
                        minLines = 1,
                        maxLines = 4,
                        trailingIcon = {
                            if (uiState.draft.isNotEmpty()) {
                                TextButton(onClick = onClearDraft) {
                                    Text(stringResource(R.string.chat_clear_draft))
                                }
                            }
                        }
                    )
                    Button(onClick = onSend, enabled = uiState.draft.isNotBlank()) {
                        Text(stringResource(R.string.chat_send))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageList(session: ChatSessionUi?, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var atBottom by remember(session?.id) { mutableStateOf(true) }
    val messages = session?.messages.orEmpty()
    val latestMessages by rememberUpdatedState(messages)
    fun List<LazyListItemInfo>.isAtBottom(): Boolean {
        val lastVisible = lastOrNull() ?: return false
        val layout = listState.layoutInfo
        return lastVisible.index == layout.totalItemsCount - 1 &&
            lastVisible.offset + lastVisible.size <= layout.viewportEndOffset - 8
    }
    LaunchedEffect(listState, session?.id) {
        snapshotFlow { !listState.canScrollForward }.collectLatest { bottom ->
            if (!bottom) delay(500)
            atBottom = bottom
        }
    }
    LaunchedEffect(listState, session?.id) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }.collect { visibleItems ->
            if (!listState.isScrollInProgress &&
                latestMessages.lastOrNull()?.streaming == true &&
                visibleItems.isAtBottom()
            ) {
                listState.requestScrollToItem(listState.layoutInfo.totalItemsCount - 1)
            }
        }
    }
    Box(modifier) {
        if (messages.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.chat_empty_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(R.string.chat_empty_message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (message.role ==
                            ChatRole.User
                        ) {
                            Arrangement.End
                        } else {
                            Arrangement.Start
                        }
                    ) {
                        if (message.role == ChatRole.User) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.widthIn(max = 320.dp)
                            ) { Text(message.markdown, modifier = Modifier.padding(12.dp)) }
                        } else {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    stringResource(R.string.chat_assistant_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (message.markdown.isNotEmpty()) {
                                    StreamingMarkdown(
                                        text = message.markdown,
                                        streaming = message.streaming,
                                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                                    )
                                }
                                if (message.streaming) {
                                    Text(
                                        "●",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "bottom") { Box(Modifier.padding(bottom = 8.dp)) }
            }
        }
        if (!atBottom && messages.isNotEmpty()) {
            Button(
                onClick = {
                    scope.launch {
                        listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1)
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
            ) { Text(stringResource(R.string.chat_scroll_bottom)) }
        }
    }
}
