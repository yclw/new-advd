pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        kotlin("jvm") version "2.3.21"
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "AndroidVibeDesign"

include(":app")
include(":shell")
include(":core:common")
include(":core:model")
include(":core:database")
include(":core:datastore")
include(":core:filesystem")
include(":core:git")
include(":core:secure-storage")
include(":core:network")
include(":core:logging")
include(":core:navigation")
include(":core:designsystem")
include(":core:ui")
include(":core:testing")
include(":data:project")
include(":data:template")
include(":data:session")
include(":data:ai-config")
include(":data:settings")
include(":contract:agent")
include(":contract:project-runtime")
include(":agent:runtime-koog")
include(":domain:project")
include(":domain:template")
include(":domain:version")
include(":domain:agent")
include(":domain:preview")
include(":domain:settings")
include(":feature:projects")
include(":feature:workbench")
include(":feature:templates")
include(":feature:versions")
include(":feature:settings")
include(":feature:build")
