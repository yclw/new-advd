package com.aeibi.avd.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

class TestingConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.dependencies.add("testImplementation", "junit:junit:4.13.2")
    }
}
