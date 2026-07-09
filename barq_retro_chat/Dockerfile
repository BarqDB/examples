# syntax=docker/dockerfile:1

# BarqDB's JVM engine bundles a linux-x86_64 native library, so we pin amd64. On an x86_64
# server this runs natively; on Apple Silicon it runs under emulation (still correct, just slower).

# ───────────────────────── build stage ─────────────────────────
FROM --platform=linux/amd64 eclipse-temurin:17-jdk AS build
WORKDIR /src

# Build scripts first, so dependency resolution is cached separately from source changes.
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY shared/build.gradle.kts ./shared/
COPY server/build.gradle.kts ./server/
COPY client/build.gradle.kts ./client/
RUN chmod +x gradlew

# Sources.
COPY shared ./shared
COPY server ./server
COPY client ./client

# Compile everything (incl. the Kotlin/JS client + webpack bundle) and lay down a runnable
# distribution: server/build/install/server/{bin,lib}. Needs network for Gradle + deps + the
# Kotlin/JS toolchain — a from-scratch build takes a few minutes.
RUN ./gradlew --no-daemon --console=plain :server:installDist

# ───────────────────────── runtime stage ─────────────────────────
FROM --platform=linux/amd64 eclipse-temurin:17-jre AS runtime
WORKDIR /app

# curl is only here for the container HEALTHCHECK.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 --home /app messenger \
 && mkdir -p /data && chown -R messenger:messenger /app /data
USER messenger

COPY --from=build --chown=messenger:messenger /src/server/build/install/server ./

ENV PORT=8080 \
    BARQ_DATA_DIR=/data \
    BARQ_BOTS=off \
    JAVA_OPTS="-Xmx512m"

EXPOSE 8080
VOLUME ["/data"]

HEALTHCHECK --interval=30s --timeout=3s --start-period=45s --retries=3 \
  CMD curl -fsS "http://localhost:${PORT}/healthz" || exit 1

# The Gradle-generated start script honors JAVA_OPTS.
CMD ["./bin/server"]
