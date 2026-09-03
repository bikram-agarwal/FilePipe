import com.android.build.api.dsl.ApplicationExtension
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

kotlin {
    jvmToolchain(
        libs.versions.java
            .get()
            .toInt(),
    )
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
        )
    }
}

// Release signing only when keystore.properties exists (GitHub Actions writes it from secrets).
// Local assembleRelease stays unsigned; sign locally with your own tooling (e.g. apksigner).
val keystoreProps = Properties()
val keystorePropsFile = rootProject.file("keystore.properties")
if (keystorePropsFile.exists()) {
    keystorePropsFile.inputStream().use { keystoreProps.load(it) }
}

val releaseStoreFile =
    keystoreProps
        .getProperty("storeFile")
        ?.takeIf { it.isNotBlank() }
        ?.let { rootProject.file(it) }
        ?.takeIf { it.isFile }
val releaseStorePassword = keystoreProps.getProperty("storePassword")?.takeIf { it.isNotBlank() }
val releaseKeyAlias = keystoreProps.getProperty("keyAlias")?.takeIf { it.isNotBlank() }
val releaseKeyPassword =
    keystoreProps.getProperty("keyPassword")?.takeIf { it.isNotBlank() } ?: releaseStorePassword

val hasReleaseSigning =
    releaseStoreFile != null &&
        releaseStorePassword != null &&
        releaseKeyAlias != null &&
        releaseKeyPassword != null
val previewVersionSuffix =
    providers.gradleProperty("previewVersionSuffix").orNull?.takeIf { it.isNotBlank() }

extensions.configure<ApplicationExtension>("android") {
    val filePipeApplicationId = "dev.bikram.filepipe"
    namespace = filePipeApplicationId
    compileSdk = 37
    defaultConfig {
        applicationId = filePipeApplicationId
        minSdk = 31
        targetSdk = 37
        versionCode = 31001
        versionName = "3.10.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "PLAY_STORE_LISTING_URL",
            "\"https://play.google.com/store/apps/details?id=$filePipeApplicationId\"",
        )

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64", "armeabi-v7a")
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile!!
                storePassword = releaseStorePassword!!
                keyAlias = releaseKeyAlias!!
                keyPassword = releaseKeyPassword!!
            }
        }
    }

    buildTypes {
        debug {
        }
        // Declare before devRelease so initWith(getByName("release")) always resolves (Gradle
        // registers build types in declaration order; release signing above is optional).
        release {
            versionNameSuffix = previewVersionSuffix
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // Embed native debug symbols in the AAB for Play Console crash/ANR symbolication (transitive .so from deps).
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
        create("devRelease") {
            initWith(getByName("release"))
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        jniLibs {
            keepDebugSymbols += "**/libdatastore_shared_counter.so"
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            applicationIdSuffix = ".gh"
            buildConfigField("String", "GITHUB_REPO", "\"bikram-agarwal/filepipe\"")
            buildConfigField("Boolean", "SHOW_UPDATES", "true")
            buildConfigField("Boolean", "USE_PLAY_IN_APP_UPDATES", "false")
            buildConfigField("String", "CHANGELOG_GITHUB_REPO", "\"bikram-agarwal/filepipe\"")
            buildConfigField("String", "CHANGELOG_GITHUB_BRANCH", "\"main\"")
        }
        create("fdroid") {
            dimension = "distribution"
            applicationIdSuffix = ".gh"
            buildConfigField("String", "GITHUB_REPO", "\"bikram-agarwal/filepipe\"")
            buildConfigField("Boolean", "SHOW_UPDATES", "true")
            buildConfigField("Boolean", "USE_PLAY_IN_APP_UPDATES", "false")
            buildConfigField("String", "CHANGELOG_GITHUB_REPO", "\"bikram-agarwal/filepipe\"")
            buildConfigField("String", "CHANGELOG_GITHUB_BRANCH", "\"main\"")
        }
        create("playstore") {
            dimension = "distribution"
            buildConfigField("String", "GITHUB_REPO", "\"\"")
            buildConfigField("Boolean", "SHOW_UPDATES", "true")
            buildConfigField("Boolean", "USE_PLAY_IN_APP_UPDATES", "true")
            buildConfigField("String", "CHANGELOG_GITHUB_REPO", "\"bikram-agarwal/filepipe\"")
            buildConfigField("String", "CHANGELOG_GITHUB_BRANCH", "\"main\"")
        }
    }

    sourceSets {
        getByName("fdroid") {
            java.directories.add("src/github/java")
            kotlin.directories.add("src/github/java")
        }
    }

    androidResources {
        localeFilters += setOf("en")
    }

    lint {
        disable += "NullSafeMutableLiveData"
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

val copyHelpDoc =
    tasks.register<Copy>("copyHelpDoc") {
        from(rootProject.file("docs/HELP.md"))
        into("src/main/assets")
    }

tasks.named("preBuild") {
    dependsOn(copyHelpDoc)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
}

configurations.named("detekt") {
    resolutionStrategy {
        force(
            "io.github.detekt.sarif4k:sarif4k:${libs.versions.sarif4k.get()}",
            "io.github.detekt.sarif4k:sarif4k-jvm:${libs.versions.sarif4k.get()}",
            "io.github.oshai:kotlin-logging:${libs.versions.kotlinLogging.get()}",
        )
    }
}

configurations.configureEach {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-metadata-jvm:${libs.versions.kotlin.get()}")
    }
}

ktlint {
    android.set(true)
    version.set(libs.versions.ktlint.get())
}

dependencies {
    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3.expressive)
    implementation(libs.compose.material3.adaptive)
    implementation(libs.compose.material3.adaptive.layout)
    implementation(libs.compose.material3.adaptive.navigation)
    implementation(libs.compose.material3.adaptive.navigation.suite)
    implementation(libs.reorderable)
    debugImplementation(libs.compose.ui.tooling)

    // Activity + Navigation
    implementation(libs.activity.compose)
    implementation(libs.nav.compose)
    implementation(libs.hilt.nav.compose)

    // Lifecycle / ViewModel
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.process)

    // Hilt
    implementation(libs.hilt.android)
    compileOnly(libs.errorprone.annotations)
    ksp(libs.hilt.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    // Paging 3
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

    // WorkManager + Hilt integration
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Coroutines
    implementation(libs.coroutines.android)

    // Serialization (for Room TypeConverters)
    implementation(libs.serialization.json)

    // Core
    implementation(libs.core.ktx)
    implementation(libs.splashscreen)

    // DocumentFile (SAF helper)
    implementation(libs.documentfile)

    // ExifInterface (image orientation metadata; AndroidX replacement for deprecated android.media.ExifInterface)
    implementation(libs.exifinterface)

    implementation(libs.datastore.preferences)

    implementation(libs.material.kolor)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation("androidx.room:room-testing:${libs.versions.room.get()}")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")

    // Play in-app update AARs merge extra manifest entries. App info may list permissions under the
    // "Nearby devices" group on Android 12+ even though FilePipe does not declare them in src manifests.
    // Inspect merged output: ./gradlew :app:processPlaystoreReleaseMainManifest
    add("playstoreImplementation", "com.google.android.play:app-update:2.1.0")
    add("playstoreImplementation", "com.google.android.play:app-update-ktx:2.1.0")
    add("playstoreImplementation", "com.google.android.play:review:2.0.2")
    add("playstoreImplementation", "com.google.android.play:review-ktx:2.0.2")
}
