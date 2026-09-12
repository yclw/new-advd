package com.aeibi.avd.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

class AndroidComposeFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("avd.android.library")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.getByType<LibraryExtension>().buildFeatures.compose = true
            val libraries = extensions.getByType<VersionCatalogsExtension>().named("libs")
            dependencies.apply {
                add("implementation", platform(libraries.findLibrary("androidx-compose-bom").get()))
                add("implementation", libraries.findLibrary("androidx-compose-ui").get())
                add("implementation", libraries.findLibrary("androidx-compose-material3").get())
            }
        }
    }
}
