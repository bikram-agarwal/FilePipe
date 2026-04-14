import com.android.build.api.dsl.ApplicationExtension
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
    compilerOptions {
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
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
    keystoreProps.getProperty("storeFile")?.takeIf { it.isNotBlank() }?.let { rootProject.file(it) }?.takeIf { it.isFile }
val releaseStorePassword = keystoreProps.getProperty("storePassword")?.takeIf { it.isNotBlank() }
val releaseKeyAlias = keystoreProps.getProperty("keyAlias")?.takeIf { it.isNotBlank() }
val releaseKeyPassword =
    keystoreProps.getProperty("keyPassword")?.takeIf { it.isNotBlank() } ?: releaseStorePassword

val hasReleaseSigning =
    releaseStoreFile != null &&
        releaseStorePassword != null &&
        releaseKeyAlias != null &&
        releaseKeyPassword != null

extensions.configure<ApplicationExtension>("android") {
    namespace = "dev.bikram.filepipe"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.bikram.filepipe"
        minSdk = 30
        targetSdk = 36
        versionCode = 302
        versionName = "3.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

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
                "proguard-rules.pro"
            )
            matchingFallbacks += listOf("release")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // Embed native debug symbols in the AAB for Play Console crash/ANR symbolication (transitive .so from deps).
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
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
        create("playstore") {
            dimension = "distribution"
            buildConfigField("String", "GITHUB_REPO", "\"\"")
            buildConfigField("Boolean", "SHOW_UPDATES", "true")
            buildConfigField("Boolean", "USE_PLAY_IN_APP_UPDATES", "true")
            buildConfigField("String", "CHANGELOG_GITHUB_REPO", "\"bikram-agarwal/filepipe\"")
            buildConfigField("String", "CHANGELOG_GITHUB_BRANCH", "\"main\"")
        }
    }

    androidResources {
        ignoreAssetsPattern = "IconKitchen.zip"
    }

    lint {
        disable += "NullSafeMutableLiveData"
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3.expressive)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
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
    implementation(libs.appcompat)
    implementation(libs.splashscreen)

    // DocumentFile (SAF helper)
    implementation(libs.documentfile)

    implementation(libs.datastore.preferences)

    implementation(libs.material.kolor)

    // Play in-app update AARs merge extra manifest entries. App info may list permissions under the
    // "Nearby devices" group on Android 12+ even though FilePipe does not declare them in src manifests.
    // Inspect merged output: ./gradlew :app:processPlaystoreReleaseMainManifest
    add("playstoreImplementation", "com.google.android.play:app-update:2.1.0")
    add("playstoreImplementation", "com.google.android.play:app-update-ktx:2.1.0")
}
