# barq-todo

A tiny Node.js to-do app that tests the published [`@barqdb/barq`](https://www.npmjs.com/package/@barqdb/barq) package end to end — a local, offline-first database.

> **Why Node and not a browser React app?** `@barqdb/barq` is a native database (a Realm fork). It runs in **Node.js** and **React Native** via a native addon (`barq.node`) — there is no browser/WASM build. A web React app would install the package but crash trying to load the native engine.

## Run it

```bash
npm install    # pulls @barqdb/barq; prebuild-install fetches barq.node for your platform
npm start      # runs todo.js
```

## What it does

`todo.js` opens a local `todos.barq` database, defines a `Todo` model, then:

1. **CREATE** — inserts 5 todos in a write transaction
2. **READ / QUERY** — `.filtered(...)` and `.sorted(...)` for pending/done + top priority
3. **UPDATE** — marks a task complete, renames and re-prioritises another
4. **DELETE** — removes one
5. **PERSISTENCE** — closes the database, reopens it from disk, and confirms the data is still there

It prints each step and exits `0` when every check passes.

## The API in brief

```js
const Barq = require("@barqdb/barq");

class Todo extends Barq.Object {
  static schema = {
    name: "Todo",
    primaryKey: "_id",
    properties: {
      _id: "objectId",
      title: "string",
      completed: { type: "bool", default: false },
      priority: { type: "int", default: 0 },
      createdAt: "date",
    },
  };
}

const barq = await Barq.open({ schema: [Todo], path: "todos.barq" });
barq.write(() => {
  barq.create("Todo", { _id: new Barq.Types.ObjectId(), title: "Buy milk", createdAt: new Date() });
});
const pending = barq.objects("Todo").filtered("completed == false").sorted("priority", true);
```
