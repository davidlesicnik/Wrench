import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.lesicnik.wrench"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lesicnik.wrench"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // 1. Try to get secrets from System Environment (GitHub Actions)
            val keystoreFilePath = System.getenv("RELEASE_KEYSTORE_PATH")
            val keystorePass = System.getenv("RELEASE_KEYSTORE_PASSWORD")
            val alias = System.getenv("RELEASE_KEY_ALIAS")
            val aliasPass = System.getenv("RELEASE_KEY_PASSWORD")

            // 2. If env vars are present, use them
            if (keystoreFilePath != null && keystorePass != null) {
                storeFile = rootProject.file(keystoreFilePath)
                storePassword = keystorePass
                keyAlias = alias
                keyPassword = aliasPass
            }
            // 3. Optional: Fallback for local builds using local.properties
            else {
                val localProperties = Properties()
                val localFile = rootProject.file("local.properties")
                if (localFile.exists()) {
                    localProperties.load(FileInputStream(localFile))
                    val localStoreFile = localProperties.getProperty("storeFile")
                    if (localStoreFile != null) {
                        storeFile = rootProject.file(localStoreFile)
                        storePassword = localProperties.getProperty("storePassword")
                        keyAlias = localProperties.getProperty("keyAlias")
                        keyPassword = localProperties.getProperty("keyPassword")
                    }
                }
            }

        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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

    val isReleaseTask = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
    if (isReleaseTask && signingConfigs.getByName("release").storeFile == null) {
        throw GradleException(
            "Release signing config is missing. Set RELEASE_KEYSTORE_* env vars " +
                "or provide signing entries in local.properties."
        )
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
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

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Retrofit + OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Image loading
    implementation(libs.coil.compose)

    // Security
    implementation(libs.androidx.security.crypto)

    // Charts
    implementation(libs.vico.compose.m3)

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
