# barqDB Messenger — a realtime retro chatroom

A pure-Kotlin, full-stack web app that shows off **BarqDB** as a realtime database. It's a
2010-style chatroom: pick a screen name, join a room, and chat in realtime. Every message is
pushed by the database the instant it's written — **the message stream never polls** — and every
number on screen (messages stored, people online, msg/s, write latency) is read live from BarqDB.

<p align="center"><i>Welcome → pick a room → chat. The status bar shows the database's real
counters — starting at zero and climbing as you (and anyone else) actually use it.</i></p>

## Why it's a good BarqDB demo

The whole realtime pipeline is one BarqDB feature: **a query that is also a `Flow`.**

```
browser ──ws──▶ server ──barq.write { copyToBarq(msg) }──▶  BarqDB
                                                              │  (change notification)
browser ◀─ws── server ◀── barq.query(...).asFlow() collect ◀─┘
```

1. A browser opens a WebSocket and joins a room.
2. For that browser the server opens `barq.query<Message>("room == $0", room).asFlow()`.
   The **first** emission is the room's history; **every emission after that** is whatever
   BarqDB reports as freshly inserted.
3. When anyone — a person or a bot — sends a message, the server just **writes it to BarqDB**.
   It never hand-delivers the message. The database wakes up every matching subscription and
   the new row fans out to all viewers.

Nobody ever asks the database "anything new?". That's the point.

