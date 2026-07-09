#!/bin/sh
# Container entrypoint: seed the archive ONCE, then run the server.
#
# On the very first boot the data volume is empty, so we import the dataset. A marker file in the
# volume records that we've done it, so every later restart skips straight to the server. Set
# SEED_COUNT=0 to skip seeding entirely (start with an empty DB).
set -e

DATA_DIR="${BARQ_DATA_DIR:-/data}"
SEED_COUNT="${SEED_COUNT:-1000000}"
MARKER="$DATA_DIR/.seeded"

if [ "$SEED_COUNT" != "0" ] && [ ! -f "$MARKER" ]; then
  echo "[entrypoint] first boot — seeding BarqDB with $SEED_COUNT messages…"
  java -cp "lib/*" io.github.barqdb.chat.server.SeederKt "$SEED_COUNT"
  touch "$MARKER"
  echo "[entrypoint] seeding complete."
else
  echo "[entrypoint] archive already seeded (or SEED_COUNT=0) — skipping import."
fi

exec ./bin/server
