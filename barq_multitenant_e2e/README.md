# Barq multi-tenancy end-to-end test

A runnable, black-box proof that Barq's multi-tenant sync works and is
**totally isolated** between tenants. It launches the real sync server in
multi-tenant mode, mints a signed JWT per (tenant, device), drives real clients
through the **native SDK**, and inspects the server's files on disk.

> Not affiliated with Realm or MongoDB. Barq is a fork of Realm Core / Realm C++.

## What it proves

The scenario uses **two tenants** and the **same partition name (`shared`)** for
all of them — so any leakage would show up immediately.

| Check | Proves |
|-------|--------|
| Tenant A / device 2 downloads what device 1 wrote | **sync works** within a tenant (N devices, shared dataset) |
| Tenant B on partition `shared` sees **0** objects | **total isolation** — same partition string, different tenant, no data bleed |
| `root-dir/tenant-a` and `root-dir/tenant-b` are separate subtrees | tenant data is namespaced on disk |
| The written string is absent from every server file | **encrypted at rest** with a per-tenant key |
| …but present in the client's local db | the encryption check is real, not a false negative |

The server log shows the routing that makes isolation total — every client asked
for `shared`, but the server derived the path from the **signed token**:

```
BIND authenticated (path='shared', file_path='/tenant-a/shared', tenant='tenant-a', identity='device-1')
BIND authenticated (path='shared', file_path='/tenant-a/shared', tenant='tenant-a', identity='device-2')
BIND authenticated (path='shared', file_path='/tenant-b/shared', tenant='tenant-b', identity='device-1')
```

## How isolation is enforced

- Each tenant has its own RSA key pair. The server loads the **public** keys from
  `--jwt-public-key-dir` (`<tenant_id>.pem`); tokens are verified against the key
  for the `app_id` claim, so a token is only ever accepted for its own tenant.
- The server builds the file path as `/<app_id>/<client partition>` — the tenant
  part comes from the verified token, never from the client. The client can't name
  another tenant's subtree.
- With `--tenant-encryption-master-key`, each tenant's files are encrypted with a
  key derived from `master-secret + tenant_id`, so the files are separately keyed
  at rest.

## Build the prerequisites

From the workspace root (`barq/`):

```sh
# 1. The sync server (needs BARQ_BUILD_SERVER=ON)
cmake -S core -B core/cmake-build-debug -DBARQ_BUILD_SERVER=ON
cmake --build core/cmake-build-debug --target BarqSyncServer

# 2. This example (points at the local core + native checkouts)
cmake -S example/barq_multitenant_e2e -B example/barq_multitenant_e2e/build \
  -DBARQ_NATIVE_SOURCE_DIR=$(pwd)/native -DBARQ_CORE_SOURCE_DIR=$(pwd)/core
cmake --build example/barq_multitenant_e2e/build --target barq_mt_e2e
```

## Run

```sh
./example/barq_multitenant_e2e/run_e2e.sh
```

Expected tail:

```
ALL CHECKS PASSED
...
  PASS  tenant-a and tenant-b have separate subtrees on disk
  PASS  no plaintext in server files — encrypted at rest per tenant
  PASS  control: plaintext IS present client-side (server-encryption check is real)
RESULT: PASS
```

Exit code is `0` on success, `1` if any check fails. Requires `openssl` and a
shell with `/dev/tcp` (bash). All keys, tokens, server data, and client databases
are created under a fresh `mktemp` directory and removed on exit.

### Overrides

Environment variables: `PORT` (default `9457`), `SERVER_BIN`, `CLIENT_BIN`.

## Files

- `src/main.cpp` — the native-SDK client scenario (writes/reads `Widget`, waits on
  the sync session, asserts convergence and isolation).
- `run_e2e.sh` — generates per-tenant keys, mints RS256 JWTs with `openssl`,
  launches the server in multi-tenant mode, runs the client, checks the disk.
- `CMakeLists.txt` — builds `barq_mt_e2e` against `Barq::Native`.
