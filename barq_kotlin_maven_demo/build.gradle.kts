plugins {
    // Barq's compiler plugin is tied to this exact Kotlin version — do not change it
    // independently of the barqdb version.
    kotlin("jvm") version "2.0.20"
    // The Barq Gradle plugin. Applying it wires in the Barq compiler plugin that
    // turns plain `BarqObject` classes into managed, persistable database models.
    id("io.github.barqdb.kotlin") version "4.0.6"
    application
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    // The only line you need to pull barqdb from Maven Central. This is a
    // Kotlin Multiplatform artifact; on a JVM project Gradle automatically
    // selects the `-jvm` variant, which bundles the native engine (libbarqc).
    implementation("io.github.barqdb.kotlin:library-base:4.0.6")

    // Barq's write/query APIs are coroutine- and Flow-based.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.0")
}

kotlin {
    // Barq's JVM engine needs JDK 11+. 17 is a safe, widely-available default;
    // the foojay resolver in settings.gradle.kts fetches it if it's missing.
    jvmToolchain(17)
}

application {
    mainClass.set("io.github.barqdb.demo.MainKt")
}
