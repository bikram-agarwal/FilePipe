buildscript {
    configurations.classpath {
        resolutionStrategy {
            force(
                "io.github.detekt.sarif4k:sarif4k:${libs.versions.sarif4k.get()}",
                "io.github.detekt.sarif4k:sarif4k-jvm:${libs.versions.sarif4k.get()}",
                "io.github.oshai:kotlin-logging:${libs.versions.kotlinLogging.get()}"
            )
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.versions)
    alias(libs.plugins.version.catalog.update)
}

tasks.named<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask>("dependencyUpdates") {
    outputFormatter = "plain"
    checkForGradleUpdate = true
    filterConfigurations = Spec { configuration ->
        val isToolRuntimeConfiguration =
            listOf("detekt", "ktlint").any { toolName ->
                configuration.name.contains(toolName, ignoreCase = true)
            } ||
                configuration.dependencies.any { dependency ->
                    dependency.group == "dev.detekt" ||
                        dependency.group == "com.pinterest.ktlint"
                }
        !isToolRuntimeConfiguration
    }
    rejectVersionIf {
        val candidateVersionText = candidate.version.lowercase()
        val currentVersionText = currentVersion.lowercase()
        val unstableQualifiers = listOf("alpha", "beta", "rc", "cr", "m", "preview", "dev")
        val candidateIsUnstable = unstableQualifiers.any { qualifier -> candidateVersionText.contains(qualifier) }
        val currentIsStable = unstableQualifiers.none { qualifier -> currentVersionText.contains(qualifier) }

        candidateIsUnstable && currentIsStable
    }
}

tasks.register<Exec>("checkDependencyUpdates") {
    group = "help"
    description = "Runs dependencyUpdates with the flags required by this project."
    val gradleExecutable =
        if (System.getProperty("os.name").lowercase().contains("windows")) {
            ".\\gradlew.bat"
        } else {
            "./gradlew"
        }
    commandLine(gradleExecutable, "--no-parallel", "--no-configuration-cache", "dependencyUpdates")
}

versionCatalogUpdate {
    keep {
        // Keep only "java" even if it doesn't appear in libraries/plugins
        versions.add("java")
    }
}
