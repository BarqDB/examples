# Barq Native demo

This demo opens a local Barq database and shows the main local database features:

- schema models
- primary keys
- primitive values
- optional values
- dates, UUID, ObjectId, Decimal128, binary, and mixed values
- embedded objects
- links and backlinks
- lists, sets, and dictionaries
- create, read, update, delete
- type-safe queries
- sorting
- frozen reads
- thread-safe references

Atlas and sync are not shown here because those parts are being removed from this rebrand.

## Build and run

```sh
cmake -S . -B build
cmake --build build --target barq_native_demo -j4
./build/barq_native_demo
```

The demo fetches these projects from GitHub by default:

- `https://github.com/BarqDB/barq-native.git`
- `https://github.com/BarqDB/barq-core.git`

For local development, you can point it at local checkouts:

```sh
cmake -S . -B build \
  -DBARQ_NATIVE_SOURCE_DIR=../../native \
  -DBARQ_CORE_SOURCE_DIR=../../core
```
