package app.seguridad.tools.redact

import app.seguridad.tools.MAX_DECLARED_PIXELS
import app.seguridad.tools.MAX_DECLARED_SIDE
import app.seguridad.tools.MAX_REDACT_PIXELS
import app.seguridad.tools.photo.PhotoFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/** «Tachar en imagen»: boxes from the finger, undo and redo, the output format and the decode size. */
class RedactionTest {
    @Test fun dragIsScaledAndRoundedOutward() {
        // A 4000×2000 image shown at 1000×500: 4 image pixels per view pixel.
        assertEquals(Box(400, 400, 800, 600), boxFromDrag(100f, 100f, 200f, 150f, 1000f, 500f, 4000, 2000))
        // Scales that are not whole numbers: the box always covers all that was drawn, and less than 1 px more.
        val rnd = Random(20261027)
        repeat(2000) {
            val viewW = 100f + rnd.nextFloat() * 1000f
            val viewH = 100f + rnd.nextFloat() * 1000f
            val imageW = 1 + rnd.nextInt(8000)
            val imageH = 1 + rnd.nextInt(8000)
            val xs = listOf(rnd.nextFloat() * viewW, rnd.nextFloat() * viewW).sorted()
            val ys = listOf(rnd.nextFloat() * viewH, rnd.nextFloat() * viewH).sorted()
            val box = boxFromDrag(xs[0], ys[0], xs[1], ys[1], viewW, viewH, imageW, imageH) ?: return@repeat
            val sx = imageW.toDouble() / viewW
            val sy = imageH.toDouble() / viewH
            val msg = "$box from $xs $ys on $viewW×$viewH, image $imageW×$imageH"
            val e = 1e-6 // the last bit of a double, whichever order the multiplication is done in
            assertTrue(msg, box.left <= xs[0] * sx + e && box.top <= ys[0] * sy + e)
            assertTrue(msg, box.right >= xs[1] * sx - e && box.bottom >= ys[1] * sy - e)
            assertTrue(msg, box.left > xs[0] * sx - 1 - e && box.top > ys[0] * sy - 1 - e)
            assertTrue(msg, box.right < xs[1] * sx + 1 + e && box.bottom < ys[1] * sy + 1 + e)
            assertTrue(msg, box.left >= 0 && box.top >= 0 && box.right <= imageW && box.bottom <= imageH)
            assertTrue(msg, box.right - box.left >= 1 && box.bottom - box.top >= 1)
        }
    }

    @Test fun backwardsDrag() {
        val expected = Box(400, 400, 800, 600)
        assertEquals(expected, boxFromDrag(200f, 150f, 100f, 100f, 1000f, 500f, 4000, 2000))
        assertEquals(expected, boxFromDrag(200f, 100f, 100f, 150f, 1000f, 500f, 4000, 2000))
        assertEquals(expected, boxFromDrag(100f, 150f, 200f, 100f, 1000f, 500f, 4000, 2000))
    }

    @Test fun clippedToImage() {
        // Past every edge: the whole image, never outside it.
        assertEquals(Box(0, 0, 4000, 2000), boxFromDrag(-50f, -50f, 2000f, 900f, 1000f, 500f, 4000, 2000))
        assertEquals(Box(3600, 1600, 4000, 2000), boxFromDrag(900f, 400f, 1100f, 600f, 1000f, 500f, 4000, 2000))
        assertEquals(Box(0, 0, 40, 20), boxFromDrag(10f, 5f, -30f, -100f, 1000f, 500f, 4000, 2000))
        // Entirely outside: nothing to cover.
        assertNull(boxFromDrag(1100f, 600f, 1200f, 700f, 1000f, 500f, 4000, 2000))
        assertNull(boxFromDrag(-20f, -20f, -10f, -10f, 1000f, 500f, 4000, 2000))
        // Along an edge only: zero wide inside the image.
        assertNull(boxFromDrag(1000f, 0f, 1200f, 500f, 1000f, 500f, 4000, 2000))
    }

