package com.aeibi.avd.data.project

import com.aeibi.avd.core.common.ProjectId
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectStorageLayoutTest {
    @Test
    fun `derives every project path from its id`() {
        val projectId = ProjectId("sample")

        assertEquals("projects", ProjectStorageLayout.projectsDirectory.value)
        assertEquals("projects/sample", ProjectStorageLayout.projectDirectory(projectId).value)
        assertEquals(
            "projects/sample/project.json",
            ProjectStorageLayout.metadataPath(projectId).value
        )
        assertEquals(
            "projects/sample/workspace",
            ProjectStorageLayout.workspaceDirectory(projectId).value
        )
    }
}
