// Root build file. Plugin versions are declared here (apply false) so every module
// resolves the SAME Kotlin toolchain. Kotlin is pinned to 2.0.20 because the Barq
// compiler plugin (io.github.barqdb.kotlin:4.0.7) is built against exactly that version.
plugins {
    kotlin("multiplatform") version "2.0.20" apply false
    kotlin("jvm") version "2.0.20" apply false
    kotlin("plugin.serialization") version "2.0.20" apply false
    id("io.github.barqdb.kotlin") version "4.0.7" apply false
}
