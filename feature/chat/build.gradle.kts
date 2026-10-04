plugins {
    id("avd.android.compose.feature")
    id("avd.testing")
}

android {
    namespace = "com.aeibi.avd.feature.chat"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.jetbrains.markdown)
    implementation("androidx.compose.runtime:runtime-saveable")
}
