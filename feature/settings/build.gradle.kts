plugins {
    id("avd.android.compose.feature")
    id("avd.testing")
}

android { namespace = "com.aeibi.avd.feature.settings" }

dependencies {
    implementation(project(":core:navigation"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":domain:settings"))
}
