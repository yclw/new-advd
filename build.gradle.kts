plugins {
    base
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kotlin.serialization) apply false
    id("avd.module-graph")
    id("avd.architecture-sources")
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
}
