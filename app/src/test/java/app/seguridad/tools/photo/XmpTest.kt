package app.seguridad.tools.photo

import app.seguridad.tools.photo.TestImages.EOI
import app.seguridad.tools.photo.TestImages.PNG_SIGNATURE
import app.seguridad.tools.photo.TestImages.SOI
import app.seguridad.tools.photo.TestImages.ascii
import app.seguridad.tools.photo.TestImages.baseline
import app.seguridad.tools.photo.TestImages.concat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.concurrent.CancellationException

/**
 * HERR-1 (audit 1.3.0): an XMP made of many Dublin Core declarations cost quadratic time (about 13 s for 32 000 on a
 * desktop, hours for 50 MB) and could not be cancelled. Now at most [Xmp.MAX_DC_PREFIXES] distinct prefixes are checked
 * (more counts as author data: fail safe), and every reader calls the caller's checkpoint as it goes.
 */
class XmpTest {
    private val dcNs = "http://purl.org/dc/elements/1.1/"

    /** One declaration, with no creator nor rights under it. */
    private val unit = "xmlns:a=\"$dcNs\" "

    private fun pngXmp(xmp: String) = concat(
        PNG_SIGNATURE, TestImages.ihdr(), TestImages.text("XML:com.adobe.xmp", xmp), *TestImages.idats().toTypedArray(), TestImages.iend(),
    )

    private fun webpXmp(xmp: String) = TestImages.riff(
        TestImages.vp8x(TestImages.XMP_FLAG),
        TestImages.riffChunk("VP8L", TestImages.VP8L_DATA),
        TestImages.riffChunk("XMP ", ascii(xmp)),
    )

    /**
     * M8 of the F6 review: a generous wall-clock bound, for a slow CI runner (the desktop measures tens of ms; before
     * the fix these took tens of seconds). The shape of the cost is checked apart, relatively ([growsLinearly]).
     */
    private val SLOW_CI_MS = 5_000L

    /**
     * M6 of the F6 review: the worst text for a regex-based reader, 45 MB that never match a declaration (a name of the
     * longest length and the most spaces, then no «=»), with one real Dublin Core declaration so that no shortcut
     * skips the search.
     */
    private fun worstXmp(bytes: Int): ByteArray {
        val filler = "xmlns:" + "a".repeat(63) + " ".repeat(16) + "X"
        val sb = StringBuilder(bytes + 128)
        sb.append("xmlns:dc=\"$dcNs\" ")
        while (sb.length < bytes) sb.append(filler)
        return ascii(sb.toString())
    }

    private fun ms(block: () -> Unit): Long {
        val t = System.nanoTime()
        block()
        return (System.nanoTime() - t) / 1_000_000
    }

    @Test fun thirtyTwoThousandDeclarationsTakeLessThanASecond() {
        val png = pngXmp(unit.repeat(32_000))
        val t = ms {
            val r = PhotoCleaner.inspect(png)
            assertTrue(Found.XMP_COMMENTS in r.found)
            assertFalse(Found.AUTHOR_RIGHTS in r.found) // one prefix, and nothing under it
            PhotoCleaner.clean(png)
        }
        assertTrue("inspect and clean took $t ms", t < SLOW_CI_MS)
    }

    @Test fun thirtyTwoThousandDistinctPrefixesTakeLessThanASecondAndCountAsAuthorData() {
        val xmp = (0 until 32_000).joinToString("") { "xmlns:a$it=\"$dcNs\" " }
        for (image in listOf(pngXmp(xmp), webpXmp(xmp))) {
            var r: PhotoReport? = null
            val t = ms { r = PhotoCleaner.inspect(image) }
            assertTrue("inspect took $t ms", t < SLOW_CI_MS)
            // Past the cap nothing is known about the rest: fail safe, the screen says author data goes.
            assertTrue(Found.AUTHOR_RIGHTS in r!!.found)
        }
    }

