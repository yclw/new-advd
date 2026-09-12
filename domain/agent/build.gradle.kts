plugins {
    id("avd.android.library")
    id("avd.testing")
}

android { namespace = "com.aeibi.avd.domain.agent" }
dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:logging"))
    implementation(project(":contract:agent"))
    implementation(project(":contract:project-runtime"))
    implementation(project(":data:project"))
    implementation(project(":data:session"))
    implementation(project(":data:ai-config"))
    implementation(libs.javax.inject)
    implementation(libs.kotlinx.coroutines.core)
}
