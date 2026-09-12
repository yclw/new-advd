package com.aeibi.avd.feature.projects

import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.core.model.ProjectStatus
import com.aeibi.avd.core.ui.ContentState
import com.aeibi.avd.core.ui.OperationState

data class ProjectsUiState(
    val content: ContentState<List<ProjectItem>> = ContentState.Loading,
    val operation: OperationState = OperationState.Idle
)

data class ProjectItem(
    val id: ProjectId,
    val name: String,
    val description: String,
    val status: ProjectStatus,
    val icon: ProjectIconPreview?
)

class ProjectIconPreview private constructor(private val pngBytes: ByteArray) {
    fun copyPngBytes(): ByteArray = pngBytes.copyOf()

    companion object {
        fun fromPng(bytes: ByteArray): ProjectIconPreview = ProjectIconPreview(bytes.copyOf())
    }
}
