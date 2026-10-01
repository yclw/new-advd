package com.aeibi.avd.core.model

import com.aeibi.avd.core.common.ProjectId

data class Project(
    val id: ProjectId,
    val name: String,
    val description: String,
    val icon: ProjectIcon?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

class ProjectIcon private constructor(private val pngBytes: ByteArray) {
    fun copyPngBytes(): ByteArray = pngBytes.copyOf()

    override fun equals(other: Any?): Boolean =
        other is ProjectIcon && pngBytes.contentEquals(other.pngBytes)

    override fun hashCode(): Int = pngBytes.contentHashCode()

    companion object {
        fun fromPng(bytes: ByteArray): ProjectIcon = ProjectIcon(bytes.copyOf())
    }
}
