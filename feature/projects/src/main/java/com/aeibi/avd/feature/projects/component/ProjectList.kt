package com.aeibi.avd.feature.projects.component

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aeibi.avd.feature.projects.ProjectItem
import com.aeibi.avd.feature.projects.R

@Composable
internal fun ProjectList(
    projects: List<ProjectItem>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSelect: (ProjectItem) -> Unit,
    onUpdate: (ProjectItem) -> Unit,
    onDelete: (ProjectItem) -> Unit
) {
    LazyColumn(modifier) {
        items(projects, key = { it.id.value }) { project ->
            ListItem(
                modifier = Modifier.clickable(enabled = enabled) { onSelect(project) },
                leadingContent = { ProjectIcon(project) },
                headlineContent = { Text(project.name) },
                supportingContent = { Text(project.description.ifBlank { project.status.name }) },
                trailingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(enabled = enabled, onClick = { onUpdate(project) }) {
                            Text(stringResource(R.string.projects_edit))
                        }
                        TextButton(enabled = enabled, onClick = { onDelete(project) }) {
                            Text(stringResource(R.string.projects_delete))
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ProjectIcon(project: ProjectItem) {
    val bytes = project.icon?.copyPngBytes()
    val image = remember(bytes?.contentHashCode()) {
        bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
        if (image == null) {
            Text(project.name.take(1).uppercase())
        } else {
            Image(
                bitmap = image,
                contentDescription = stringResource(R.string.projects_selected_icon),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}
