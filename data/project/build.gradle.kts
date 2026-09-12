plugins {
    id("avd.android.library")
    id("avd.hilt")
    id("avd.testing")
    alias(libs.plugins.kotlin.serialization)
}

android { namespace = "com.aeibi.avd.data.project" }

dependencies {
    implementation(libs.androidx.annotation.experimental)
    implementation(project(":core:common"))
    implementation(project(":core:filesystem"))
    implementation(project(":core:git"))
    implementation(project(":core:model"))
    implementation(project(":core:logging"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}
