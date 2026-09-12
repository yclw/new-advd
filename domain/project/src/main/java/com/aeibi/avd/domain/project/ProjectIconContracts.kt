package com.aeibi.avd.domain.project

/** Feature-facing project icon input. The data module receives a separately mapped resource input. */
class ProjectIconUpload private constructor(private val bytes: ByteArray) {
    fun copyPngBytes(): ByteArray = bytes.copyOf()

    companion object {
        fun fromPng(bytes: ByteArray): ProjectIconUpload = ProjectIconUpload(bytes.copyOf())
    }
}

class ProjectIconContent private constructor(private val bytes: ByteArray) {
    fun copyPngBytes(): ByteArray = bytes.copyOf()

    companion object {
        fun fromPng(bytes: ByteArray): ProjectIconContent = ProjectIconContent(bytes.copyOf())
    }
}

sealed interface ProjectIconChange {
    data object Keep : ProjectIconChange
    data object Remove : ProjectIconChange
    data class Replace(val icon: ProjectIconUpload) : ProjectIconChange
}
