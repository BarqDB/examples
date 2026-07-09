// A tiny to-do app that exercises the published @barqdb/barq package end to end:
// open a local database, then create / read / update / query / delete todos, and
// finally prove the data survives a close + reopen (offline-first persistence).

const Barq = require("@barqdb/barq");
const fs = require("node:fs");
const path = require("node:path");

const DB_PATH = path.join(__dirname, "todos.barq");

// A Todo model. `_id` is the primary key; `completed` defaults to false.
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

function line() {
  console.log("-".repeat(60));
}
function show(barq) {
  for (const t of barq.objects("Todo").sorted("priority", true)) {
    console.log(
      `  [${t.completed ? "x" : " "}] (p${t.priority}) ${t.title}  <${t._id.toHexString().slice(-6)}>`,
    );
  }
}

async function main() {
  // Start from a clean file so the run is reproducible.
  for (const f of [DB_PATH, `${DB_PATH}.lock`, `${DB_PATH}.note`]) {
    fs.rmSync(f, { force: true, recursive: true });
  }
  fs.rmSync(`${DB_PATH}.management`, { force: true, recursive: true });

  console.log(`@barqdb/barq version: ${Barq.version || "(n/a)"}`);
  console.log(`Opening database at: ${DB_PATH}`);
  line();

  let barq = await Barq.open({ schema: [Todo], path: DB_PATH });

  // ---- CREATE ---------------------------------------------------------------
  const seed = [
    { title: "Publish @barqdb/barq to npm", priority: 5, completed: true },
    { title: "Point native deps at static.barqdb.space", priority: 4, completed: true },
    { title: "Write a to-do example", priority: 3 },
    { title: "Buy milk", priority: 1 },
    { title: "Take over the offline-first world", priority: 2 },
  ];
  barq.write(() => {
    for (const s of seed) {
      barq.create("Todo", {
        _id: new Barq.Types.ObjectId(),
        title: s.title,
        priority: s.priority,
        completed: !!s.completed,
        createdAt: new Date(),
      });
    }
  });
  console.log(`CREATE: inserted ${seed.length} todos`);
  show(barq);
  line();

  // ---- READ / QUERY ---------------------------------------------------------
  const all = barq.objects("Todo");
  const pending = all.filtered("completed == false");
  const done = all.filtered("completed == true");
  console.log(`READ: ${all.length} total | ${pending.length} pending | ${done.length} done`);
  const top = all.filtered("completed == false").sorted("priority", true)[0];
  console.log(`Highest-priority pending task: "${top.title}"`);
  line();

  // ---- UPDATE ---------------------------------------------------------------
  barq.write(() => {
    top.completed = true; // finish the top task
    const milk = all.filtered("title == 'Buy milk'")[0];
    milk.priority = 9; // bump the milk
    milk.title = "Buy oat milk";
  });
  console.log(`UPDATE: completed "${top.title}", bumped the milk`);
  show(barq);
  line();

  // ---- DELETE ---------------------------------------------------------------
  barq.write(() => {
    const toDelete = all.filtered("title == 'Take over the offline-first world'")[0];
    barq.delete(toDelete);
  });
  console.log(`DELETE: removed one todo -> ${all.length} remain`);
  line();

  // ---- PERSISTENCE: close, reopen, confirm the data is still there ----------
  const beforeCount = all.length;
  const beforeDone = all.filtered("completed == true").length;
  barq.close();
  console.log("Closed the database. Reopening from disk...");

  barq = await Barq.open({ schema: [Todo], path: DB_PATH });
  const after = barq.objects("Todo");
  console.log(`REOPEN: ${after.length} todos found (expected ${beforeCount})`);
  show(barq);
  line();

  // ---- verdict --------------------------------------------------------------
  const ok =
    after.length === beforeCount &&
    beforeCount === 4 &&
    after.filtered("completed == true").length === beforeDone &&
    after.filtered("title == 'Buy oat milk'").length === 1 &&
    after.filtered("title == 'Take over the offline-first world'").length === 0;

  barq.close();
  fs.rmSync(DB_PATH, { force: true, recursive: true });
  fs.rmSync(`${DB_PATH}.management`, { force: true, recursive: true });
  fs.rmSync(`${DB_PATH}.lock`, { force: true });
  fs.rmSync(`${DB_PATH}.note`, { force: true });

  if (ok) {
    console.log("RESULT: ✅ all checks passed — CRUD, query, and persistence work.");
    process.exit(0);
  } else {
    console.log("RESULT: ❌ a check failed.");
    process.exit(1);
  }
}

main().catch((err) => {
  console.error("ERROR:", err && err.stack ? err.stack : err);
  process.exit(1);
});
