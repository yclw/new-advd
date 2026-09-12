package com.aeibi.avd.data.project.project

import com.aeibi.avd.core.common.OperationResult
import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.core.filesystem.ControlledFileSystem
import com.aeibi.avd.core.filesystem.FileSystemResult
import com.aeibi.avd.core.filesystem.RelativePath
import com.aeibi.avd.core.git.ControlledGit
import com.aeibi.avd.core.git.GitCommit
import com.aeibi.avd.core.git.GitCommitRequest
import com.aeibi.avd.core.git.GitRepositoryLocation
import com.aeibi.avd.core.git.GitResult
import com.aeibi.avd.core.git.GitRevision
import com.aeibi.avd.core.git.GitStatus
import com.aeibi.avd.core.model.ProjectStatus
import com.aeibi.avd.data.project.ProjectMutationLease
import com.aeibi.avd.data.project.version.ProjectGitRepositoryLocator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultProjectRepositoryTest {
    @Test
    fun `create persists a draft project without a workspace directory`() = runBlocking {
        val fileSystem = FakeFileSystem()
        val repository = repository(fileSystem)

        val project = repository.createDraft("Demo", "A demo", null).successValue()

        assertEquals(ProjectStatus.DRAFT, project.status)
        assertEquals("A demo", project.description)
        assertTrue(fileSystem.texts.containsKey("projects/${project.id.value}/project.json"))
        assertFalse(fileSystem.directories.contains("projects/${project.id.value}/workspace"))
        assertEquals(listOf(project), repository.observeProjects().first().successValue())
    }

    @Test
    fun `custom icon survives reload and can be read`() = runBlocking {
        val fileSystem = FakeFileSystem()
        val icon = ProjectIconData.fromPng(png512())
        val created = repository(fileSystem)
            .createDraft(
                name = "Demo",
                description = "",
                icon = icon
            )
            .successValue()

        val reloaded = repository(fileSystem)
        val restored = reloaded.observeProjects().first().successValue().single()
        val loadedIcon = reloaded.loadIcon(created.id).successValue()

        assertTrue(restored.hasCustomIcon)
        assertNotNull(loadedIcon)
        assertTrue(loadedIcon!!.copyPngBytes().contentEquals(png512()))
        assertNotNull(
            fileSystem.bytes.keys.single()
                .substringAfterLast("icon-")
                .removeSuffix(".png")
                .toLongOrNull()
        )
    }

    @Test
    fun `update preserves historical icons when replacing or removing`() = runBlocking {
        val fileSystem = FakeFileSystem()
        val repository = repository(fileSystem)
        val created = repository.createDraft(
            name = "Before",
            description = "",
            icon = ProjectIconData.fromPng(png512())
        ).successValue()

        val replaced = repository.updateProfile(
            created.id,
            "After",
            "Updated",
            ProjectIconDataChange.Replace(ProjectIconData.fromPng(png512()))
        ).successValue()
        val removed = repository.updateProfile(
            created.id,
            "After",
            "Updated",
            ProjectIconDataChange.Remove
        ).successValue()

        assertEquals(created.id, replaced.id)
        assertEquals("After", replaced.name)
        assertTrue(replaced.hasCustomIcon)
        assertFalse(removed.hasCustomIcon)
        assertEquals(null, repository.loadIcon(created.id).successValue())
        assertEquals(2, fileSystem.bytes.size)
    }

    @Test
    fun `duplicate names are case insensitive`() = runBlocking {
        val repository = repository(FakeFileSystem())
        repository.createDraft("Demo", "", null)

        val result = repository.createDraft("demo", "", null)

        assertEquals(ProjectDataError.NameAlreadyExists, (result as OperationResult.Failure).error)
    }

    @Test
    fun `delete is idempotent`() = runBlocking {
        val repository = repository(FakeFileSystem())
        val created = repository.createDraft("Demo", "", null).successValue()

        assertTrue(repository.delete(created.id) is OperationResult.Success)
        assertTrue(repository.delete(ProjectId("already-gone")) is OperationResult.Success)
        assertFalse(repository.observeProjects().first().successValue().any { it.id == created.id })
    }

    private fun repository(fileSystem: FakeFileSystem) = DefaultProjectRepository(
        fileSystem = fileSystem,
        git = NoOpGit,
        repositoryLocator = ProjectGitRepositoryLocator {
            GitRepositoryLocation("/projects/${it.value}/workspace", "/projects/${it.value}/git")
        },
        initializationJournals = InitializationJournalStore(fileSystem),
        mutationLease = ProjectMutationLease()
    )

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

private object NoOpGit : ControlledGit {
    override suspend fun initialize(repository: GitRepositoryLocation): GitResult<Unit> =
        GitResult.Success(Unit)
    override suspend fun commitAll(
        repository: GitRepositoryLocation,
        request: GitCommitRequest
    ): GitResult<GitRevision> = error("Not used")
    override suspend fun status(repository: GitRepositoryLocation): GitResult<GitStatus> =
        error("Not used")
    override suspend fun readHistory(
        repository: GitRepositoryLocation
    ): GitResult<List<GitCommit>> = error("Not used")
    override suspend fun restoreWorkTree(
        repository: GitRepositoryLocation,
        revision: GitRevision
    ): GitResult<Unit> = error("Not used")
}

private fun <T> OperationResult<T>.successValue(): T = (this as OperationResult.Success).value

private fun png512(): ByteArray = byteArrayOf(
    137.toByte(), 80, 78, 71, 13, 10, 26, 10,
    0, 0, 0, 13, 'I'.code.toByte(), 'H'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte(),
    0, 0, 2, 0, 0, 0, 2, 0
)
