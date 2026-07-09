plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    js(IR) {
        // The whole browser app bundles to a single `client.js` that the server serves.
        browser {
            commonWebpackConfig {
                outputFileName = "client.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        jsMain.dependencies {
            implementation(project(":shared"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
            // kotlinx.html builds the retro UI as real DOM — no HTML templates, pure Kotlin.
            implementation("org.jetbrains.kotlinx:kotlinx-html:0.11.0")
        }
    }
}
