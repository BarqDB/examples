#!/usr/bin/env bash
#
# End-to-end Flexible Sync (FLX) isolation check.
#
# Spins up the real Barq sync server with Flexible Sync enabled and per-table
# rules (Order is owner-scoped, Catalog is public read-only), mints a signed JWT
# for each participant (admin, two users, a back-office reader), and runs the
# native-SDK client scenario in src/main.cpp.
#
# Prereqs (build these first - see README.md):
#   core:    BarqSyncServer  -> core/cmake-build-debug/.../barq-server-dbg
#   example: barq_flx_e2e     -> example/barq_flexible_sync_e2e/build/barq_flx_e2e
#
# Requires: openssl, and (for readiness polling) bash's /dev/tcp.

set -u

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$SCRIPT_DIR/../.." && pwd)"

SERVER_BIN="${SERVER_BIN:-$REPO/core/cmake-build-debug/src/barq/sync/noinst/server/barq-server-dbg}"
CLIENT_BIN="${CLIENT_BIN:-$SCRIPT_DIR/build/barq_flx_e2e}"
PORT="${PORT:-9471}"
HOST="127.0.0.1"

for bin in "$SERVER_BIN" "$CLIENT_BIN"; do
    if [ ! -x "$bin" ]; then
        echo "ERROR: missing binary: $bin" >&2
        echo "Build the server and the example first (see README.md)." >&2
        exit 2
    fi
done

WORK="./.tmp"
KEYS="$WORK/keys"          # RSA keypair; the server verifies tokens with the public half
TOKENS="$WORK/tokens"      # minted JWTs
DATA="$WORK/server"        # --root-dir
CLIENTWORK="$WORK/clients" # local client .barq files
rm -rf "$WORK"
mkdir -p "$KEYS" "$TOKENS" "$DATA" "$CLIENTWORK"

SERVER_PID=""
cleanup() {
    [ -n "$SERVER_PID" ] && kill "$SERVER_PID" 2>/dev/null
    wait "$SERVER_PID" 2>/dev/null
}
trap cleanup EXIT

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

# One signing keypair for the whole demo (single tenant "shop").
openssl genrsa -out "$KEYS/shop.key" 2048 2>/dev/null
openssl rsa -in "$KEYS/shop.key" -pubout -out "$KEYS/shop.pem" 2>/dev/null

# mint <out> <identity> <is_admin>: RS256 JWT for tenant "shop", path "flx".
mint() {
    local out="$1" identity="$2" is_admin="$3"
    local now exp header payload admin_claim signing sig
    now="$(date +%s)"
    exp="$((now + 86400))"
    header='{"alg":"RS256","typ":"JWT"}'
    admin_claim=""
    [ "$is_admin" = "yes" ] && admin_claim='"admin":true,'
    payload="{\"sub\":\"$identity\",\"app_id\":\"shop\",\"path\":\"flx\",${admin_claim}\"access\":[\"download\",\"upload\"],\"iat\":$now,\"exp\":$exp}"
    signing="$(printf '%s' "$header" | b64url).$(printf '%s' "$payload" | b64url)"
    sig="$(printf '%s' "$signing" | openssl dgst -sha256 -sign "$KEYS/shop.key" -binary | b64url)"
    printf '%s.%s' "$signing" "$sig" > "$out"
}

echo "workdir: $WORK"
echo "== minting tokens (tenant 'shop', path 'flx') =="
mint "$TOKENS/admin.jwt" "admin" yes
mint "$TOKENS/user_0.jwt" "user_0" no
mint "$TOKENS/user_1.jwt" "user_1" no
mint "$TOKENS/backoffice.jwt" "backoffice" yes

echo "== launching Flexible Sync server on $HOST:$PORT =="
"$SERVER_BIN" \
    --root-dir "$DATA" \
    --jwt-public-key "$KEYS/shop.pem" \
    --enable-flx \
    --flx-owner-rule "Order:owner_id" \
    --flx-public-readonly-rule "Catalog" \
    --log-level detail \
    --host "$HOST" --port "$PORT" \
    > "$WORK/server.log" 2>&1 &
SERVER_PID=$!

# Wait for the port to accept connections.
ready=0
for _ in $(seq 1 100); do
    if (exec 3<>"/dev/tcp/$HOST/$PORT") 2>/dev/null; then
        exec 3>&- 3<&-
        ready=1
        break
    fi
    if ! kill -0 "$SERVER_PID" 2>/dev/null; then
        echo "ERROR: server exited during startup. Log:" >&2
        cat "$WORK/server.log" >&2
        exit 2
    fi
    sleep 0.2
done
if [ "$ready" -ne 1 ]; then
    echo "ERROR: server did not become ready. Log:" >&2
    cat "$WORK/server.log" >&2
    exit 2
fi
echo "server ready (pid $SERVER_PID)"

echo "== running client scenario =="
"$CLIENT_BIN" "ws://$HOST:$PORT/barq-sync" "$TOKENS" "$CLIENTWORK"
CLIENT_RC=$?

kill "$SERVER_PID" 2>/dev/null
wait "$SERVER_PID" 2>/dev/null
SERVER_PID=""

echo ""
echo "== server compensating-write / rule log =="
grep -E "compensating|owner field|read-only|denied|BIND authenticated|BIND rejected" "$WORK/server.log" | sed 's/^/  /' | head -20 || true

echo ""
if [ "$CLIENT_RC" -eq 0 ]; then
    echo "RESULT: PASS"
    exit 0
else
    echo "RESULT: FAIL (client_rc=$CLIENT_RC)"
    echo "server log: $WORK/server.log"
    exit 1
fi
