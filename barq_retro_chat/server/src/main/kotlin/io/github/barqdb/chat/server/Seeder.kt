package io.github.barqdb.chat.server

import io.github.barqdb.chat.protocol.Nick
import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.types.BarqInstant
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * One-time bulk importer for the huge-data demo.
 *
 * Streams a real, permissively-licensed public dataset — the **Tatoeba** English sentence export
 * (CC BY 2.0, https://tatoeba.org) — and writes ~1,000,000 of the sentences into BarqDB as
 * historical chat messages. After this runs, the app's counters and full-text search operate on
 * genuinely large, real data (nothing generated or faked).
 *
 * The dataset is downloaded at run time and cached under `build/` — it is never committed to the
 * repo, so there is no third-party data to redistribute.
 *
 * Run with the server stopped (single writer):
 *   ./gradlew :server:seed                 # ~1,000,000 rows
 *   ./gradlew :server:seed --args="250000" # a smaller sample
 */

private const val DEFAULT_COUNT = 1_000_000
private const val BATCH = 25_000
private const val DEFAULT_URL =
    "https://downloads.tatoeba.org/exports/per_language/eng/eng_sentences.tsv.bz2"

fun main(args: Array<String>) {
    val target = args.getOrNull(0)?.toIntOrNull() ?: DEFAULT_COUNT
    val dataDir = System.getenv("BARQ_DATA_DIR") ?: System.getProperty("barq.data") ?: "data"
    val url = System.getenv("TATOEBA_URL") ?: DEFAULT_URL

    println("── barqDB Messenger seeder ──")
    println("dataset : Tatoeba English sentences (CC BY 2.0, https://tatoeba.org)")
    println("target  : ${"%,d".format(target)} messages")
    println("data dir: $dataDir/barqdb")

    // Download (once) into a cache under build/.
    val cache = File("build/seed-cache").apply { mkdirs() }
    val bz2 = File(cache, "eng_sentences.tsv.bz2")
    if (args.getOrNull(1) != null) {
        // explicit local file override
        download(File(args[1]).toURI().toURL().toString(), bz2, alreadyLocal = true)
    } else if (!bz2.exists() || bz2.length() < 1_000_000) {
        println("downloading dataset → ${bz2.path} …")
        download(url, bz2)
        println("downloaded ${"%,d".format(bz2.length())} bytes")
    } else {
        println("using cached dataset (${"%,d".format(bz2.length())} bytes)")
    }

    val config = BarqConfiguration.Builder(setOf(MessageEntity::class, Counters::class))
        .directory(File(dataDir, "barqdb").apply { mkdirs() }.absolutePath)
        .name("messenger.barq")
        .deleteBarqIfMigrationNeeded()
        .build()
    val barq = Barq.open(config)

    val startSeq = barq.query<MessageEntity>().max("seq", Long::class).find() ?: 0L
    var inserted = 0
    val t0 = System.nanoTime()

    BufferedReader(InputStreamReader(BZip2CompressorInputStream(bz2.inputStream().buffered()), Charsets.UTF_8))
        .use { reader ->
            val batch = ArrayList<MessageEntity>(BATCH)
            var idx = 0
            var line = reader.readLine()
            while (line != null && inserted < target) {
                // Tatoeba TSV: id \t lang \t text  (text has no tabs)
                val text = line.substringAfterLast('\t').trim()
                if (text.isNotEmpty() && text.length <= 280) {
                    val user = BOT_NAMES[(idx / 6) % BOT_NAMES.size]
                    batch.add(MessageEntity().apply {
                        id = "seed-${startSeq + idx + 1}"
                        room = SEED_ROOMS[idx % SEED_ROOMS.size].id
                        seq = startSeq + idx + 1
                        this.user = user
                        color = Nick.colorFor(user)
                        flag = ""
                        this.text = text
                        createdAt = BarqInstant.now()
                        latencyMs = 2
                    })
                    idx++; inserted++
                    if (batch.size >= BATCH) {
                        flush(barq, batch)
                        batch.clear()
                        report(inserted, target, t0)
                    }
                }
                line = reader.readLine()
            }
            if (batch.isNotEmpty()) { flush(barq, batch); report(inserted, target, t0) }
        }

    val total = barq.query<MessageEntity>().count().find()
    val secs = (System.nanoTime() - t0) / 1e9
    println("── done ──")
    println("inserted ${"%,d".format(inserted)} rows in ${"%.1f".format(secs)}s " +
        "(${"%,d".format((inserted / secs).toLong())} rows/sec)")
    println("BarqDB now holds ${"%,d".format(total)} messages total.")
    barq.close()
}

private fun flush(barq: Barq, batch: List<MessageEntity>) {
    barq.writeBlocking { batch.forEach { copyToBarq(it) } }
}

private fun report(done: Int, target: Int, t0: Long) {
    val secs = (System.nanoTime() - t0) / 1e9
    val rate = if (secs > 0) (done / secs).toLong() else 0
    println("  … ${"%,d".format(done)} / ${"%,d".format(target)}  (${"%,d".format(rate)} rows/sec)")
}

private fun download(url: String, dest: File, alreadyLocal: Boolean = false) {
    val conn = URL(url).openConnection()
    if (conn is HttpURLConnection) {
        conn.connectTimeout = 30_000
        conn.readTimeout = 120_000
        conn.instanceFollowRedirects = true
    }
    conn.getInputStream().use { input -> dest.outputStream().use { out -> input.copyTo(out, 1 shl 16) } }
}
