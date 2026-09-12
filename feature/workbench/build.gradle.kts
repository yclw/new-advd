plugins {
    id("avd.android.compose.feature")
    id("avd.testing")
}

android { namespace = "com.aeibi.avd.feature.workbench" }

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:navigation"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":domain:project"))
    implementation(project(":domain:agent"))
    implementation(project(":domain:preview"))
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
