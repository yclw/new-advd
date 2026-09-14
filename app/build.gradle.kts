plugins {
    id("avd.android.compose.application")
    id("avd.hilt")
    id("avd.testing")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.aeibi.avd.app"

    androidResources {
        generateLocaleConfig = true
    }

    defaultConfig {
        applicationId = "com.aeibi.avd"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:navigation"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":feature:projects"))
    implementation(project(":feature:workbench"))
    implementation(project(":feature:templates"))
    implementation(project(":feature:versions"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:build"))
    implementation(project(":agent:runtime-koog"))
    implementation(project(":contract:project-runtime"))
    implementation(project(":domain:agent"))
    implementation(project(":domain:preview"))
    implementation(project(":domain:settings"))
    implementation(project(":domain:project"))
    implementation(project(":data:settings"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.coroutines.core)
    implementation("androidx.compose.runtime:runtime-saveable")
}
