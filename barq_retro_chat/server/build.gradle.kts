import org.gradle.api.tasks.JavaExec
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    // Applying the Barq Gradle plugin wires in the Barq compiler plugin that turns plain
    // `BarqObject` classes (see Models.kt) into managed, persistable database models.
    id("io.github.barqdb.kotlin")
    application
}

val ktorVersion = "2.3.12"

dependencies {
    implementation(project(":shared"))

    // BarqDB — the local, embedded realtime database. The JVM variant bundles the native
    // engine (libbarqc), so there is nothing else to install.
    implementation("io.github.barqdb.kotlin:library-base:4.0.7")

    // Ktor: HTTP + WebSockets. The whole realtime bridge is Barq change-notifications
    // forwarded over these sockets.
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("ch.qos.logback:logback-classic:1.5.6")

    // The seeder streams a .bz2 dataset; Commons Compress decodes bzip2.
    implementation("org.apache.commons:commons-compress:1.26.2")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("io.github.barqdb.chat.server.ServerKt")
}

// ── One-time bulk seeder ────────────────────────────────────────────────────
// Downloads a real, CC BY 2.0 public dataset (Tatoeba English sentences) and bulk-imports
// ~1,000,000 of them into BarqDB as historical messages, so the full-text search + counters
// run against genuinely large data. Run with the server stopped:
//   ./gradlew :server:seed                 # ~1,000,000 rows (default)
//   ./gradlew :server:seed --args="250000" # a smaller sample
tasks.register<JavaExec>("seed") {
    group = "application"
    description = "Bulk-import ~1M real Tatoeba sentences into BarqDB for the huge-data demo."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.github.barqdb.chat.server.SeederKt")
    // Big batched writes like a bit more heap.
    jvmArgs = listOf("-Xmx1g")
}

// ── Bundle the compiled Kotlin/JS client into the server jar ────────────────
// The browser app (:client) compiles to `client.js`. We copy the whole browser
// distribution into the server's resources under `web/`, and Ktor serves it as
// static content. One `./gradlew :server:run` therefore serves BOTH the API and
// the fully-built pure-Kotlin front end.
tasks.named<ProcessResources>("processResources") {
    dependsOn(":client:jsBrowserDistribution")
    from(project(":client").layout.buildDirectory.dir("dist/js/productionExecutable")) {
        into("web")
    }
}
