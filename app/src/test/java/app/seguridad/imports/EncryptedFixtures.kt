package app.seguridad.imports

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/*
 * What the tests of the encrypted readers share (Task 10). The encrypted fixtures were written once by
 * scripts/import-fixtures/gen_encrypted.py, an independent Python implementation written from the public format
 * documents (see its header), each next to its plain twin; the tests only read those bytes.
 */

/** The main password of every encrypted fixture, as the plan's manual checks use it. */
internal const val FIXTURE_PASSWORD = "prueba-clavero-1.4"

internal fun resource(path: String): ByteArray =
    requireNotNull(EncryptedFixtureMarker::class.java.getResourceAsStream("/import/$path")) { path }.use { it.readBytes() }

private object EncryptedFixtureMarker

internal fun okRead(bytes: ByteArray, password: String? = null): ImportRead {
    val r = readImport(bytes, password?.toCharArray())
    assertTrue("$r", r is ReadResult.Ok)
    return (r as ReadResult.Ok).read
}

internal fun failedRead(bytes: ByteArray, password: String? = FIXTURE_PASSWORD): String {
    val r = readImport(bytes, password?.toCharArray())
    assertTrue("$r", r is ReadResult.Failed)
    return (r as ReadResult.Failed).message
}

internal fun jsonRoot(bytes: ByteArray): JsonObject = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject

/** [root] with [key] set to [value] (a string, a number or any element). */
internal fun JsonObject.with(key: String, value: Any?): JsonObject {
    val v: JsonElement = when (value) {
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        null -> kotlinx.serialization.json.JsonNull
        else -> error("type")
    }
    return JsonObject(this + (key to v))
}

internal fun JsonObject.bytes(): ByteArray = toString().toByteArray()

/** Where [literal] goes in [bytesWithLiteral]: a string value no fixture has. */
internal const val LITERAL_MARK = "@@literal@@"

/** This object's bytes with the value [LITERAL_MARK] written as [literal], as it is (a number such as `6e5`). */
internal fun JsonObject.bytesWithLiteral(literal: String): ByteArray =
    toString().also { check("\"$LITERAL_MARK\"" in it) }.replace("\"$LITERAL_MARK\"", literal).toByteArray()

/** How long [block] takes, in milliseconds. */
internal inline fun millis(block: () -> Unit): Long {
    val t = System.nanoTime()
    block()
    return (System.nanoTime() - t) / 1_000_000
}

/** [bytes] cut at five points across the file: none of them reads, and none throws. */
internal fun assertTruncationsFail(bytes: ByteArray) {
    for (k in 1..5) {
        val cut = bytes.copyOf(bytes.size * k / 6)
        val r = readImport(cut, FIXTURE_PASSWORD.toCharArray())
        assertTrue("cut at $k/6: $r", r is ReadResult.Failed)
    }
}

/** Every array a reader kept in its [Secrets] is all zeros once it returned, and there were [exactly] of them. */
internal fun assertAllZero(seen: List<ByteArray>, exactly: Int) {
    assertEquals("secrets kept", exactly, seen.size)
    for (a in seen) assertArrayEquals(ByteArray(a.size), a)
}

/** Each of [arrays] is itself (not a copy) among what a [Secrets] saw, so its wipe reaches it. */
internal fun assertKept(seen: List<ByteArray>, vararg arrays: ByteArray) {
    for ((i, a) in arrays.withIndex()) assertTrue("array $i not kept", seen.any { it === a })
}

/** The same items, skipped elements and notes count: what an encrypted file gives must be what its plain twin gives. */
internal fun assertSameRead(plain: ImportRead, opened: ImportRead) {
    assertEquals(plain.format, opened.format)
    assertEquals(plain.items, opened.items)
    assertEquals(plain.skipped, opened.skipped)
    assertEquals(plain.becameNotes, opened.becameNotes)
    assertEquals(plain, opened)
}
