package io.github.barqdb.demo

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.notifications.InitialResults
import io.github.barqdb.kotlin.notifications.UpdatedResults
import io.github.barqdb.kotlin.query.Sort
import io.github.barqdb.kotlin.types.BarqInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.Instant
import java.util.UUID

private fun id() = UUID.randomUUID().toString()

private fun section(title: String) {
    println("\n" + "─".repeat(64))
    println("▶ $title")
    println("─".repeat(64))
}

private fun BarqInstant.pretty(): String =
    Instant.ofEpochSecond(epochSeconds).toString()

fun main() = runBlocking {
    println("barqdb Kotlin demo — using io.github.barqdb.kotlin:library-base:4.0.6 from Maven Central")

    // ── 1. Open a local, embedded database ────────────────────────────────
    // No server, no connection string. The whole DB is a file on disk.
    section("1. Open a local Barq database")
    val dbDir = File("build/barqdb").apply {
        deleteRecursively() // start fresh each run so the demo is repeatable
        mkdirs()
    }
    val config = BarqConfiguration.Builder(
        schema = setOf(Project::class, Task::class, Person::class)
    )
        .directory(dbDir.absolutePath)
        .name("demo.barq")
        .deleteBarqIfMigrationNeeded()
        .build()

    val barq = Barq.open(config)
    println("Opened database file: ${config.path}")

    // ── 2. Write transaction: create objects + relationships ──────────────
    // Every change happens inside a transaction. writeBlocking is the
    // synchronous form; there is also a suspending `write { }`.
    section("2. Insert data in a transaction (relationships included)")
    barq.writeBlocking {
        // People (managed objects returned by copyToBarq)
        val alice = copyToBarq(Person().apply { id = id(); name = "Alice"; email = "alice@barqdb.dev" })
        val bob = copyToBarq(Person().apply { id = id(); name = "Bob"; email = "bob@barqdb.dev" })
        val carol = copyToBarq(Person().apply { id = id(); name = "Carol"; email = "carol@barqdb.dev" })

        // Project 1 with a to-many list of tasks; tasks link to a Person (to-one)
        val website = Project().apply {
            id = id()
            name = "Website Revamp"
            tasks.add(Task().apply {
                id = id(); title = "Design mockups"; priority = 4; estimateHours = 12.0
                notes = "New landing page and pricing layout"; createdAt = BarqInstant.now(); assignee = alice
            })
            tasks.add(Task().apply {
                id = id(); title = "Deploy staging"; priority = 5; estimateHours = 3.5
                notes = "Urgent: deploy to staging before the review"; createdAt = BarqInstant.now(); assignee = bob
            })
            tasks.add(Task().apply {
                id = id(); title = "Write copy"; priority = 2; estimateHours = 6.0; done = true
                notes = "Marketing copy for the homepage"; createdAt = BarqInstant.now(); assignee = carol
            })
        }
        copyToBarq(website) // cascades: the project AND its 3 tasks are persisted

        val mobile = Project().apply {
            id = id()
            name = "Mobile App"
            tasks.add(Task().apply {
                id = id(); title = "Set up CI"; priority = 3; estimateHours = 8.0
                notes = "Automate build and deploy pipeline"; createdAt = BarqInstant.now(); assignee = bob
            })
            tasks.add(Task().apply {
                id = id(); title = "Fix urgent crash"; priority = 5; estimateHours = 2.0
                notes = "Urgent null pointer crash on launch"; createdAt = BarqInstant.now(); assignee = alice
            })
        }
        copyToBarq(mobile)
    }
    println("Inserted ${barq.query<Project>().count().find()} projects, " +
        "${barq.query<Task>().count().find()} tasks, " +
        "${barq.query<Person>().count().find()} people.")

    // ── 3. Queries (Barq Query Language) ──────────────────────────────────
    section("3. Query: open tasks, highest priority first")
    val openTasks = barq.query<Task>("done == false")
        .sort("priority", Sort.DESCENDING)
        .find()
    openTasks.forEach { println("  P${it.priority}  ${it.title}  (${it.estimateHours}h → ${it.assignee?.name})") }

    section("4. Query with an argument: priority >= 4")
    val hot = barq.query<Task>("priority >= $0", 4).find()
    hot.forEach { println("  ${it.title}  [P${it.priority}]") }

    // ── 5. Aggregations ───────────────────────────────────────────────────
    section("5. Aggregations: count / sum / max")
    val total = barq.query<Task>().count().find()
    val doneCount = barq.query<Task>("done == true").count().find()
    val totalHours = barq.query<Task>().sum("estimateHours", Double::class).find()
    val topPriority = barq.query<Task>().max("priority", Int::class).find()
    println("  tasks: $total   done: $doneCount   remaining hours: $totalHours   max priority: $topPriority")

    // ── 6. Full-text search (@FullText on Task.notes) ─────────────────────
    section("6. Full-text search: notes containing 'urgent'")
    val urgent = barq.query<Task>("notes TEXT $0", "urgent").find()
    urgent.forEach { println("  ${it.title} — \"${it.notes}\"") }

    // ── 7. Relationship traversal ─────────────────────────────────────────
    section("7. Walk relationships: project → tasks → assignee")
    barq.query<Project>().sort("name").find().forEach { p ->
        println("  ${p.name} (${p.tasks.size} tasks)")
        p.tasks.forEach { t -> println("     • ${t.title}  →  ${t.assignee?.name ?: "unassigned"}") }
    }

    // ── 8. Update inside a transaction ────────────────────────────────────
    section("8. Update: mark 'Design mockups' done")
    barq.writeBlocking {
        // Objects fetched inside a write are live & mutable; edits are persisted.
        query<Task>("title == $0", "Design mockups").first().find()?.done = true
    }
    println("  Remaining open tasks: ${barq.query<Task>("done == false").count().find()}")

    // ── 9. Delete inside a transaction ────────────────────────────────────
    section("9. Delete: remove 'Write copy'")
    barq.writeBlocking {
        query<Task>("title == $0", "Write copy").first().find()?.let { delete(it) }
    }
    println("  Total tasks now: ${barq.query<Task>().count().find()}")

    // ── 10. Reactive query: a Flow that updates itself ───────────────────
    // asFlow() emits the current results, then a new emission every time the
    // underlying data changes — this is how you build live UIs.
    section("10. Reactive Flow: watch open-task count change live")
    val observer = launch(Dispatchers.Default) {
        barq.query<Task>("done == false").asFlow().collect { change ->
            when (change) {
                is InitialResults -> println("  [live] start: ${change.list.size} open tasks")
                is UpdatedResults -> println("  [live] change: ${change.list.size} open tasks " +
                    "(+${change.insertions.size} / -${change.deletions.size})")
                else -> {}
            }
        }
    }
    delay(300) // let the initial emission arrive
    println("  ...adding a new task in a background write...")
    barq.write {
        copyToBarq(Task().apply {
            id = id(); title = "Announce launch"; priority = 1; createdAt = BarqInstant.now()
        })
    }
    delay(300) // let the update emission arrive
    observer.cancel()

    // ── Done ──────────────────────────────────────────────────────────────
    section("Done")
    println("Database file left at: ${config.path}")
    barq.close()
    println("Barq closed. ✔")
}