    @Test fun tinyIsNull() {
        // A tap.
        assertNull(boxFromDrag(100f, 100f, 100f, 100f, 1000f, 500f, 4000, 2000))
        // 100×50 image shown at 1000×500: 10 view pixels per image pixel.
        assertNull(boxFromDrag(100f, 100f, 105f, 200f, 1000f, 500f, 100, 50)) // 0.5 px wide
        assertNull(boxFromDrag(100f, 100f, 200f, 109f, 1000f, 500f, 100, 50)) // 0.9 px high
        assertEquals(Box(10, 10, 11, 20), boxFromDrag(100f, 100f, 110f, 200f, 1000f, 500f, 100, 50)) // exactly 1 px
        // Nonsense sizes or positions never give a box.
        assertNull(boxFromDrag(0f, 0f, 10f, 10f, 0f, 500f, 100, 50))
        assertNull(boxFromDrag(0f, 0f, 10f, 10f, 1000f, Float.NaN, 100, 50))
        assertNull(boxFromDrag(0f, 0f, 10f, 10f, 1000f, 500f, 0, 50))
        assertNull(boxFromDrag(Float.NaN, 0f, 10f, 10f, 1000f, 500f, 100, 50))
        assertNull(boxFromDrag(0f, 0f, Float.POSITIVE_INFINITY, 10f, 1000f, 500f, 100, 50))
    }

    @Test fun undoRedo() {
        val a = Box(0, 0, 1, 1)
        val b = Box(1, 1, 2, 2)
        val c = Box(2, 2, 3, 3)
        val h = RedactHistory()
        assertEquals(emptyList<Box>(), h.boxes)
        assertFalse(h.canUndo)
        assertFalse(h.canRedo)
        assertFalse(h.undo())
        assertFalse(h.redo())
        h.add(a)
        h.add(b)
        assertEquals(listOf(a, b), h.boxes)
        assertTrue(h.undo())
        assertEquals(listOf(a), h.boxes)
        assertTrue(h.canRedo)
        assertTrue(h.redo())
        assertEquals(listOf(a, b), h.boxes)
        assertFalse(h.redo())
        assertTrue(h.undo())
        assertTrue(h.undo())
        assertEquals(emptyList<Box>(), h.boxes)
        assertFalse(h.undo())
        assertTrue(h.redo())
        // A new box after undoing drops what could be redone.
        h.add(c)
        assertEquals(listOf(a, c), h.boxes)
        assertFalse(h.canRedo)
        assertFalse(h.redo())
        // The list handed out is a copy: later changes do not alter it.
        val snapshot = h.boxes
        h.undo()
        assertEquals(listOf(a, c), snapshot)
    }

    @Test fun outputFormatByInput() {
        // Screenshots stay lossless; photos are JPEG 95.
        assertEquals(PhotoFormat.PNG, redactOutputFormat(PhotoFormat.PNG))
        assertEquals(PhotoFormat.PNG, redactOutputFormat(PhotoFormat.WEBP))
        assertEquals(PhotoFormat.JPEG, redactOutputFormat(PhotoFormat.JPEG))
        assertEquals(PhotoFormat.JPEG, redactOutputFormat(PhotoFormat.HEIF))
        // The fixed names ShareTemp accepts, and their types.
        assertEquals("imagen-tachada.png", redactFileName(PhotoFormat.PNG))
        assertEquals("imagen-tachada.jpg", redactFileName(PhotoFormat.JPEG))
        assertEquals("image/png", redactMime(PhotoFormat.PNG))
        assertEquals("image/jpeg", redactMime(PhotoFormat.JPEG))
        for (f in PhotoFormat.entries) {
            val out = redactOutputFormat(f)
            assertTrue(redactFileName(out) in setOf("imagen-tachada.png", "imagen-tachada.jpg"))
        }
    }

    @Test fun previewShowsTheSameBoxAsTheCopy() {
        // The preview draws the box back in view pixels: it covers at least what the finger drew.
        val box = boxFromDrag(10.3f, 20.6f, 33.1f, 47.9f, 300f, 200f, 1000, 777)!!
        val r = boxToView(box, 300f, 200f, 1000, 777)
        assertTrue(r.left <= 10.3f && r.top <= 20.6f && r.right >= 33.1f && r.bottom >= 47.9f)
        assertEquals(ViewRect(100f, 100f, 200f, 150f), boxToView(Box(400, 400, 800, 600), 1000f, 500f, 4000, 2000))
    }

