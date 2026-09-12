package com.aeibi.avd.core.git

internal object GitNative {
    init {
        System.loadLibrary("vibe_git")
    }

    external fun initialize(workTreePath: String, gitDirectoryPath: String)

    external fun commitAll(
        gitDirectoryPath: String,
        message: String,
        authorName: String,
        authorEmail: String,
        timestampEpochSeconds: Long
    ): String

    external fun status(gitDirectoryPath: String): NativeGitStatus

    external fun readHistory(gitDirectoryPath: String): Array<NativeGitCommit>

    external fun restoreWorkTree(gitDirectoryPath: String, revision: String)
}

internal class GitNativeException(val kind: String, message: String) : RuntimeException(message)

internal data class NativeGitStatus(val headRevision: String?, val hasChanges: Boolean)

internal data class NativeGitCommit(
    val revision: String,
    val parentRevisions: Array<String>,
    val committedAtEpochSeconds: Long,
    val trailers: Array<NativeGitTrailer>
)

internal data class NativeGitTrailer(val token: String, val value: String)
