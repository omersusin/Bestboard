plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "handboard.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "handboard.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 4
        versionName = "3.1.0"
    }

    // ponytail: release signing from env/secrets (CI) or local keystore.properties (dev).
    // Without both, release builds stay unsigned — debug is unaffected.
    signingConfigs {
        val keystorePath = System.getenv("BESTBOARD_KEYSTORE_PATH")
            ?: project.findProperty("bestboard.keystore.path")?.toString()
        if (!keystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("BESTBOARD_KEYSTORE_PASSWORD")
                    ?: project.findProperty("bestboard.keystore.password")?.toString()
                keyAlias = System.getenv("BESTBOARD_KEY_ALIAS")
                    ?: project.findProperty("bestboard.key.alias")?.toString()
                keyPassword = System.getenv("BESTBOARD_KEY_PASSWORD")
                    ?: project.findProperty("bestboard.key.password")?.toString()
            }
        }
    }

    buildTypes {
        release {
            // ponytail: minify back ON — the ViewTree-owner crash that forced it off is fixed.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.3")
    // ponytail: was transitive-only (build audit) — declare explicitly.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // JVM unit tests (no emulator): Trie, predictor, parsers, currency math.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    // Emoji Picker
    implementation("androidx.emoji2:emoji2:1.5.0")
    implementation("androidx.emoji2:emoji2-emojipicker:1.5.0")
}
