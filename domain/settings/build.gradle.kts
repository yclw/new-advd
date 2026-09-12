plugins {
    id("avd.android.library")
    id("avd.testing")
}

android {
    namespace = "com.aeibi.avd.domain.settings"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":data:ai-config"))
    implementation(project(":data:settings"))
    implementation(libs.javax.inject)
    implementation(libs.kotlinx.coroutines.core)
}
