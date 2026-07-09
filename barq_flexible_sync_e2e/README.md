# Barq Flexible Sync (FLX) end-to-end example

A runnable, native-SDK demonstration of the classic multi-user model on a
**single shared server file**:

- **Shared catalog** — a `Catalog` (products) table every user can read, but only
  an admin can write. Configured as a **public-read-only** rule.
- **Private orders** — an `Order` table where each row is visible and writable
  only to the user whose identity equals its `owner_id`. Configured as an
  **owner** rule (`Order:owner_id`).
- **Back-office aggregation** — an admin token sees *every* user's orders.

Unlike Partition Sync (one file per partition), Flexible Sync keeps one file per
tenant and lets each client subscribe to queries; the server intersects every
subscription with its per-table rule, so "subscribe to all Orders" really means
"all Orders I own". This is the piece the PBS demo in `../barq_multitenant_e2e`
can't express.

## What it proves

`src/main.cpp` drives the real `barq-server` through the native SDK and checks:

1. Admin seeds two products into the shared catalog.
2. `user_0` and `user_1` each create their own order.
3. Each user sees **only** their own order plus both shared products — even
   though all three clients share the same server file (per-user isolation).
4. A back-office admin token sees **both** users' orders (aggregation).

## Build

Build the server (in the core repo) and this example. Point the example at your
local checkouts so it doesn't fetch from GitHub:

```bash
# 1. Server (core repo) — produces barq-server-dbg
cmake --build ../../core/cmake-build-debug --target BarqSyncServer -j

# 2. This example
cmake -S . -B build \
  -DBARQ_NATIVE_SOURCE_DIR=../../native \
  -DBARQ_CORE_SOURCE_DIR=../../core
cmake --build build --target barq_flx_e2e -j
```

## Run

```bash
./run_flx_e2e.sh
```

The script mints a signed JWT for each participant (`admin`, `user_0`, `user_1`,
`backoffice`), launches the server with:

```
--enable-flx --flx-owner-rule Order:owner_id --flx-public-readonly-rule Catalog
```

runs the client scenario, and prints `RESULT: PASS` on success. All generated
keys, tokens, and databases land under `./.tmp` (git-ignored).

## The rules, in one place

| Table   | Server rule                        | Who can read           | Who can write            |
|---------|------------------------------------|------------------------|--------------------------|
| Catalog | `--flx-public-readonly-rule Catalog` | everyone             | admin only               |
| Order   | `--flx-owner-rule Order:owner_id`    | the row's `owner_id` | the row's `owner_id`, or admin |

A write that violates a rule (e.g. `user_0` trying to create an order owned by
`user_1`, or a user editing the read-only catalog) is **undone by the server**
with a compensating write, and the offending client is told which object was
rejected. This example sticks to the allowed paths; see the core test suite
(`Sync_FLX*` in `core/test/test_sync.cpp`) for the adversarial cases.

## Known limitations (by design, for now)

- **Ownership is a single string field equal to the token identity.** There is
  no notion of teams, roles, groups, or "shared with" — a row belongs to exactly
  one identity. This covers the "my data vs. everyone's data" model but is a
  simpler permission system than Realm's document-level roles.
- The per-client server-side view state is reclaimed lazily (on the first FLX
  bind after a restart); a very long-lived server accumulates a bounded amount of
  metadata between restarts.
