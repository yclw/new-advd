package com.aeibi.avd.core.git

/** Git operations for a repository. */
interface ControlledGit {
    suspend fun initialize(repository: GitRepositoryLocation): GitResult<Unit>

    suspend fun commitAll(
        repository: GitRepositoryLocation,
        request: GitCommitRequest
    ): GitResult<GitRevision>

    suspend fun status(repository: GitRepositoryLocation): GitResult<GitStatus>

    suspend fun readHistory(repository: GitRepositoryLocation): GitResult<List<GitCommit>>

    /** Replaces worktree content with [revision] without moving HEAD. */
    suspend fun restoreWorkTree(
        repository: GitRepositoryLocation,
        revision: GitRevision
    ): GitResult<Unit>
}

/** Locations of the worktree and Git metadata directory. */
data class GitRepositoryLocation(val workTreePath: String, val gitDirectoryPath: String) {
    init {
        require(workTreePath.isAbsolutePath()) { "workTreePath must be absolute." }
        require(gitDirectoryPath.isAbsolutePath()) { "gitDirectoryPath must be absolute." }
    }
}

data class GitCommitRequest(
    val subject: String,
    val trailers: List<GitTrailer>,
    val signature: GitSignature
) {
    init {
        require(subject.none { it == '\n' || it == '\r' || it == '\u0000' }) {
            "Git commit subject must be one line."
        }
    }
}

data class GitSignature(val name: String, val email: String, val timestampEpochSeconds: Long) {
    init {
        require(name.isSingleLineValue()) { "Git signature name must be a non-empty line." }
        require(email.isSingleLineValue()) { "Git signature email must be a non-empty line." }
    }
}

@JvmInline
value class GitRevision private constructor(val value: String) {
    companion object {
        fun of(value: String): GitRevision? = value.takeIf(String::isNotBlank)?.let(::GitRevision)
    }
}

data class GitStatus(val headRevision: GitRevision?, val hasChanges: Boolean)

data class GitCommit(
    val revision: GitRevision,
    val parentRevisions: List<GitRevision>,
    val committedAtEpochMillis: Long,
    val trailers: List<GitTrailer>
)

data class GitTrailer(val token: String, val value: String) {
    init {
        require(token.matches(TRAILER_TOKEN)) { "Git trailer token is invalid." }
        require(value.isSingleLineValue()) { "Git trailer value must be a non-empty line." }
    }

    private companion object {
        val TRAILER_TOKEN = Regex("[A-Za-z0-9-]+")
    }
}

sealed interface GitResult<out T> {
    data class Success<T>(val value: T) : GitResult<T>
    data class Failure(val error: GitError) : GitResult<Nothing>
}

enum class GitError {
    NATIVE_BACKEND_UNAVAILABLE,
    REPOSITORY_NOT_FOUND,
    REPOSITORY_ALREADY_EXISTS,
    INVALID_REVISION,
    WORK_TREE_DIRTY,
    NO_CHANGES,
    INVALID_INPUT,
    OPERATION_FAILED
}

private fun String.isAbsolutePath(): Boolean = startsWith('/') && none { it == '\u0000' }

private fun String.isSingleLineValue(): Boolean =
    isNotBlank() && none { it == '\n' || it == '\r' || it == '\u0000' }
