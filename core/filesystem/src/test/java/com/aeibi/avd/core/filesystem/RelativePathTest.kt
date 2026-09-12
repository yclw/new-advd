package com.aeibi.avd.core.filesystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelativePathTest {
    @Test
    fun `accepts a nested relative path`() {
        assertEquals(
            "projects/project-a/project.json",
            RelativePath.of("projects/project-a/project.json")?.value
        )
    }

    @Test
    fun `rejects absolute and traversing paths`() {
        assertNull(RelativePath.of("/projects/project-a"))
        assertNull(RelativePath.of("projects/../project-a"))
        assertNull(RelativePath.of("projects/./project-a"))
    }
}
