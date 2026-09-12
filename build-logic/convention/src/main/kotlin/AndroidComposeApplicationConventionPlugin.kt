package com.aeibi.avd.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

class AndroidComposeApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("avd.android.application")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        extensions.getByType<ApplicationExtension>().buildFeatures.compose = true
    }
}
