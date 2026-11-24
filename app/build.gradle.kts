plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.hesabgara.anbargardan"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.hesabgara.anbargardan"
        minSdk = 24
        targetSdk = 34
        versionCode = (project.findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("appVersionName") as String?) ?: "1.0.0"
    }
    buildFeatures { compose = true }
    // One fixed key for every build, so a new APK installs over the old one (in-app updates keep user data).
    signingConfigs {
        create("app") {
            storeFile = file("anbargardani.jks")
            storePassword = "anbargardani1405"
            keyAlias = "anbargardani"
            keyPassword = "anbargardani1405"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("app") }
        release {
            signingConfig = signingConfigs.getByName("app")
            isMinifyEnabled = false
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
