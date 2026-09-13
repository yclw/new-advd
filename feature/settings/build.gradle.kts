plugins {
    id("avd.android.compose.feature")
    id("avd.hilt")
    id("avd.testing")
}

android { namespace = "com.aeibi.avd.feature.settings" }

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:navigation"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":domain:settings"))
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation("androidx.compose.runtime:runtime-saveable")
}
