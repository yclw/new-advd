package com.aeibi.avd.core.git

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Libgit2ControlledGitTest {
    private val git = Libgit2ControlledGit()
    private lateinit var root: File
    private lateinit var workTree: File
    private lateinit var repository: GitRepositoryLocation

    @Before
    fun setUp() {
        val cacheDir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        root = File(cacheDir, "git-test-${UUID.randomUUID()}").apply { mkdirs() }
        workTree = File(root, "workspace").apply { mkdirs() }
        repository = GitRepositoryLocation(workTree.absolutePath, File(root, "git").absolutePath)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun ignoredFilesAreSnapshottedAndRestored() = runBlocking {
        git.initialize(repository).value()
        File(workTree, ".gitignore").writeText("ignored/\n")
        val ignored = File(workTree, "ignored").apply { mkdirs() }
        val existing = File(ignored, "existing.txt").apply { writeText("first") }
        val first = git.commitAll(repository, commitRequest()).value()

        val added = File(ignored, "added.txt").apply { writeText("new") }
        assertTrue(git.status(repository).value().hasChanges)
        existing.writeText("second")
        git.commitAll(repository, commitRequest()).value()

        git.restoreWorkTree(repository, first).value()
        assertEquals("first", existing.readText())
        assertFalse(added.exists())
        git.commitAll(repository, commitRequest()).value()
        assertEquals(3, git.readHistory(repository).value().size)
    }

    @Test
    fun deletingTrackedFileIsRecordedInSnapshot() = runBlocking {
        git.initialize(repository).value()
        File(workTree, ".gitignore").writeText("ignored/\n")
        val ignored = File(workTree, "ignored").apply { mkdirs() }
        val file = File(ignored, "deleted.txt").apply { writeText("saved") }
        val withFile = git.commitAll(repository, commitRequest()).value()

        assertTrue(file.delete())
        assertTrue(git.status(repository).value().hasChanges)
        val withoutFile = git.commitAll(repository, commitRequest()).value()
        git.restoreWorkTree(repository, withFile).value()
        assertEquals("saved", file.readText())
        git.restoreWorkTree(repository, withoutFile).value()
        assertFalse(file.exists())
    }

    private fun commitRequest() = GitCommitRequest(
        subject = "snapshot",
        trailers = emptyList(),
        signature = GitSignature("AVD", "versions@local", System.currentTimeMillis() / 1_000)
    )

    private fun <T> GitResult<T>.value(): T = when (this) {
        is GitResult.Success -> value
        is GitResult.Failure -> error("Git failed: $error")
    }
}
