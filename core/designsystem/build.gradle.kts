plugins {
    id("avd.android.compose.feature")
    id("avd.testing")
}

android {
    namespace = "com.aeibi.avd.core.designsystem"
}

dependencies {
    implementation(project(":core:model"))
}