    @Test fun targetSizeKeepsUnderTheLimit() {
        assertNull(redactTargetSize(4000, 3000))
        assertNull(redactTargetSize(6000, 4000)) // exactly 24 MP
        for ((w, h) in listOf(6000 to 5000, 12000 to 9000, 16384 to 12288, 100_000 to 300, 1 to 30_000_000, 30_000_000 to 1)) {
            val (tw, th) = redactTargetSize(w, h)!!
            val px = tw.toLong() * th
            assertTrue("$w×$h → $tw×$th", px <= MAX_REDACT_PIXELS && tw >= 1 && th >= 1)
            if (w > 1000 && h > 1000) {
                assertTrue("$w×$h → $tw×$th", px >= MAX_REDACT_PIXELS * 0.99)
                assertTrue("aspect $w×$h → $tw×$th", abs(tw.toDouble() / th - w.toDouble() / h) < 0.01)
            }
        }
        // About 24 MP, as the annex says.
        assertEquals(24, redactMegapixels(redactTargetSize(6000, 5000)!!.first, redactTargetSize(6000, 5000)!!.second))
    }

    @Test fun sampleSizeForAndroid8() {
        assertEquals(1, redactSampleSize(4000, 3000))
        assertEquals(1, redactSampleSize(6000, 4000))
        assertEquals(2, redactSampleSize(6000, 5000))
        assertEquals(8, redactSampleSize(20000, 20000)) // 5000² is still over 24 MP
        assertEquals(2, redactSampleSize(1, 30_000_000))
        // Rounding up, as libjpeg does: never over the limit.
        for ((w, h) in listOf(6001 to 4001, 9797 to 9797, 65535 to 65535, 1 to Int.MAX_VALUE)) {
            val s = redactSampleSize(w, h)
            val px = ((w + s - 1L) / s) * ((h + s - 1L) / s)
            assertTrue("$w×$h / $s", px <= MAX_REDACT_PIXELS)
            assertEquals("power of two: $s", 0, s and (s - 1))
        }
    }

    @Test fun declaredSizeIsCheckedBeforeDecoding() {
        // Sides: up to 20 000 px.
        assertTrue(declaredSizeAllowed(20_000, 1))
        assertTrue(declaredSizeAllowed(1, 20_000))
        assertTrue(declaredSizeAllowed(20_000, 5_000)) // exactly 100 MP too
        assertFalse(declaredSizeAllowed(20_001, 1))
        assertFalse(declaredSizeAllowed(1, 20_001))
        // Pixels: up to 100 MP.
        assertTrue(declaredSizeAllowed(10_000, 10_000))
        assertFalse(declaredSizeAllowed(10_000, 10_001))
        assertFalse(declaredSizeAllowed(10_001, 10_000))
        assertFalse(declaredSizeAllowed(20_000, 20_000))
        // A real 200 MP sensor is refused; 50 MP is not.
        assertTrue(declaredSizeAllowed(8160, 6120))
        assertFalse(declaredSizeAllowed(16320, 12240))
        // Nonsense never passes, nor does a product that would overflow an Int.
        assertFalse(declaredSizeAllowed(0, 100))
        assertFalse(declaredSizeAllowed(100, 0))
        assertFalse(declaredSizeAllowed(-1, 100))
        assertFalse(declaredSizeAllowed(100, -1))
        assertFalse(declaredSizeAllowed(Int.MIN_VALUE, Int.MIN_VALUE))
        assertFalse(declaredSizeAllowed(Int.MAX_VALUE, Int.MAX_VALUE))
        assertFalse(declaredSizeAllowed(65_536, 65_536))
        assertEquals(100_000_000L, MAX_DECLARED_PIXELS)
        assertEquals(20_000, MAX_DECLARED_SIDE)
    }

    @Test fun megapixelsAreRoundedAndNeverZero() {
        assertEquals(24, redactMegapixels(4898, 4898))
        assertEquals(6, redactMegapixels(3000, 2000))
        assertEquals(1, redactMegapixels(10, 10))
    }
}
