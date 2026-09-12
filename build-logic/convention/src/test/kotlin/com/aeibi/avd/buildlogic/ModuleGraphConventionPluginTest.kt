package com.aeibi.avd.buildlogic

import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModuleGraphConventionPluginTest {
    @get:Rule
    val testProject = TemporaryFolder()

    @Test
    fun `feature to data dependency fails verification`() {
        val root = testProject.root
        root.file("settings.gradle.kts").writeText(
            """
            rootProject.name = "module-graph-test"
            include(":core:common", ":data:project", ":feature:projects")
            """.trimIndent()
        )
        root.file("build.gradle.kts").writeText(
            """
            plugins {
                base
                id("avd.module-graph")
            }
            """.trimIndent()
        )
        root.file("core/common/build.gradle.kts").writeText("plugins { `java-library` }")
        root.file("data/project/build.gradle.kts").writeText("plugins { `java-library` }")
        root.file("feature/projects/build.gradle.kts").writeText(
            """
            plugins { `java-library` }

            dependencies {
                implementation(project(":data:project"))
            }
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(root)
            .withArguments("verifyModuleGraph")
            .withPluginClasspath(listOf(pluginJar()))
            .buildAndFail()

        assertTrue(result.output, result.output.contains(":feature:projects must not depend on :data:project"))
    }

    @Test
    fun `agent runtime to data dependency fails verification`() {
        val root = testProject.root
        root.file("settings.gradle.kts").writeText(
            """
            rootProject.name = "module-graph-test"
            include(":core:common", ":data:project", ":agent:runtime-koog")
            """.trimIndent()
        )
        root.file("build.gradle.kts").writeText(
            """
            plugins {
                base
                id("avd.module-graph")
            }
            """.trimIndent()
        )
        root.file("core/common/build.gradle.kts").writeText("plugins { `java-library` }")
        root.file("data/project/build.gradle.kts").writeText("plugins { `java-library` }")
        root.file("agent/runtime-koog/build.gradle.kts").writeText(
            """
            plugins { `java-library` }

            dependencies {
                implementation(project(":data:project"))
            }
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(root)
            .withArguments("verifyModuleGraph")
            .withPluginClasspath(listOf(pluginJar()))
            .buildAndFail()

        assertTrue(
            result.output,
            result.output.contains(":agent:runtime-koog must not depend on :data:project")
        )
    }

    @Test
    fun `data module to data module dependency fails verification`() {
        val root = testProject.root
        root.file("settings.gradle.kts").writeText(
            """
            rootProject.name = "module-graph-test"
            include(":core:common", ":data:settings", ":data:project")
            """.trimIndent()
        )
        root.file("build.gradle.kts").writeText(
            """
            plugins {
                base
                id("avd.module-graph")
            }
            """.trimIndent()
        )
        root.file("core/common/build.gradle.kts").writeText("plugins { `java-library` }")
        root.file("data/settings/build.gradle.kts").writeText("plugins { `java-library` }")
        root.file("data/project/build.gradle.kts").writeText("plugins { `java-library` }")
        root.file("data/settings/build.gradle.kts").writeText(
            """
            plugins { `java-library` }

            dependencies {
                implementation(project(":data:project"))
            }
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(root)
            .withArguments("verifyModuleGraph")
            .withPluginClasspath(listOf(pluginJar()))
            .buildAndFail()

        assertTrue(
            result.output,
            result.output.contains(":data:settings must not depend on :data:project")
        )
    }

    private fun pluginJar(): File {
        val testClasses =
            File(ModuleGraphConventionPluginTest::class.java.protectionDomain.codeSource.location.toURI())
        return File(testClasses.parentFile.parentFile.parentFile, "libs/convention.jar").also {
            check(it.isFile) { "Plugin jar is missing: $it" }
        }
    }

    private fun File.file(path: String): File = File(this, path).also { file ->
        file.parentFile.mkdirs()
    }
}
