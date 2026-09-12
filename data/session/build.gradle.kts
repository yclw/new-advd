plugins {
    id("avd.kotlin.core")
    id("avd.testing")
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:logging"))
    implementation(libs.kotlinx.coroutines.core)
}
