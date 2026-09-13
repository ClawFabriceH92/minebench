import java.io.File
import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun releaseKeystore(): File? {
    System.getenv("MINEBENCH_KEYSTORE_B64")?.let { b64 ->
        val tmp = File(System.getenv("RUNNER_TEMP") ?: "/tmp", "minebench-release.keystore")
        tmp.writeBytes(Base64.getDecoder().decode(b64))
        return tmp
    }
    val candidates = listOf(
        File(System.getProperty("user.home"), ".secrets/keystores-android/minebench-release.keystore"),
        File("/root/.secrets/keystores-android/minebench-release.keystore"),
    )
    return candidates.firstOrNull { it.exists() }
}

android {
    namespace = "com.trucdecomptable.minebench"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.trucdecomptable.minebench"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"
    }

    val ks = releaseKeystore()
    if (ks != null) {
        signingConfigs {
            create("release") {
                storeFile = ks
                storePassword = System.getenv("MINEBENCH_KEYSTORE_PASSWORD") ?: "minebench2026"
                keyAlias = System.getenv("MINEBENCH_KEY_ALIAS") ?: "minebench"
                keyPassword = System.getenv("MINEBENCH_KEY_PASSWORD") ?: "minebench2026"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            ks?.let { signingConfig = signingConfigs.getByName("release") }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        checkReleaseBuilds = false
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    debugImplementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