    @Test fun webpAndManyJpegSegmentsAreLinearToo() {
        val webp = webpXmp(unit.repeat(32_000))
        assertTrue("WebP", ms { PhotoCleaner.inspect(webp) } < SLOW_CI_MS)
        val segment = TestImages.xmp(unit.repeat(1_500)) // about 64 KB, the most one APP1 holds
        val jpeg = concat(SOI, *Array(64) { segment }, baseline.body(), EOI)
        assertTrue("JPEG", ms { PhotoCleaner.inspect(jpeg) } < SLOW_CI_MS)
    }

    @Test fun theWorstFortyFiveMegabytePacketIsReadInLinearTime() {
        val worst = worstXmp(45_000_000)
        var r: Set<Found>? = null
        val t = ms { r = Xmp.flags(worst, 0, worst.size) }
        assertTrue("45 MB took $t ms", t < SLOW_CI_MS)
        assertFalse(Found.AUTHOR_RIGHTS in r!!) // «dc» is bound, nothing is under it
        // The same with the declaration repeated (one prefix, 45 MB).
        val dup = ascii(unit.repeat(45_000_000 / unit.length))
        assertTrue("45 MB of one declaration", ms { Xmp.flags(dup, 0, dup.size) } < SLOW_CI_MS)
    }

    @Test fun growsLinearly() {
        // Four times the text costs about four times the time (the quadratic reader cost sixteen); best of three runs.
        fun best(b: ByteArray) = (1..3).minOf { ms { Xmp.flags(b, 0, b.size) } }
        val small = worstXmp(4_000_000)
        val big = worstXmp(16_000_000)
        best(small) // warm-up
        val ts = best(small)
        val tb = best(big)
        assertTrue("4 MB: $ts ms, 16 MB: $tb ms", tb < 10 * ts + 200)
    }

    @Test fun aLongPacketIsCancelledWithinOneWindow() {
        val worst = worstXmp(45_000_000)
        val c = Canceller(at = 2)
        val t = ms {
            try {
                Xmp.flags(worst, 0, worst.size, c)
                fail("not cancelled")
            } catch (e: CancellationException) {
                assertEquals(2, c.calls)
            }
        }
        assertTrue("cancelled after $t ms", t < SLOW_CI_MS)
        // A checkpoint for every MiB, about: 45 MB is read in tens of windows, each one checked.
        var calls = 0
        Xmp.flags(worst, 0, worst.size) { calls++ }
        assertTrue("$calls checkpoints", calls >= 43)
    }

    @Test fun whatSpansTwoWindowsIsStillFound() {
        // The windows overlap by more than the longest declaration, so nothing is lost at a boundary.
        for (at in listOf(Xmp.WINDOW - 40, Xmp.WINDOW - 5, Xmp.WINDOW, 2 * Xmp.WINDOW - 60)) {
            val head = " ".repeat(at)
            for (tail in listOf(
                "xmlns:zz=\"$dcNs\" zz:creator=\"x\"",
                "<rdf:Description exif:GPSLatitude=\"40\"/>",
            )) {
                val b = ascii(head + tail + " ".repeat(100))
                val f = Xmp.flags(b, 0, b.size)
                assertTrue("$at $tail", Found.AUTHOR_RIGHTS in f || Found.GPS in f)
            }
            // The declaration in one window, its use far away in another.
            val split = ascii("xmlns:far=\"$dcNs\"" + head + " far:rights=\"y\"")
            assertTrue("$at far", Found.AUTHOR_RIGHTS in Xmp.flags(split, 0, split.size))
        }
        // A prefix used as the end of a longer name still counts, as «zz:creator» in «xzz:creator» did before.
        val inside = ascii("xmlns:zz=\"$dcNs\" xzz:creator=\"x\"")
        assertTrue(Found.AUTHOR_RIGHTS in Xmp.flags(inside, 0, inside.size))
        // A declaration that is not one: no «=», a name of 65 characters, 17 spaces.
        for (bad in listOf("xmlns:zz \"$dcNs\" zz:creator", "xmlns:${"z".repeat(65)}=\"$dcNs\" ${"z".repeat(65)}:creator",
            "xmlns:zz${" ".repeat(17)}=\"$dcNs\" zz:creator")) {
            val b = ascii(bad)
            assertFalse(bad, Found.AUTHOR_RIGHTS in Xmp.flags(b, 0, b.size))
        }
    }

