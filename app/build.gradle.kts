import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.kapt")
}

val isCiBuild = System.getenv("GITHUB_ACTIONS")?.equals("true", ignoreCase = true) == true
val requireReleaseSigningProperty = providers.gradleProperty("requireReleaseSigning").orNull
val requireReleaseSigning =
    when (requireReleaseSigningProperty?.trim()?.lowercase()) {
        "true" -> true
        "false" -> false
        else -> isCiBuild
    }

var releaseSigningConfigured = false

android {
    namespace = "com.lesicnik.wrench"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lesicnik.wrench"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val releaseSigning =
        signingConfigs.create("release") {
            // 1. Try to get secrets from System Environment (e.g., CI)
            val keystoreFilePath = System.getenv("RELEASE_KEYSTORE_PATH")
            val keystorePass = System.getenv("RELEASE_KEYSTORE_PASSWORD")
            val alias = System.getenv("RELEASE_KEY_ALIAS")
            val aliasPass = System.getenv("RELEASE_KEY_PASSWORD")

            // 2. If env vars are present, use them
            if (!keystoreFilePath.isNullOrBlank() && !keystorePass.isNullOrBlank()) {
                storeFile = rootProject.file(keystoreFilePath)
                storePassword = keystorePass
                keyAlias = alias
                keyPassword = aliasPass
            } else {
                // 3. Optional: Fallback for local builds using local.properties
                val localFile = rootProject.file("local.properties")
                if (localFile.exists()) {
                    val localProperties = Properties()
                    FileInputStream(localFile).use(localProperties::load)
                    val localStoreFile = localProperties.getProperty("storeFile")
                    if (!localStoreFile.isNullOrBlank()) {
                        storeFile = rootProject.file(localStoreFile)
                        storePassword = localProperties.getProperty("storePassword")
                        keyAlias = localProperties.getProperty("keyAlias")
                        keyPassword = localProperties.getProperty("keyPassword")
                    }
                }
            }
        }

    releaseSigningConfigured =
        releaseSigning.storeFile != null &&
            releaseSigning.storeFile!!.exists() &&
            !releaseSigning.storePassword.isNullOrBlank() &&
            !releaseSigning.keyAlias.isNullOrBlank() &&
            !releaseSigning.keyPassword.isNullOrBlank()

    val isReleaseArtifactRequested =
        gradle.startParameter.taskNames
            .map { it.substringAfterLast(":") }
            .any { taskName ->
                taskName.equals("assembleRelease", ignoreCase = true) ||
                    taskName.equals("bundleRelease", ignoreCase = true)
            }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseSigningConfigured) {
                signingConfig = releaseSigning
            } else {
                if (isReleaseArtifactRequested) {
                    project.logger.warn(
                        "Release signing is not configured; release artifacts will be unsigned. " +
                            "Set RELEASE_KEYSTORE_* env vars or add storeFile/storePassword/keyAlias/keyPassword to local.properties."
                    )
                }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    packaging {
        resources {
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) { variantBuilder ->
        if (!isCiBuild) {
            variantBuilder.enable = false
        }
    }
}

tasks
    .matching {
        it.name.equals("assembleRelease", ignoreCase = true) ||
            it.name.equals("bundleRelease", ignoreCase = true)
    }
    .configureEach {
        doFirst {
            if (requireReleaseSigning && !releaseSigningConfigured) {
                throw GradleException(
                    "Release signing config is missing. Set RELEASE_KEYSTORE_* env vars " +
                        "or provide signing entries in local.properties."
                )
            }
        }
    }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Retrofit + OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Image loading
    implementation(libs.coil.compose)

    // Security
    implementation(libs.androidx.security.crypto)

    // Charts
    implementation(libs.vico.compose.m3)

    // Offline cache + sync
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
