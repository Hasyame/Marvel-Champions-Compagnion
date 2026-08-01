import java.io.File
import java.net.URI

plugins {
    // AGP 9 ships Kotlin support built in; the standalone
    // org.jetbrains.kotlin.android plugin must not be applied alongside it.
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.hasyame.marvelchampions"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hasyame.marvelchampions"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    androidResources {
        // Locales the app actually ships translations for. Keeps the per-app
        // language picker (res/xml/locales_config.xml) in sync with reality.
        localeFilters += listOf("en", "fr")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // Robolectric needs the merged resources and assets to serve
            // pack_metadata.json to PackMetadataAssetTest.
            isIncludeAndroidResources = true
        }
    }

    kotlin {
        jvmToolchain(21)
    }

    lint {
        warningsAsErrors = true
        checkDependencies = true
        // Reported against the version catalog on every new library release;
        // not a reason to fail CI.
        disable += "GradleDependency"
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

/**
 * Downloads the card snapshot bundled into the APK.
 *
 * The output is gitignored on purpose: it is Fantasy Flight's card text, and
 * committing it would republish it (see README, Legal). Run this before an
 * install if you want the app usable offline on first launch; without it the
 * app simply asks for a sync, which is how CI builds.
 */
tasks.register("fetchCardSeed") {
    group = "marvelchampions"
    description = "Downloads the MarvelCDB card and pack snapshot into assets/seed (not committed)."

    val outputDir = layout.projectDirectory.dir("src/main/assets/seed")
    outputs.dir(outputDir)
    // Always hits the network; caching a snapshot of a live API would defeat
    // the point of the task.
    outputs.upToDateWhen { false }

    // Resolved at configuration time. Referencing anything from the build
    // script inside doLast would break the configuration cache.
    val seedDir = outputDir.asFile
    val targets = mapOf(
        // encounter=1 is required. Without it the endpoint silently returns
        // only player cards and omits every encounter card.
        "cards_en.json" to "https://marvelcdb.com/api/public/cards/?encounter=1",
        "cards_fr.json" to "https://fr.marvelcdb.com/api/public/cards/?encounter=1",
        "packs_en.json" to "https://marvelcdb.com/api/public/packs/",
        "packs_fr.json" to "https://fr.marvelcdb.com/api/public/packs/",
    )

    doLast {
        seedDir.mkdirs()
        targets.forEach { (fileName, url) ->
            val destination = File(seedDir, fileName)
            logger.lifecycle("fetchCardSeed: $url")
            URI(url).toURL().openStream().use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            logger.lifecycle(
                "fetchCardSeed: wrote ${destination.name} (${destination.length()} bytes)",
            )
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
}
