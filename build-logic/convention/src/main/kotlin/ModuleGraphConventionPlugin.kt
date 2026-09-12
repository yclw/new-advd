package com.aeibi.avd.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register

abstract class VerifyModuleGraphTask : DefaultTask() {
    @get:Input
    abstract val violations: ListProperty<String>

    @TaskAction
    fun verify() {
        if (violations.get().isNotEmpty()) {
            throw GradleException(
                "Module graph violations:\n${violations.get().joinToString("\n")}"
            )
        }
    }
}

class ModuleGraphConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            check(path == ":") { "avd.module-graph must be applied to the root project." }

            val verifyModuleGraph = tasks.register<VerifyModuleGraphTask>("verifyModuleGraph") {
                group = "verification"
                description = "Verifies the architectural module dependency rules."
            }

            gradle.projectsEvaluated {
                verifyModuleGraph.configure {
                    violations.set(
                        allprojects.flatMap { project ->
                            project.configurations
                                .filter { !it.isCanBeResolved }
                                .flatMap {
                                    it.dependencies.withType(ProjectDependency::class.java)
                                }
                                .mapNotNull { dependency ->
                                    dependency.path.takeIf {
                                        isForbiddenDependency(project.path, it)
                                    }
                                        ?.let { target ->
                                            "${project.path} must not depend on $target"
                                        }
                                }
                        }
                    )
                }
            }

            tasks.named("check") {
                dependsOn(verifyModuleGraph)
            }
        }
    }

    private fun isForbiddenDependency(source: String, target: String): Boolean = when {
        source.startsWith(":core:") -> !target.startsWith(":core:")
        source.startsWith(":contract:") ->
            target != ":core:common" && target != ":core:model"
        source.startsWith(":data:") ->
            !target.startsWith(":core:") &&
                !target.startsWith(":contract:")
        source.startsWith(":agent:") ->
            !target.startsWith(":core:") && !target.startsWith(":contract:")
        source.startsWith(":domain:") ->
            !target.startsWith(":core:") &&
                !target.startsWith(":contract:") &&
                !target.startsWith(":data:")
        source.startsWith(":feature:") ->
            !target.startsWith(":core:") &&
                !target.startsWith(":domain:") &&
                !(target.startsWith(":feature:") && target.endsWith(":api"))
        source == ":app" ->
            !target.startsWith(":core:") &&
                !target.startsWith(":feature:") &&
                target !in appBindingModules
        source == ":shell" -> true
        else -> false
    }

    private companion object {
        val appBindingModules = setOf(
            ":agent:runtime-koog",
            ":contract:project-runtime",
            ":domain:agent",
            ":domain:preview",
            ":domain:settings",
            ":domain:project",
            ":data:settings"
        )
    }
}
