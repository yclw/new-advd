package com.aeibi.avd.feature.workbench

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.dropUnlessResumed
import com.aeibi.avd.core.common.ProjectId

enum class WorkbenchSection {
    Chat,
    Preview
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkbenchRoute(
    projectId: ProjectId,
    onNavigateBack: () -> Unit,
    onVersionsRequested: (ProjectId) -> Unit,
    onBuildRequested: (ProjectId) -> Unit,
    onSettingsRequested: () -> Unit,
    content: @Composable (WorkbenchSection, ProjectId) -> Unit
) {
    var section by rememberSaveable { mutableStateOf(WorkbenchSection.Chat) }

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
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            PrimaryScrollableTabRow(selectedTabIndex = section.ordinal) {
                WorkbenchSection.entries.forEach { item ->
                    Tab(
                        modifier = Modifier.testTag("workbench_${item.name.lowercase()}"),
                        selected = section == item,
                        onClick = { section = item },
                        text = { Text(stringResource(item.titleResource)) }
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                TextButton(onClick = { onVersionsRequested(projectId) }) {
                    Text(stringResource(R.string.workbench_versions))
                }
                TextButton(onClick = { onBuildRequested(projectId) }) {
                    Text(stringResource(R.string.workbench_build))
                }
                TextButton(onClick = onSettingsRequested) {
                    Text(stringResource(R.string.workbench_settings))
                }
            }
            Layout(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                content = {
                    WorkbenchSection.entries.forEach { item ->
                        key(item) {
                            Box(Modifier.fillMaxSize()) {
                                content(item, projectId)
                            }
                        }
                    }
                }
            ) { measurables, constraints ->
                val panes = measurables.map { it.measure(constraints) }
                layout(constraints.maxWidth, constraints.maxHeight) {
                    panes[section.ordinal].placeRelative(0, 0)
                }
            }
        }
    }
}

private val WorkbenchSection.titleResource: Int
    get() = when (this) {
        WorkbenchSection.Chat -> R.string.workbench_chat
        WorkbenchSection.Preview -> R.string.workbench_preview
    }
