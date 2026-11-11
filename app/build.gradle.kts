plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// versionCode は更新のたびに増やすこと。
// CI では GitHub Actions の run_number を VERSION_CODE として渡す(README参照)。
// GitHub Actions は未設定の secrets を空文字で渡すため、空文字は未設定として扱う。
fun envOrNull(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

val appVersionCode: Int = (envOrNull("VERSION_CODE") ?: findProperty("VERSION_CODE") as String?)
    ?.toIntOrNull() ?: 1
val appVersionName: String = "1.0.0"

android {
    namespace = "jp.achi.homelauncher"
    compileSdk = 36

    defaultConfig {
        applicationId = "jp.achi.homelauncher"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    // 署名は固定。環境変数があればそちらを優先(GitHub Secrets 用)。
    signingConfigs {
        create("release") {
            storeFile = file(envOrNull("KEYSTORE_FILE") ?: "release.keystore")
            storePassword = envOrNull("KEYSTORE_PASSWORD") ?: "homelauncher"
            keyAlias = envOrNull("KEY_ALIAS") ?: "homelauncher"
            keyPassword = envOrNull("KEY_PASSWORD") ?: "homelauncher"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // debug と release で署名を揃え、上書きインストールできるようにする
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
