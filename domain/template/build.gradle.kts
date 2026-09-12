plugins {
    id("avd.kotlin.domain")
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":data:template"))
    implementation(libs.javax.inject)
    implementation(libs.kotlinx.coroutines.core)
}
