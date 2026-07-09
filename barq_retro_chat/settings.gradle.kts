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
    // Lets Gradle download a matching JDK for the toolchain if one isn't installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "barq-retro-chat"

// :shared  — Kotlin Multiplatform wire protocol (JVM + JS), the single source of truth
//            for every message that crosses the WebSocket.
// :server  — Ktor + BarqDB. Owns the database and turns Barq change-notifications into
//            realtime WebSocket pushes.
// :client  — Kotlin/JS browser app. The retro UI. Talks WebSocket only.
include(":shared", ":server", ":client")
