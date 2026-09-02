plugins {
    id("com.android.application")
    id("kotlin-android")
    id("dev.flutter.flutter-gradle-plugin")
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
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
val castReceiverAppId = releaseValue("DEXTOP_CAST_RECEIVER_APP_ID") ?: "BABD4047"
val releaseSigningReady = listOf(
    releaseKeystore, releaseAlias, releaseStorePassword, releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "moe.n4tsu.dextop"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    defaultConfig {
        applicationId = "moe.n4tsu.dextop"
        // Dextop relies on the modern display/windowing APIs introduced with
        // Android 11. Keeping the declared floor aligned with that runtime
        // contract avoids exposing an unsupported setup path on Android 10.
        minSdk = 30
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
        buildConfigField("String", "CAST_RECEIVER_APP_ID", "\"$castReceiverAppId\"")
        buildConfigField("boolean", "EMBEDDED_STELLAR", "true")

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++20", "-Wall", "-Wextra", "-Werror=return-type")
            }
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            buildConfigField("String", "DISTRIBUTION_CHANNEL", "\"github\"")
            buildConfigField("boolean", "EMBEDDED_STELLAR", "true")
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("String", "DISTRIBUTION_CHANNEL", "\"play\"")
            buildConfigField("boolean", "EMBEDDED_STELLAR", "true")
        }
    }

    buildFeatures {
        buildConfig = true
        aidl = true
    }

    packaging {
        resources {
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
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
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

flutter {
    source = "../.."
}

val generateNativeStrings by tasks.registering(Exec::class) {
    group = "localization"
    description = "Generates NativeStrings.kt from the Flutter ARB catalogs"
    workingDir(rootProject.file(".."))
    commandLine("python3", "tool/generate_native_strings.py")
    inputs.files(
        fileTree(rootProject.file("../lib/l10n")) {
            include("app_*.arb")
        }
    )
    outputs.file(file("src/main/kotlin/moe/n4tsu/dextop/NativeStrings.kt"))
}

tasks.configureEach {
    if ((name.startsWith("compile") && name.endsWith("Kotlin")) ||
        name.contains("SourceSetPaths")
    ) {
        dependsOn(generateNativeStrings)
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.packaging.jniLibs.excludes.addAll(
            listOf(
                "lib/armeabi-v7a/**",
                "lib/x86/**",
                "lib/x86_64/**"
            )
        )
    }
}

dependencies {
    implementation("androidx.window:window:1.5.1")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.gms:play-services-cast-framework:21.5.0")
    implementation("androidx.media3:media3-muxer:1.10.1")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    // 2.1.1 is the newest Kadb line compatible with this project's compileSdk 36.
    implementation("com.flyfishxu:kadb:2.1.1")
    testImplementation(kotlin("test"))
}

tasks.configureEach {
    if (name in setOf(
            "packageGithubRelease",
            "bundleGithubRelease",
            "packagePlayRelease",
            "bundlePlayRelease"
        )) {
        doFirst {
            if (!releaseSigningReady) {
                throw GradleException(
                    "Release signing is not configured. Copy .env.example to .env and set DEXTOP_* values."
                )
            }
        }
    }
}
