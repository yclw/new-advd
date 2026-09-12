plugins {
    id("avd.kotlin.domain")
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":contract:project-runtime"))
    implementation(libs.javax.inject)
    implementation(libs.kotlinx.coroutines.core)
}
