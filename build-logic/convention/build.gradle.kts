plugins {
    `kotlin-dsl`
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

kotlin {
    jvmToolchain(17)
}

group = "com.aeibi.avd.buildlogic"

dependencies {
    implementation("com.android.tools.build:gradle:9.2.1")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    implementation("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.3.21")
    implementation("com.google.dagger:hilt-android-gradle-plugin:2.60.1")
    implementation("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.9")

    testImplementation(gradleTestKit())
    testImplementation("junit:junit:4.13.2")
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "avd.android.application"
            implementationClass = "com.aeibi.avd.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidComposeApplication") {
            id = "avd.android.compose.application"
            implementationClass =
                "com.aeibi.avd.buildlogic.AndroidComposeApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "avd.android.library"
            implementationClass = "com.aeibi.avd.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidComposeFeature") {
            id = "avd.android.compose.feature"
            implementationClass = "com.aeibi.avd.buildlogic.AndroidComposeFeatureConventionPlugin"
        }
        register("androidDomain") {
            id = "avd.kotlin.domain"
            implementationClass = "com.aeibi.avd.buildlogic.KotlinDomainConventionPlugin"
        }
        register("kotlinCore") {
            id = "avd.kotlin.core"
            implementationClass = "com.aeibi.avd.buildlogic.KotlinCoreConventionPlugin"
        }
        register("hilt") {
            id = "avd.hilt"
            implementationClass = "com.aeibi.avd.buildlogic.HiltConventionPlugin"
        }
        register("testing") {
            id = "avd.testing"
            implementationClass = "com.aeibi.avd.buildlogic.TestingConventionPlugin"
        }
        register("moduleGraph") {
            id = "avd.module-graph"
            implementationClass = "com.aeibi.avd.buildlogic.ModuleGraphConventionPlugin"
        }
        register("architectureSources") {
            id = "avd.architecture-sources"
            implementationClass = "com.aeibi.avd.buildlogic.ArchitectureSourcesConventionPlugin"
        }
    }
}
