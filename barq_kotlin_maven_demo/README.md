# barqdb Kotlin demo (Maven Central)

A tiny Kotlin/JVM CLI that pulls **barqdb** straight from Maven Central and shows off
what it can do. No local build of the SDK is needed — Gradle downloads
`io.github.barqdb.kotlin:library-base:4.0.7` and the native engine for you.

## Run it

```bash
./gradlew run
```

That's it. The first run downloads Gradle, a JDK for the toolchain, and the
barqdb artifacts, so give it a minute. After that it's instant.

## What it demonstrates

The program (`src/main/kotlin/io/github/barqdb/demo/`) walks through the core
features in order:

1. **Open a local database** — embedded, file-based, zero config. No server.
2. **Transactional writes** — create objects inside `writeBlocking { }`.
3. **Relationships** — a `Project` holds a to-many `BarqList<Task>`; each `Task`
   has a to-one link to a `Person`. Saved with a single `copyToBarq` (cascades).
4. **Queries** — the Barq Query Language: `query<Task>("done == false").sort(...)`.
5. **Arguments** — parameterized filters: `query<Task>("priority >= $0", 4)`.
6. **Aggregations** — `count()`, `sum()`, `max()` run in the engine.
7. **Full-text search** — `@FullText` field queried with `notes TEXT $0`.
8. **Relationship traversal** — walk `project → tasks → assignee`.
9. **Updates & deletes** — fetch-and-mutate / `delete()` inside a transaction.
10. **Reactive queries** — `asFlow()` emits the current result set and then a
    fresh emission every time the data changes (how you'd drive a live UI).

## How it's wired (two lines)

`build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") version "2.0.20"
    id("io.github.barqdb.kotlin") version "4.0.7"   // Barq compiler plugin
    application
}

dependencies {
    implementation("io.github.barqdb.kotlin:library-base:4.0.7")
}
```

The Barq Gradle plugin lives on Maven Central (not the Gradle Plugin Portal), so
`settings.gradle.kts` adds `mavenCentral()` to `pluginManagement { repositories { } }`.

## Notes

- **Platform:** the published `4.0.7` JVM artifact bundles the macOS engine
  (`libbarqc.dylib`, universal x86_64 + arm64), so `./gradlew run` works out of
  the box on macOS. The same `library-base` coordinate also targets Android,
  iOS, and macOS/native — see the other samples under `example/`.
- **Kotlin version is pinned** to `2.0.20` on purpose: Barq's compiler plugin is
  built against that exact compiler. Bump them together.
- The database file is written to `build/barqdb/demo.barq` and recreated on each
  run so the demo is repeatable.
