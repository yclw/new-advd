plugins {
    id("avd.android.compose.feature")
    id("avd.testing")
}

android {
    namespace = "com.aeibi.avd.feature.preview"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation("androidx.compose.runtime:runtime-saveable")
}
