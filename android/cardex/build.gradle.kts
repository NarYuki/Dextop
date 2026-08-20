plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseEnvironment = mutableMapOf<String, String>()
val environmentFile = rootProject.file("../.env")
if (environmentFile.exists()) {
    environmentFile.readLines().forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isNotEmpty() && !line.startsWith("#") && line.contains('=')) {
            val separator = line.indexOf('=')
            releaseEnvironment[line.substring(0, separator).trim()] =
                line.substring(separator + 1).trim().removeSurrounding("\"").removeSurrounding("'")
        }
    }
}

fun releaseValue(name: String): String? =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: releaseEnvironment[name]?.takeIf { it.isNotBlank() }

val releaseKeystore = releaseValue("DEXTOP_KEYSTORE_FILE")
val releaseAlias = releaseValue("DEXTOP_KEY_ALIAS")
val releaseStorePassword = releaseValue("DEXTOP_STORE_PASSWORD")
val releaseKeyPassword = releaseValue("DEXTOP_KEY_PASSWORD")
val releaseSigningReady = listOf(
    releaseKeystore, releaseAlias, releaseStorePassword, releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "moe.n4tsu.cardex"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    defaultConfig {
        applicationId = "moe.n4tsu.cardex"
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = rootProject.file("../$releaseKeystore")
                storePassword = releaseStorePassword
                keyAlias = releaseAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            if (releaseSigningReady) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation("androidx.car.app:app:1.7.0")
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation(kotlin("test"))
}

tasks.configureEach {
    if (name == "packageRelease" || name == "bundleRelease") {
        doFirst {
            if (!releaseSigningReady) {
                throw GradleException("Dextop Car Companion release signing requires the DEXTOP_* values in .env")
            }
        }
    }
}
