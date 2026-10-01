package com.aeibi.avd.data.project.project

import com.aeibi.avd.core.common.OperationResult
import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.core.database.project.ProjectDao
import com.aeibi.avd.core.database.project.ProjectIconRow
import com.aeibi.avd.core.database.project.ProjectRow
import com.aeibi.avd.core.database.project.ProjectWithIconRow
import com.aeibi.avd.core.filesystem.ControlledFileSystem
import com.aeibi.avd.core.filesystem.FileSystemResult
import com.aeibi.avd.core.filesystem.RelativePath
import com.aeibi.avd.core.model.ProjectIcon
import com.aeibi.avd.data.project.ProjectMutationLease
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultProjectRepositoryTest {
    @Test
    fun `create persists a project with a workspace directory`() = runBlocking {
        val fileSystem = FakeFileSystem()
        val dao = FakeProjectDao()
        val repository = repository(fileSystem, dao)

        val project = repository.createProject("Demo", "A demo", null).successValue()

        assertEquals("A demo", project.description)
        assertNotNull(dao.getProject(project.id.value))
        assertTrue(fileSystem.directories.contains("projects/${project.id.value}/workspace"))
        assertFalse(fileSystem.directories.contains("projects/${project.id.value}/git"))
        assertEquals(listOf(project), repository.observeProjects().first().successValue())
    }

    @Test
    fun `custom icon survives reload and can be read`() = runBlocking {
        val fileSystem = FakeFileSystem()
        val dao = FakeProjectDao()
        val icon = ProjectIcon.fromPng(png512())
        val created = repository(fileSystem, dao)
            .createProject(
                name = "Demo",
                description = "",
                icon = icon
            )
            .successValue()

        val reloaded = repository(fileSystem, dao)
        val restored = reloaded.observeProjects().first().successValue().single()
        assertNotNull(restored.icon)
        assertEquals(icon, restored.icon)
        assertEquals(1, dao.icons.size)
    }

    @Test
    fun `update preserves historical icons when replacing or removing`() = runBlocking {
        val fileSystem = FakeFileSystem()
        val dao = FakeProjectDao()
        val repository = repository(fileSystem, dao)
        val created = repository.createProject(
            name = "Before",
            description = "",
            icon = ProjectIcon.fromPng(png512())
        ).successValue()

        val replaced = repository.updateProfile(
            created.id,
            "After",
            "Updated",
            ProjectIconDataChange.Replace(ProjectIcon.fromPng(png512()))
        ).successValue()
        val removed = repository.updateProfile(
            created.id,
            "After",
            "Updated",
            ProjectIconDataChange.Remove
        ).successValue()

        assertEquals(created.id, replaced.id)
        assertEquals("After", replaced.name)
        assertNotNull(replaced.icon)
        assertEquals(null, removed.icon)
        assertEquals(2, dao.icons.size)
    }

    @Test
    fun `duplicate names are case insensitive`() = runBlocking {
        val repository = repository(FakeFileSystem(), FakeProjectDao())
        repository.createProject("Demo", "", null)

        val result = repository.createProject("demo", "", null)

        assertEquals(ProjectDataError.NameAlreadyExists, (result as OperationResult.Failure).error)
    }

    @Test
    fun `delete is idempotent`() = runBlocking {
        val repository = repository(FakeFileSystem(), FakeProjectDao())
        val created = repository.createProject("Demo", "", null).successValue()

        assertTrue(repository.delete(created.id) is OperationResult.Success)
        assertTrue(repository.delete(ProjectId("already-gone")) is OperationResult.Success)
        assertFalse(repository.observeProjects().first().successValue().any { it.id == created.id })
    }

    private fun repository(fileSystem: FakeFileSystem, dao: FakeProjectDao) =
        DefaultProjectRepository(
            projectDao = dao,
            fileSystem = fileSystem,
            mutationLease = ProjectMutationLease()
        )

    private class FakeProjectDao : ProjectDao {
        private val projects = MutableStateFlow<List<ProjectRow>>(emptyList())
        val icons = mutableMapOf<Pair<String, String>, ByteArray>()

        override fun observeProjects() = projects.map { rows -> rows.map(::withIcon) }

        override suspend fun getProjects(): List<ProjectWithIconRow> =
            projects.value.map(::withIcon)

        override suspend fun getProjectWithIcon(id: String): ProjectWithIconRow? =
            getProject(id)?.let(::withIcon)

        override suspend fun getProject(id: String): ProjectRow? =
            projects.value.firstOrNull { it.id == id }

        override suspend fun nameExists(nameKey: String, exceptId: String): Boolean =
            projects.value.any { it.nameKey == nameKey && it.id != exceptId }

        override suspend fun getIcon(projectId: String, revision: String): ByteArray? =
            icons[projectId to revision]?.copyOf()

        private fun withIcon(project: ProjectRow): ProjectWithIconRow = ProjectWithIconRow(
            project = project,
            iconPng = project.iconRevision?.let { icons[project.id to it]?.copyOf() }
        )

        override suspend fun insertProject(project: ProjectRow) {
            projects.value = projects.value + project
        }

        override suspend fun insertIcon(icon: ProjectIconRow) {
            icons[icon.projectId to icon.revision] = icon.png.copyOf()
        }

        override suspend fun updateProject(project: ProjectRow) {
            projects.value = projects.value.map { if (it.id == project.id) project else it }
        }

        override suspend fun deleteProject(id: String) {
            projects.value = projects.value.filterNot { it.id == id }
            icons.keys.removeAll { it.first == id }
        }
    }

    private class FakeFileSystem : ControlledFileSystem {
        val directories = mutableSetOf<String>()
        val texts = mutableMapOf<String, String>()
        val bytes = mutableMapOf<String, ByteArray>()

        override suspend fun createDirectories(path: RelativePath): FileSystemResult<Unit> {
            directories += path.value
            return FileSystemResult.Success(Unit)
        }

        override suspend fun listDirectories(path: RelativePath): FileSystemResult<List<String>> {
            val prefix = "${path.value}/"
            return FileSystemResult.Success(
                directories.mapNotNull { directory ->
                    directory.removePrefix(prefix).takeIf { it.isNotEmpty() && '/' !in it }
                }
            )
        }

        override suspend fun readText(path: RelativePath): FileSystemResult<String?> =
            FileSystemResult.Success(texts[path.value])

        override suspend fun writeTextAtomically(
            path: RelativePath,
            content: String
        ): FileSystemResult<Unit> {
            texts[path.value] = content
            return FileSystemResult.Success(Unit)
        }

        override suspend fun readBytes(path: RelativePath): FileSystemResult<ByteArray?> =
            FileSystemResult.Success(bytes[path.value]?.copyOf())

        override suspend fun writeBytesAtomically(
            path: RelativePath,
            bytes: ByteArray
        ): FileSystemResult<Unit> {
            this.bytes[path.value] = bytes.copyOf()
            return FileSystemResult.Success(Unit)
        }

        override suspend fun moveDirectoryAtomically(
            source: RelativePath,
            destination: RelativePath
        ): FileSystemResult<Unit> {
            moveEntries(directories, source.value, destination.value)
            moveEntries(texts, source.value, destination.value)
            moveEntries(bytes, source.value, destination.value)
            directories += destination.value
            return FileSystemResult.Success(Unit)
        }

        override suspend fun deleteFile(path: RelativePath): FileSystemResult<Unit> {
            bytes.remove(path.value)
            texts.remove(path.value)
            return FileSystemResult.Success(Unit)
        }

        override suspend fun deleteDirectory(path: RelativePath): FileSystemResult<Unit> {
            val prefix = "${path.value}/"
            directories.removeAll { it == path.value || it.startsWith(prefix) }
            texts.keys.removeAll { it.startsWith(prefix) }
            bytes.keys.removeAll { it.startsWith(prefix) }
            return FileSystemResult.Success(Unit)
        }

        override suspend fun exists(path: RelativePath): FileSystemResult<Boolean> =
            FileSystemResult.Success(directories.contains(path.value))

        private fun <T> moveEntries(
            values: MutableMap<String, T>,
            source: String,
            destination: String
        ) {
            val prefix = "$source/"
            values.filterKeys { it.startsWith(prefix) }.toMap().forEach { (path, value) ->
                values.remove(path)
                values["$destination/${path.removePrefix(prefix)}"] = value
            }
        }

        private fun moveEntries(values: MutableSet<String>, source: String, destination: String) {
            val prefix = "$source/"
            values.filter { it == source || it.startsWith(prefix) }.toList().forEach { path ->
                values.remove(path)
                val target = if (path == source) {
                    destination
                } else {
                    "$destination/${path.removePrefix(prefix)}"
                }
                values += target
            }
        }
    }
}

private fun <T> OperationResult<T>.successValue(): T = (this as OperationResult.Success).value

private fun png512(): ByteArray = byteArrayOf(
    137.toByte(), 80, 78, 71, 13, 10, 26, 10,
    0, 0, 0, 13, 'I'.code.toByte(), 'H'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte(),
    0, 0, 2, 0, 0, 0, 2, 0
)
