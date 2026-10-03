plugins {
    id("avd.android.compose.feature")
    id("avd.testing")
}

android { namespace = "com.aeibi.avd.feature.build" }

dependencies {
    implementation(project(":core:common"))
    implementation(libs.androidx.lifecycle.runtime.compose)
}
