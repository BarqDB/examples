#!/usr/bin/env bash
#
# End-to-end multi-tenancy + sync check.
#
# Spins up the real Barq sync server in multi-tenant mode (per-tenant JWT
# verification keys + per-tenant at-rest encryption + resource limits), mints a
# signed JWT for each (tenant, device), runs the native-SDK client scenario, and
# then inspects the server's on-disk files to confirm tenant subtrees are
# separate and encrypted.
#
# Prereqs (build these first — see README.md):
#   core:    BarqSyncServer  -> core/cmake-build-debug/.../barq-server-dbg
#   example: barq_mt_e2e      -> example/barq_multitenant_e2e/build/barq_mt_e2e
#
# Requires: openssl, and (for readiness polling) bash's /dev/tcp.

set -u

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$SCRIPT_DIR/../.." && pwd)"

SERVER_BIN="${SERVER_BIN:-$REPO/core/cmake-build-debug/src/barq/sync/noinst/server/barq-server-dbg}"
CLIENT_BIN="${CLIENT_BIN:-$SCRIPT_DIR/build/barq_mt_e2e}"
PORT="${PORT:-9457}"
HOST="127.0.0.1"

for bin in "$SERVER_BIN" "$CLIENT_BIN"; do
    if [ ! -x "$bin" ]; then
        echo "ERROR: missing binary: $bin" >&2
        echo "Build the server and the example first (see README.md)." >&2
        exit 2
    fi
done

WORK="./.tmp" # "$(mktemp -d "${TMPDIR:-/tmp}/barq-mt-e2e.XXXXXX")"
KEYS="$WORK/pubkeys"        # --jwt-public-key-dir  (public keys, one per tenant)
PRIV="$WORK/priv"           # private keys used to sign tokens
TOKENS="$WORK/tokens"       # minted JWTs
DATA="$WORK/server"         # --root-dir
CLIENTWORK="$WORK/clients"  # local client .barq files
mkdir -p "$KEYS" "$PRIV" "$TOKENS" "$DATA" "$CLIENTWORK"

SERVER_PID=""
cleanup() {
    [ -n "$SERVER_PID" ] && kill "$SERVER_PID" 2>/dev/null
    wait "$SERVER_PID" 2>/dev/null
}
trap cleanup EXIT

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

# make_key <tenant_id>: RSA keypair; public key -> $KEYS/<tenant_id>.pem
make_key() {
    local t="$1"
    openssl genrsa -out "$PRIV/$t.key" 2048 2>/dev/null
    openssl rsa -in "$PRIV/$t.key" -pubout -out "$KEYS/$t.pem" 2>/dev/null
}

# mint <out> <tenant_id> <device_id>: RS256 JWT signed with the tenant's key.
mint() {
    local out="$1" tenant="$2" device="$3"
    local now exp header payload signing sig
    now="$(date +%s)"
    exp="$((now + 86400))"
    header='{"alg":"RS256","typ":"JWT"}'
    payload="{\"sub\":\"$device\",\"app_id\":\"$tenant\",\"access\":[\"download\",\"upload\"],\"iat\":$now,\"exp\":$exp}"
    signing="$(printf '%s' "$header" | b64url).$(printf '%s' "$payload" | b64url)"
    sig="$(printf '%s' "$signing" | openssl dgst -sha256 -sign "$PRIV/$tenant.key" -binary | b64url)"
    printf '%s.%s' "$signing" "$sig" > "$out"
}

echo "workdir: $WORK"
echo "== generating per-tenant keys and tokens =="
make_key "tenant-a"
make_key "tenant-b"
head -c 64 /dev/urandom > "$WORK/master.key"
mint "$TOKENS/a1.jwt" "tenant-a" "device-1"
mint "$TOKENS/a2.jwt" "tenant-a" "device-2"
mint "$TOKENS/b1.jwt" "tenant-b" "device-1"

echo "== launching multi-tenant sync server on $HOST:$PORT =="
"$SERVER_BIN" \
    --root-dir "$DATA" \
    --jwt-public-key-dir "$KEYS" \
    --tenant-encryption-master-key "$WORK/master.key" \
    --tenant-max-connections 100 \
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

# Stop the server so its files are flushed and at rest before we inspect them.
kill "$SERVER_PID" 2>/dev/null
wait "$SERVER_PID" 2>/dev/null
SERVER_PID=""

echo ""
echo "== on-disk checks (server root: $DATA) =="
disk_rc=0
if [ -d "$DATA/tenant-a" ] && [ -d "$DATA/tenant-b" ]; then
    echo "  PASS  tenant-a and tenant-b have separate subtrees on disk"
else
    echo "  FAIL  expected separate tenant-a and tenant-b subtrees under $DATA"
    disk_rc=1
fi
# The plaintext we wrote must NOT appear in any server file (they are encrypted
# with a per-tenant key). Scan regular files only: the .barq.control dir holds
# named pipes (*.cv) that would block a recursive grep.
if find "$DATA" -type f -print0 2>/dev/null | xargs -0 grep -qa "from-tenant-A" 2>/dev/null; then
    echo "  FAIL  plaintext 'from-tenant-A' found on disk — files are NOT encrypted"
    disk_rc=1
else
    echo "  PASS  no plaintext in server files — encrypted at rest per tenant"
fi
# Control: the same string IS present in the client's local (unencrypted) db, so
# the check above is meaningful — the data really exists in plaintext, just not
# in the server's encrypted files.
if find "$CLIENTWORK" -type f -print0 2>/dev/null | xargs -0 grep -qa "from-tenant-A" 2>/dev/null; then
    echo "  PASS  control: plaintext IS present client-side (server-encryption check is real)"
else
    echo "  WARN  control: plaintext not found client-side (encryption check inconclusive)"
fi

echo ""
echo "== server BIND log (tenant routing) =="
grep -E "BIND authenticated|BIND rejected" "$WORK/server.log" | sed 's/^/  /' || true

echo ""
if [ "$CLIENT_RC" -eq 0 ] && [ "$disk_rc" -eq 0 ]; then
    echo "RESULT: PASS"
    exit 0
else
    echo "RESULT: FAIL (client_rc=$CLIENT_RC disk_rc=$disk_rc)"
    echo "server log: $WORK/server.log"
    exit 1
fi
