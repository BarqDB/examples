pluginManagement {
    repositories {
        gradlePluginPortal()
        // The Barq Gradle plugin (io.github.barqdb.kotlin) is published to Maven Central,
        // not the Gradle Plugin Portal, so it must be resolvable from here.
        mavenCentral()
        google()
    }
}

plugins {
    // Lets Gradle download a matching JDK for the toolchain if one isn't installed,
    // so `./gradlew run` works on any machine without a manual JDK install.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "barq-kotlin-maven-demo"