    @Test fun creatorUnderAnyPrefixIsStillFound() {
        for (xmp in listOf(
            "xmlns:zz=\"$dcNs\" zz:creator=\"Autora Inventada\"",
            "xmlns:q='$dcNs' <q:rights>texto</q:rights>",
            // Sixteen prefixes without it, then the one that has it: past the cap, still reported.
            (0 until 16).joinToString("") { "xmlns:p$it=\"$dcNs\" " } + "xmlns:last=\"$dcNs\" last:creator=\"x\"",
        )) {
            assertTrue(xmp, Found.AUTHOR_RIGHTS in PhotoCleaner.inspect(pngXmp(xmp)).found)
        }
        // Sixteen distinct prefixes, none with author data: nothing is guessed.
        val sixteen = (0 until 16).joinToString("") { "xmlns:p$it=\"$dcNs\" " }
        assertFalse(Found.AUTHOR_RIGHTS in PhotoCleaner.inspect(pngXmp(sixteen)).found)
    }

    /** A checkpoint that cancels at its [at]-th call, as `ensureActive()` does once the job is cancelled. */
    private class Canceller(private val at: Int) : () -> Unit {
        var calls = 0
        override fun invoke() {
            calls++
            if (calls >= at) throw CancellationException("cancelled")
        }
    }

    private fun assertCancelled(name: String, block: (() -> Unit) -> Unit) {
        val c = Canceller(at = 2)
        try {
            block(c)
            fail("$name: the checkpoint was not honoured (${c.calls} calls)")
        } catch (e: PhotoFailure) {
            fail("$name: a cancellation must not become «dañado»")
        } catch (e: CancellationException) {
            assertEquals("$name stops at the cancelling call", 2, c.calls)
        }
    }

    @Test fun cancellationIsHonouredAndNeverBecomesDamaged() {
        val xmp = unit.repeat(32_000)
        val segment = TestImages.xmp(unit.repeat(1_500))
        val jpeg = concat(SOI, *Array(64) { segment }, baseline.body(), EOI)
        for ((name, image) in listOf("PNG" to pngXmp(xmp), "WebP" to webpXmp(xmp), "JPEG" to jpeg)) {
            assertCancelled("$name inspect") { check -> PhotoCleaner.inspect(image, check) }
            assertCancelled("$name clean") { check -> PhotoCleaner.clean(image, check) }
        }
        // The XMP reader alone checks while it goes through the prefixes.
        val distinct = ascii((0 until 32_000).joinToString("") { "xmlns:a$it=\"$dcNs\" " })
        assertCancelled("Xmp") { check -> Xmp.flags(distinct, 0, distinct.size, check) }
    }

    @Test fun aCancelledCoroutineFreesItsThread() = runBlocking {
        // As «Limpiar foto» calls it: ensureActive() of the coroutine on Dispatchers.Default.
        val segment = TestImages.xmp(unit.repeat(1_500))
        val jpeg = concat(SOI, *Array(400) { segment }, baseline.body(), EOI) // about 26 MB
        val started = CompletableDeferred<Unit>()
        var finished = false
        val job = launch(Dispatchers.Default) {
            val context = coroutineContext
            PhotoCleaner.inspect(jpeg) {
                started.complete(Unit)
                context.ensureActive()
            }
            finished = true
        }
        started.await()
        job.cancel()
        val t = ms { runBlocking { job.join() } }
        assertTrue(job.isCancelled)
        assertFalse("the work went on to the end", finished)
        assertTrue("join took $t ms", t < SLOW_CI_MS)
    }

    @Test fun withoutCancellationTheResultIsTheSame() {
        val image = TestImages.everything()
        var calls = 0
        assertEquals(Jpeg.inspect(image), PhotoCleaner.inspect(image) { calls++ })
        assertTrue("the checkpoint is called", calls > 0)
        assertTrue(PhotoCleaner.clean(image).contentEquals(PhotoCleaner.clean(image) { calls++ }))
    }
}
