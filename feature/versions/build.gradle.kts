plugins {
    id("avd.android.compose.feature")
    id("avd.testing")
}

android { namespace = "com.aeibi.avd.feature.versions" }

dependencies {
    implementation(project(":core:common"))
    implementation(libs.androidx.lifecycle.runtime.compose)
}
