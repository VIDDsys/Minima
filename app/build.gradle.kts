plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.minima.chat"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.minima.chat"
        minSdk = 26
        targetSdk = 36
        versionCode = 55
        versionName = "2.42.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("minimaDebug") {
            val ks = rootProject.file("keystore/minima-debug.jks")
            if (ks.exists()) {
                storeFile = ks
                storePassword = "minima-debug"
                keyAlias = "minima"
                keyPassword = "minima-debug"
            }
        }
        create("minimaUpload") {
            val ks = rootProject.file("keystore/minima-upload.jks")
            val passFile = rootProject.file("keystore/upload-pass.local")
            val storePass = System.getenv("MINIMA_UPLOAD_STORE_PASSWORD")
                ?: passFile.takeIf { it.exists() }?.readText()?.trim().orEmpty()
            val keyPass = System.getenv("MINIMA_UPLOAD_KEY_PASSWORD") ?: storePass
            if (ks.exists() && storePass.isNotBlank()) {
                storeFile = ks
                storePassword = storePass
                keyAlias = "minima-upload"
                keyPassword = keyPass
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Prefer upload keystore when env/file present; else app-specific debug for sideload smoke.
            val useUpload = System.getenv("MINIMA_USE_UPLOAD_SIGNING") == "1"
            val uploadCfg = signingConfigs.findByName("minimaUpload")
            val debugCfg = signingConfigs.findByName("minimaDebug")
            signingConfig = when {
                useUpload && uploadCfg?.storeFile != null -> uploadCfg
                debugCfg?.storeFile != null -> debugCfg
                else -> signingConfigs.getByName("debug")
            }
        }
        debug {
            isMinifyEnabled = false
            val debugCfg = signingConfigs.findByName("minimaDebug")
            if (debugCfg?.storeFile != null) {
                signingConfig = debugCfg
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":core:network"))
    implementation(project(":core:agent"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:settings"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
}
