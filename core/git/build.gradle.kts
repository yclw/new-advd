plugins {
    id("avd.android.library")
    id("avd.hilt")
    id("avd.testing")
}

android {
    namespace = "com.aeibi.avd.core.git"

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}

dependencies {
    implementation(project(":core:filesystem"))
}
