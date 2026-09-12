plugins {
    id("avd.android.library")
    id("avd.testing")
}

android { namespace = "com.aeibi.avd.domain.version" }

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":data:project"))
    implementation(libs.javax.inject)
    implementation(libs.kotlinx.coroutines.core)
}
