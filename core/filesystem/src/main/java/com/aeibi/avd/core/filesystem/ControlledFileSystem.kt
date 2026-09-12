package com.aeibi.avd.core.filesystem

/** Filesystem operations constrained to one application-owned root directory. */
interface ControlledFileSystem {
    suspend fun createDirectories(path: RelativePath): FileSystemResult<Unit>
    suspend fun listDirectories(path: RelativePath): FileSystemResult<List<String>>
    suspend fun readText(path: RelativePath): FileSystemResult<String?>
    suspend fun writeTextAtomically(path: RelativePath, content: String): FileSystemResult<Unit>
    suspend fun readBytes(path: RelativePath): FileSystemResult<ByteArray?>
    suspend fun writeBytesAtomically(path: RelativePath, bytes: ByteArray): FileSystemResult<Unit>
    suspend fun moveDirectoryAtomically(
        source: RelativePath,
        destination: RelativePath
    ): FileSystemResult<Unit>
    suspend fun deleteFile(path: RelativePath): FileSystemResult<Unit>
    suspend fun deleteDirectory(path: RelativePath): FileSystemResult<Unit>
    suspend fun exists(path: RelativePath): FileSystemResult<Boolean>
}

@JvmInline
value class RelativePath private constructor(val value: String) {
    companion object {
        fun of(value: String): RelativePath? = value
            .replace('\\', '/')
            .takeIf { it.isNotEmpty() && !it.startsWith('/') }
            ?.split('/')
            ?.takeIf { segments -> segments.all { it.isNotEmpty() && it != "." && it != ".." } }
            ?.joinToString("/")
            ?.let(::RelativePath)
    }
}

sealed interface FileSystemResult<out T> {
    data class Success<T>(val value: T) : FileSystemResult<T>
    data class Failure(val error: FileSystemError) : FileSystemResult<Nothing>
}

enum class FileSystemError {
    STORAGE_UNAVAILABLE,
    INVALID_PATH
}
