package com.aeibi.avd.buildlogic

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register

abstract class VerifyArchitectureSourcesTask : DefaultTask() {
    @get:Input
    abstract val violations: ListProperty<String>

    @TaskAction
    fun verify() {
        if (violations.get().isNotEmpty()) {
            throw GradleException(
                "Architecture source violations:\n${violations.get().joinToString("\n")}"
            )
        }
    }
}

class ArchitectureSourcesConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            check(path == ":") { "avd.architecture-sources must be applied to the root project." }

            val verifyArchitectureSources =
                tasks.register<VerifyArchitectureSourcesTask>("verifyArchitectureSources") {
                    group = "verification"
                    description = "Verifies domain and ViewModel source boundaries."
                }

            gradle.projectsEvaluated {
                verifyArchitectureSources.configure {
                    violations.set(allprojects.flatMap(::findViolations))
                }
            }

            tasks.named("check") {
                dependsOn(verifyArchitectureSources)
            }
        }
    }

    private fun findViolations(project: Project): List<String> {
        val sourceFiles = project.fileTree("src/main") {
            include("**/*.kt")
        }.files
        return sourceFiles.flatMap { file ->
            val imports = file.readLines().filter { it.startsWith("import ") }
            buildList {
                if (project.path.startsWith(":domain:") || project.path.startsWith(":contract:")) {
                    imports.filter(::isForbiddenInPureKotlinModule).forEach { imported ->
                        add(
                            "${project.path}: ${file.relativeTo(
                                project.projectDir
                            ).path} must not import $imported"
                        )
                    }
                }
                if (isViewModel(file)) {
                    imports.filter(::isForbiddenInViewModel).forEach { imported ->
                        add(
                            "${project.path}: ${file.relativeTo(
                                project.projectDir
                            ).path} ViewModel must not import $imported"
                        )
                    }
                }
            }
        }
    }

    private fun isViewModel(file: File): Boolean =
        file.name.endsWith("ViewModel.kt") || file.readText().contains(": ViewModel")

    private fun isForbiddenInPureKotlinModule(imported: String): Boolean =
        pureKotlinForbiddenPrefixes.any(imported::startsWith)

    private fun isForbiddenInViewModel(imported: String): Boolean =
        viewModelForbiddenPrefixes.any(imported::startsWith)

    private companion object {
        val pureKotlinForbiddenPrefixes = listOf(
            "import android.",
            "import androidx.activity.",
            "import androidx.compose.",
            "import androidx.lifecycle.",
            "import androidx.room.",
            "import androidx.work.",
            "import androidx.webkit.",
            "import dagger.hilt.android."
        )
        val viewModelForbiddenPrefixes = listOf(
            "import android.content.Context",
            "import android.webkit.WebView",
            "import androidx.room.",
            "import java.io.File",
            "import com.aeibi.avd.data.",
            "import ai.koog."
        )
    }
}
