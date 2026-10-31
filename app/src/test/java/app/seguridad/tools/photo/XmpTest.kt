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
        assertTrue("inspect and clean took $t ms", t < 1_000)
    }

    @Test fun thirtyTwoThousandDistinctPrefixesTakeLessThanASecondAndCountAsAuthorData() {
        val xmp = (0 until 32_000).joinToString("") { "xmlns:a$it=\"$dcNs\" " }
        for (image in listOf(pngXmp(xmp), webpXmp(xmp))) {
            var r: PhotoReport? = null
            val t = ms { r = PhotoCleaner.inspect(image) }
            assertTrue("inspect took $t ms", t < 1_000)
            // Past the cap nothing is known about the rest: fail safe, the screen says author data goes.
            assertTrue(Found.AUTHOR_RIGHTS in r!!.found)
        }
    }

    @Test fun webpAndManyJpegSegmentsAreLinearToo() {
        val webp = webpXmp(unit.repeat(32_000))
        assertTrue("WebP", ms { PhotoCleaner.inspect(webp) } < 1_000)
        val segment = TestImages.xmp(unit.repeat(1_500)) // about 64 KB, the most one APP1 holds
        val jpeg = concat(SOI, *Array(64) { segment }, baseline.body(), EOI)
        assertTrue("JPEG", ms { PhotoCleaner.inspect(jpeg) } < 1_000)
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
        assertTrue("join took $t ms", t < 1_000)
    }

    @Test fun withoutCancellationTheResultIsTheSame() {
        val image = TestImages.everything()
        var calls = 0
        assertEquals(Jpeg.inspect(image), PhotoCleaner.inspect(image) { calls++ })
        assertTrue("the checkpoint is called", calls > 0)
        assertTrue(PhotoCleaner.clean(image).contentEquals(PhotoCleaner.clean(image) { calls++ }))
    }
}
