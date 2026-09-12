plugins {
    id("avd.android.library")
    id("avd.hilt")
    id("avd.testing")
}

android { namespace = "com.aeibi.avd.core.filesystem" }

dependencies {
    implementation(libs.kotlinx.coroutines.core)
}
