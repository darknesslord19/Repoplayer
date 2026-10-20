// Teşhis aracı: CS3 eklentilerini (dex2jar ile jar'a çevrilmiş) masaüstü JVM'de çalıştırır.
// Yalnızca GitHub Actions'taki "Teşhis" iş akışında CloudStream ağacına eklenir.
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":library"))
    implementation(libs.nicehttp)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlinx.coroutines.core)
}

application { mainClass.set("diag.MainKt") }
