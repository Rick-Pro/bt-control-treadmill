plugins {
    alias(libs.plugins.android.application) // AGP 9 compiles Kotlin itself, so there is no kotlin-android plugin.
    alias(libs.plugins.kotlin.compose)
    // Not applied: only puts the catalog's Kotlin Gradle plugin (2.4.21) next to AGP, which would pull 2.2.10.
    // It has to sit in this module's classloader with AGP, not in the root build, because it needs AGP's classes.
    alias(libs.plugins.kotlin.jvm) apply false
}

// versionName: -Pstride.versionName wins, else the VERSION file plus "-dev".
// versionCode: -Pstride.versionCode wins, else major * 1,000,000 + minor * 1,000 + patch for a plain X.Y.Z, else 1.
// So local and PR builds are code 1 and can never replace a release (Android refuses downgrades).
val baseVersion = providers.fileContents(rootProject.layout.projectDirectory.file("VERSION")).asText.get().trim()
val appVersionName: String = providers.gradleProperty("stride.versionName").getOrElse("$baseVersion-dev")
val appVersionCode: Int = providers.gradleProperty("stride.versionCode").orNull?.toInt()
    ?: Regex("""(\d+)\.(\d+)\.(\d+)""").matchEntire(appVersionName)?.destructured?.let { (major, minor, patch) ->
        require(minor.toInt() <= 999 && patch.toInt() <= 999) { "Minor and patch must be 999 or less: $appVersionName" }
        major.toInt() * 1_000_000 + minor.toInt() * 1_000 + patch.toInt()
    }
    ?: 1

android {
    namespace = "io.github.rickpro.stride"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.rickpro.stride"
        minSdk = 33
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        // Public by design (decision D6 in docs/plan.md): the standard Android debug key settings, committed so every
        // test build has the same signature and installs over the last one. It is not the release key.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug" // installs next to a release build; the label is "Stride Dev" (src/debug)
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // No signing config on purpose: the Release workflow signs with the real key, so a release APK can never
            // go out with the debug key.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }
    androidResources { localeFilters += "en" } // drop unused AndroidX translations
    dependenciesInfo {
        includeInApk = false // no Play-only dependency block in a sideloaded APK
        includeInBundle = false
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

// The whole list (docs/plan.md section 6). config/allowed-dependency-groups.txt and CI keep it that way.
dependencies {
    implementation(project(":core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.core)
    implementation(libs.kotlinx.coroutines.android) // Handler.asCoroutineDispatcher for the Bluetooth thread
    debugImplementation(libs.androidx.compose.ui.tooling)
}