> **Precise claim:** the *message stream* is pure subscription — zero polling. A light 2-second
> heartbeat re-pushes the aggregate counters (total/room message counts, msg/s, who's online),
> because "messages per second" is inherently a time-sampled number. That heartbeat reads
> in-memory counters, not the database.

**Every number on screen is real** — read straight from BarqDB, nothing seeded or inflated:

- *messages stored* (total and per-room) = `barq.query<Message>().count()`
- *online* per room = the browsers actually connected to it
- *msg/s* per room = messages actually written per second, sampled each heartbeat
- *users served* = real session count, persisted across restarts
- *write latency* = genuinely timed around `barq.write { }`

Fresh out of the box a room reads `0 online · 0 msg/s · 0 stored` — because it really is empty.
Open a second browser window and the numbers move for real. To simulate a crowd for a load demo,
set `BARQ_BOTS=on`: bots then write **real** rows into BarqDB (so the stored counts and msg/s they
produce are genuine); they're never counted as "online" because they aren't connected.

The database also **persists** — counts survive a restart (files live in `server/data/barqdb`, or
wherever `BARQ_DATA_DIR` points). Delete that directory for a clean slate.

## Project layout

| Module | Kotlin target | Role |
| --- | --- | --- |
| `:shared` | Multiplatform (JVM + JS) | The `@Serializable` WebSocket protocol, shared by both ends |
| `:server` | JVM | Ktor + **BarqDB**. Owns the database; turns change-notifications into WS pushes |
| `:client` | JS (browser) | The retro UI, built with `kotlinx.html` — no HTML templates, pure Kotlin |

BarqDB is pulled straight from Maven Central:

```kotlin
id("io.github.barqdb.kotlin") version "4.0.7"          // compiler + gradle plugin
implementation("io.github.barqdb.kotlin:library-base:4.0.7")
```

## Run it

```bash
./gradlew :server:run
```

Then open <http://localhost:8080>. The server compiles the Kotlin/JS client, bundles it into
its own resources, opens a local BarqDB database, and serves everything on one port.

Open the page in **two browser windows**, join the same room in both, and type — each message
shows up in the other window the instant it's written to the database, and the online / stored /
msg-s counters climb for real.

Want it to look busy on its own? Run with simulated load:

```bash
BARQ_BOTS=on ./gradlew :server:run
```

> First run downloads the Gradle distribution, the Kotlin/JS toolchain, and dependencies, so
> give it a minute. `PORT=9000 ./gradlew :server:run` changes the port.

## Huge data — search a million real messages

The chat proves *realtime*; this proves *scale*. A one-time seeder bulk-imports a real public
dataset so BarqDB's full-text search and the counters run against genuinely large data:

```bash
./gradlew :server:seed                    # imports ~1,000,000 messages (run with the server stopped)
./gradlew :server:seed --args="250000"    # a smaller sample
```

It streams the **Tatoeba** English sentence export and writes 1,000,000 sentences into BarqDB as
historical messages — about **20 seconds**, **~170 MB** on disk (~50k inserts/sec). The dataset is
downloaded at run time and cached under `build/`; it is **never committed** to the repo.

Start the server and open the room picker — the **🔍 search** box runs a BarqDB `TEXT` full-text
query (backed by the `@FullText` index) over **every** stored message and shows the real result:

> Found **1,179** matches for “computer” in **1&nbsp;ms** · searched **1,000,000** messages

Single-digit-millisecond full-text search across a million rows, and the "stored" counters are now
genuinely in the millions — every number measured, nothing faked. The DB opens in ~25 ms because it
is memory-mapped, not loaded into RAM.

### Data & attribution

Seed data is from **[Tatoeba](https://tatoeba.org)** (community-contributed sentences), released
under **CC BY 2.0 FR**. The seeder downloads it at run time for local indexing and does not
redistribute it; see <https://tatoeba.org/eng/downloads>. Swap the corpus with the `TATOEBA_URL`
env var or a local file: `./gradlew :server:seed --args="1000000 /path/to/file.tsv.bz2"`.

## Deploy with Docker

```bash
docker compose up --build            # build, seed ~1M messages on first boot, then run on :8080
```

**The first boot auto-seeds the archive.** The container entrypoint imports ~1,000,000 messages
into the data volume, drops a marker, and starts the server — so a fresh deploy comes up already
loaded, with full-text search working out of the box. Every later restart finds the marker and
skips straight to the server (seconds, not minutes).

> First boot does a full Gradle build + a download + a 1M-row import, so give it a few minutes.
> Watch it with `docker compose logs -f`.

Then hit `http://<your-server>:8080`. One service, one port, one volume:

- The BarqDB files live in a named volume (`messenger-data`), so the archive and the "users served"
  tally **survive restarts**. `docker compose down -v` wipes them (the next boot re-seeds).
- `SEED_COUNT=250000 docker compose up` seeds a smaller archive; `SEED_COUNT=0` starts empty.
- `HOST_PORT=9000 docker compose up` publishes on a different host port.
- `BARQ_BOTS=on docker compose up --build` also runs the simulated live-load generator.

The image pins **`linux/amd64`** because BarqDB's JVM engine ships a linux-x86_64 native
library — native on x86_64 servers, emulated on Apple Silicon. Plain `docker build .` works too;
the container reads `PORT`, `BARQ_DATA_DIR`, `SEED_COUNT`, and `BARQ_BOTS` from the environment.

## Files worth reading

- [`server/.../ChatStore.kt`](server/src/main/kotlin/io/github/barqdb/chat/server/ChatStore.kt) — opens BarqDB and exposes `roomFeed()` (the `asFlow()` subscription) and `search()` (the timed `TEXT` query).
- [`server/.../Server.kt`](server/src/main/kotlin/io/github/barqdb/chat/server/Server.kt) — the Ktor WebSocket bridge and the optional bot engine (bots write real rows).
- [`server/.../Seeder.kt`](server/src/main/kotlin/io/github/barqdb/chat/server/Seeder.kt) — bulk-imports the ~1M-message Tatoeba archive in batched writes.
- [`client/.../Main.kt`](client/src/jsMain/kotlin/io/github/barqdb/chat/client/Main.kt) — the browser app: connect, render the three screens + archive search, react to pushes.
- [`shared/.../Protocol.kt`](shared/src/commonMain/kotlin/io/github/barqdb/chat/protocol/Protocol.kt) — every message that crosses the wire.
