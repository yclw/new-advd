package com.aeibi.avd.core.database.project

import androidx.room3.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aeibi.avd.core.database.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectDaoTest {
    @Test
    fun iconAndProfileUpdateAreCommittedTogether() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder<AppDatabase>(context).build()
        try {
            val dao = database.projectDao()
            val first = project("first", "First")
            val second = project("second", "Second")
            dao.insert(first, ProjectIconRow(first.id, "old", byteArrayOf(1)))
            dao.insert(second, null)

            val replacement = ProjectIconRow(first.id, "new", byteArrayOf(2))
            dao.update(first.copy(iconRevision = replacement.revision), replacement)
            assertEquals("new", dao.getProject(first.id)?.iconRevision)
            assertEquals(2, dao.getIcon(first.id, "new")?.single()?.toInt())
            assertEquals(1, dao.getIcon(first.id, "old")?.single()?.toInt())

            try {
                dao.update(
                    first.copy(nameKey = second.nameKey),
                    ProjectIconRow(first.id, "failed", byteArrayOf(3))
                )
                fail("The unique name key should reject this update")
            } catch (_: Exception) {
                // The icon insertion must roll back with the profile update.
            }
            assertNull(dao.getIcon(first.id, "failed"))
            assertEquals("new", dao.getProject(first.id)?.iconRevision)
            assertEquals(2, dao.observeProjects().first().size)
            assertEquals(2, dao.getProjectWithIcon(first.id)?.iconPng?.single()?.toInt())
            val observedIcon = dao.observeProjects().first()
                .first { it.project.id == first.id }.iconPng
            assertEquals(2, observedIcon?.single()?.toInt())

            dao.deleteProject(first.id)
            assertNull(dao.getIcon(first.id, "old"))
            assertNull(dao.getIcon(first.id, "new"))
        } finally {
            database.close()
        }
    }

    private fun project(id: String, name: String) = ProjectRow(
        id = id,
        name = name,
        nameKey = name.lowercase(),
        description = "",
        iconRevision = if (id == "first") "old" else null,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1
    )
}
