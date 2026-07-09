package io.github.barqdb.demo

import io.github.barqdb.kotlin.ext.barqListOf
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.annotations.FullText
import io.github.barqdb.kotlin.types.annotations.Index
import io.github.barqdb.kotlin.types.annotations.PrimaryKey

/**
 * A Barq model is just a normal Kotlin class that implements [BarqObject] and has
 * mutable `var` properties. The Barq Gradle/compiler plugin rewrites it at build time
 * into a class that reads and writes straight from the database — no codegen you check in,
 * no DAO layer, no SQL.
 */
class Person : BarqObject {
    @PrimaryKey
    var id: String = ""
    var name: String = ""
    var email: String = ""
}

class Task : BarqObject {
    @PrimaryKey
    var id: String = ""

    var title: String = ""

    /** `@Index` speeds up equality/range queries on this field. */
    @Index
    var priority: Int = 3 // 1 (low) .. 5 (urgent)

    var done: Boolean = false

    var estimateHours: Double = 0.0

    /** `@FullText` enables word-based `TEXT` search over this string. */
    @FullText
    var notes: String = ""

    /** Stored timestamp. Barq has a first-class instant type. */
    var createdAt: BarqInstant = BarqInstant.from(0, 0)

    /** A to-one relationship (a link to another object, or null). */
    var assignee: Person? = null
}

class Project : BarqObject {
    @PrimaryKey
    var id: String = ""
    var name: String = ""

    /** A to-many relationship. `BarqList` is a live, managed list of linked objects. */
    var tasks: BarqList<Task> = barqListOf()
}
