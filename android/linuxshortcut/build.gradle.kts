plugins {
    id("com.android.application")
}

// Template for the home-screen launchers of Linux applications. Dextop copies this APK,
// renames its package, label and icon for one application, signs it on the device and
// installs it; tapping the launcher opens the application through Dextop.
android {
    namespace = "moe.n4tsu.dextop.lxstub"
    compileSdk = 36

    defaultConfig {
        applicationId = "moe.n4tsu.dextop.lxstub"
        minSdk = 26
        // Below 30, so the copied resources.arsc needs no alignment and package
        // visibility does not hide Dextop from the launcher.
        targetSdk = 29
        // Dextop reinstalls launchers older than its own template (LinuxShortcutInstaller.VERSION).
        versionCode = 3
        versionName = "3.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            // The Gradle setup adds the Kotlin standard library; R8 drops it from this Java-only app.
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    packaging {
        resources.excludes += listOf("kotlin/**", "META-INF/**", "kotlin-tooling-metadata.json")
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}
