package com.aeibi.avd.core.filesystem

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
internal class AndroidControlledFileSystem @Inject constructor(
    @ApplicationContext context: Context
) : ControlledFileSystem {
    private val root = context.filesDir.toPath().resolve("avd")

    override suspend fun createDirectories(path: RelativePath): FileSystemResult<Unit> = io {
        Files.createDirectories(resolve(path))
    }

    override suspend fun listDirectories(path: RelativePath): FileSystemResult<List<String>> = io {
        val directory = resolve(path)
        if (!Files.isDirectory(directory)) return@io emptyList()
        Files.list(directory).use { entries ->
            buildList {
                val iterator = entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (Files.isDirectory(entry)) add(entry.fileName.toString())
                }
            }
        }
    }

    override suspend fun readText(path: RelativePath): FileSystemResult<String?> = io {
        val file = resolve(path)
        if (Files.notExists(
                file
            )
        ) {
            null
        } else {
            String(Files.readAllBytes(file), StandardCharsets.UTF_8)
        }
    }

    override suspend fun writeTextAtomically(
        path: RelativePath,
        content: String
    ): FileSystemResult<Unit> = writeAtomically(path, content.toByteArray(StandardCharsets.UTF_8))

    override suspend fun readBytes(path: RelativePath): FileSystemResult<ByteArray?> = io {
        val file = resolve(path)
        if (Files.notExists(file)) null else Files.readAllBytes(file)
    }

    override suspend fun writeBytesAtomically(
        path: RelativePath,
        bytes: ByteArray
    ): FileSystemResult<Unit> = writeAtomically(path, bytes.copyOf())

    override suspend fun moveDirectoryAtomically(
        source: RelativePath,
        destination: RelativePath
    ): FileSystemResult<Unit> = io {
        Files.move(resolve(source), resolve(destination), StandardCopyOption.ATOMIC_MOVE)
    }

    override suspend fun deleteFile(path: RelativePath): FileSystemResult<Unit> = io {
        Files.deleteIfExists(resolve(path))
    }

    private suspend fun writeAtomically(
        path: RelativePath,
        bytes: ByteArray
    ): FileSystemResult<Unit> = io {
        val destination = resolve(path)
        Files.createDirectories(destination.parent)
        val temporary = destination.resolveSibling(
            ".${destination.fileName}.${UUID.randomUUID()}.tmp"
        )
        try {
            Files.write(
                temporary,
                bytes,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
            )
            Files.move(
                temporary,
                destination,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    override suspend fun deleteDirectory(path: RelativePath): FileSystemResult<Unit> = io {
        val directory = resolve(path)
        if (Files.notExists(directory)) return@io Unit
        Files.walk(directory).use { entries ->
            entries.sorted(Comparator.reverseOrder()).forEach(Files::delete)
        }
    }

    override suspend fun exists(path: RelativePath): FileSystemResult<Boolean> = io {
        Files.exists(resolve(path))
    }

    private fun resolve(path: RelativePath) = root.resolve(path.value).normalize().also {
        check(it.startsWith(root)) { "Relative path escaped the controlled root." }
    }

    private suspend fun <T> io(block: () -> T): FileSystemResult<T> = withContext(Dispatchers.IO) {
        try {
            FileSystemResult.Success(block())
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            FileSystemResult.Failure(FileSystemError.STORAGE_UNAVAILABLE)
        } catch (_: SecurityException) {
            FileSystemResult.Failure(FileSystemError.STORAGE_UNAVAILABLE)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class FileSystemModule {
    @Binds
    abstract fun bindControlledFileSystem(
        implementation: AndroidControlledFileSystem
    ): ControlledFileSystem
}
