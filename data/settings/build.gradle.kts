plugins {
    id("avd.android.library")
    id("avd.hilt")
    id("avd.testing")
}

android {
    namespace = "com.aeibi.avd.data.settings"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
}
