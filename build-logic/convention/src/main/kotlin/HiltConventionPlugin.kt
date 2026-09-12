package com.aeibi.avd.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.dagger.hilt.android")
            pluginManager.apply("com.google.devtools.ksp")
            dependencies.add("implementation", "com.google.dagger:hilt-android:2.60.1")
            dependencies.add("ksp", "com.google.dagger:hilt-compiler:2.60.1")
        }
    }
}
