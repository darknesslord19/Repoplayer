import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

// Bu modül, CI sırasında CloudStream kaynak ağacının içine "player" olarak kopyalanır.
// Böylece .cs3 eklentilerinin ihtiyaç duyduğu CloudStream kütüphanesi (:library)
// doğrudan kaynaktan derlenir ve sürüm kataloğu (libs.*) ortak kullanılır.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.repoplayer.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.repoplayer.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // CI'da her derleme numarası yeni bir sürümdür (uygulama içi güncelleme denetimi bunu kullanır)
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
        versionCode = 100 + run
        versionName = "1.0.$run"
    }

    signingConfigs {
        create("shared") {
            storeFile = file("keystore.jks")
            storePassword = System.getenv("RP_STORE_PASSWORD") ?: "repoplayer"
            keyAlias = System.getenv("RP_KEY_ALIAS") ?: "repoplayer"
            keyPassword = System.getenv("RP_KEY_PASSWORD") ?: "repoplayer"
        }
    }

    buildTypes {
        release {
            // Eklentiler sınıflara yansıma ile eriştiği için küçültme KAPALI olmalı
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("shared")
        }
        debug {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        jniLibs { useLegacyPackaging = true }
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/INDEX.LIST")
        }
    }
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        optIn.addAll(
            "com.lagradost.cloudstream3.InternalAPI",
            "com.lagradost.cloudstream3.Prerelease",
            "androidx.compose.material3.ExperimentalMaterial3Api",
        )
    }
}

dependencies {
    // .cs3 eklentilerinin çalıştığı CloudStream API'si (MainAPI, ExtractorLink, extractor'lar...)
    implementation(project(":library"))
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)

    implementation(libs.core.ktx)
    implementation(libs.annotation)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.coil.compose)
    implementation(libs.bundles.coil)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.ui)
    implementation(libs.media3.common)
    implementation(libs.media3.datasource.okhttp)

    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlinx.coroutines.core)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${libs.versions.kotlinxCoroutines.get()}")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
