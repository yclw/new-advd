package com.aeibi.avd.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

class KotlinDomainConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply("avd.kotlin.core")
        target.pluginManager.apply("avd.testing")
    }
}
