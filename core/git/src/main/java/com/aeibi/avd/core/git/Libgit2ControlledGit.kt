package com.aeibi.avd.core.git

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
internal class Libgit2ControlledGit @Inject constructor() : ControlledGit {
    override suspend fun initialize(repository: GitRepositoryLocation): GitResult<Unit> = native {
        GitNative.initialize(repository.workTreePath, repository.gitDirectoryPath)
    }

    override suspend fun commitAll(
        repository: GitRepositoryLocation,
        request: GitCommitRequest
    ): GitResult<GitRevision> = native {
        GitNative.commitAll(
            gitDirectoryPath = repository.gitDirectoryPath,
            message = request.toMessage(),
            authorName = request.signature.name,
            authorEmail = request.signature.email,
            timestampEpochSeconds = request.signature.timestampEpochSeconds
        ).let { revision ->
            checkNotNull(GitRevision.of(revision)) { "Native Git returned a blank revision." }
        }
    }

    override suspend fun status(repository: GitRepositoryLocation): GitResult<GitStatus> = native {
        GitNative.status(repository.gitDirectoryPath).let { status ->
            GitStatus(
                headRevision = status.headRevision?.let { revision ->
                    checkNotNull(GitRevision.of(revision)) {
                        "Native Git returned a blank revision."
                    }
                },
                hasChanges = status.hasChanges
            )
        }
    }

    override suspend fun readHistory(
        repository: GitRepositoryLocation
    ): GitResult<List<GitCommit>> = native {
        GitNative.readHistory(repository.gitDirectoryPath).map { commit ->
            GitCommit(
                revision = checkNotNull(GitRevision.of(commit.revision)),
                parentRevisions = commit.parentRevisions.map { parent ->
                    checkNotNull(GitRevision.of(parent))
                },
                committedAtEpochMillis = commit.committedAtEpochSeconds * MILLIS_PER_SECOND,
                trailers = commit.trailers.map { trailer ->
                    GitTrailer(trailer.token, trailer.value)
                }
            )
        }.toList()
    }

    override suspend fun restoreWorkTree(
        repository: GitRepositoryLocation,
        revision: GitRevision
    ): GitResult<Unit> = native {
        GitNative.restoreWorkTree(repository.gitDirectoryPath, revision.value)
    }

    private suspend fun <T> native(block: () -> T): GitResult<T> = withContext(Dispatchers.IO) {
        try {
            GitResult.Success(block())
        } catch (error: CancellationException) {
            throw error
        } catch (_: UnsatisfiedLinkError) {
            GitResult.Failure(GitError.NATIVE_BACKEND_UNAVAILABLE)
        } catch (error: GitNativeException) {
            GitResult.Failure(error.kind.toGitError())
        } catch (_: IllegalArgumentException) {
            GitResult.Failure(GitError.INVALID_INPUT)
        } catch (_: IllegalStateException) {
            GitResult.Failure(GitError.OPERATION_FAILED)
        }
    }
}

private fun GitCommitRequest.toMessage(): String = buildString {
    append(subject)
    append("\n\n")
    trailers.forEach { trailer ->
        append(trailer.token)
        append(": ")
        append(trailer.value)
        append('\n')
    }
}

private fun String.toGitError(): GitError = when (this) {
    "REPOSITORY_NOT_FOUND" -> GitError.REPOSITORY_NOT_FOUND
    "REPOSITORY_ALREADY_EXISTS" -> GitError.REPOSITORY_ALREADY_EXISTS
    "INVALID_REVISION" -> GitError.INVALID_REVISION
    "WORK_TREE_DIRTY" -> GitError.WORK_TREE_DIRTY
    "NO_CHANGES" -> GitError.NO_CHANGES
    "INVALID_INPUT" -> GitError.INVALID_INPUT
    else -> GitError.OPERATION_FAILED
}

private const val MILLIS_PER_SECOND = 1_000L

@Module
@InstallIn(SingletonComponent::class)
internal abstract class GitModule {
    @Binds
    abstract fun bindControlledGit(implementation: Libgit2ControlledGit): ControlledGit
}
