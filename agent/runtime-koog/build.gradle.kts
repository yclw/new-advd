plugins {
    id("avd.android.library")
    id("avd.hilt")
    id("avd.testing")
}

android { namespace = "com.aeibi.avd.agent.runtimekoog" }

dependencies {
    implementation(project(":core:common"))
    implementation(project(":contract:agent"))
}
