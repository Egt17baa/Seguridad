package app.seguridad.tools.redact

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Petición del dueño (1-11-2026): acercar y alejar la imagen de «Tachar» con dos dedos. */
class RedactZoomTest {
    private val w = 1000f
    private val h = 500f

    @Test fun startsUnzoomedAndMapsPointsAsThey() {
        val z = RedactZoom()
        assertEquals(1f, z.scale)
        assertEquals(123f, z.toPreviewX(123f, w))
        assertEquals(45f, z.toPreviewY(45f, h))
    }

    @Test fun pinchKeepsThePointUnderTheFingers() {
        // Twice as big around (250, 100): that point of the image stays under the fingers.
        val z = RedactZoom().transformed(2f, 250f, 100f, 0f, 0f, w, h)
        assertEquals(2f, z.scale)
        assertEquals(250f, z.toPreviewX(250f, w), 1e-3f)
        assertEquals(100f, z.toPreviewY(100f, h), 1e-3f)
        // What is now at the left edge of the view is a quarter of the way into the image: (0 - (-250)) / 2 = 125.
        assertEquals(125f, z.toPreviewX(0f, w), 1e-3f)
    }

    @Test fun aBoxDrawnWhileZoomedLandsWhereItWasDrawn() {
        val z = RedactZoom().transformed(4f, 500f, 250f, 0f, 0f, w, h)
        // A drag across the middle of the zoomed view covers the middle quarter of the image.
        val box = boxFromDrag(z.toPreviewX(0f, w), z.toPreviewY(0f, h), z.toPreviewX(w, w), z.toPreviewY(h, h), w, h, 4000, 2000)
        assertEquals(Box(1500, 750, 2500, 1250), box)
        // And it is painted back exactly over the drag.
        val r = z.toView(boxToView(box!!, w, h, 4000, 2000))
        assertEquals(0f, r.left, 1e-2f)
        assertEquals(0f, r.top, 1e-2f)
        assertEquals(w, r.right, 1e-2f)
        assertEquals(h, r.bottom, 1e-2f)
    }

    @Test fun theImageFollowsTheFingersWhilePinchingAndMoving() {
        // Fingers centred on (250, 100) spread to twice the distance and move 10 px right: the image point that was
        // under them (x 250 of the preview) is now under their new centre (260).
        val z = RedactZoom().transformed(2f, 250f, 100f, 10f, 0f, w, h)
        assertEquals(250f, z.toPreviewX(260f, w), 1e-3f)
        assertEquals(100f, z.toPreviewY(100f, h), 1e-3f)
    }

    @Test fun aDragPastTheEdgeStopsAtWhatIsShown() {
        // Zoomed ×2 on the top-left quarter: a drag from the middle to far outside covers up to the view's corner only.
        val z = RedactZoom().transformed(2f, 0f, 0f, 0f, 0f, w, h)
        val box = boxFromDrag(z.toPreviewX(500f, w), z.toPreviewY(250f, h), z.toPreviewX(9000f, w), z.toPreviewY(9000f, h), w, h, 1000, 500)
        assertEquals(Box(250, 125, 500, 250), box)
        assertEquals(0f, z.toPreviewX(-300f, w), 1e-3f)
    }

    @Test fun scaleStaysBetweenOneAndTheMaximum() {
        assertEquals(1f, RedactZoom().transformed(0.2f, 10f, 10f, 0f, 0f, w, h).scale)
        assertEquals(MAX_REDACT_ZOOM, RedactZoom().transformed(50f, 10f, 10f, 0f, 0f, w, h).scale)
        // Zooming back out to 1 puts the image back in place.
        val back = RedactZoom().transformed(3f, 900f, 400f, 0f, 0f, w, h).transformed(0.1f, 100f, 100f, 0f, 0f, w, h)
        assertEquals(RedactZoom(), back)
    }

    @Test fun theImageAlwaysCoversTheView() {
        val z = RedactZoom().transformed(2f, 500f, 250f, 0f, 0f, w, h)
        // Moving far right or down stops at the image's edge; far left or up, at the other edge.
        val right = z.transformed(1f, 500f, 250f, 5000f, 5000f, w, h)
        assertEquals(0f, right.offsetX)
        assertEquals(0f, right.offsetY)
        val left = z.transformed(1f, 500f, 250f, -5000f, -5000f, w, h)
        assertEquals(-w, left.offsetX)
        assertEquals(-h, left.offsetY)
        // Unzoomed, it cannot be moved at all.
        assertEquals(RedactZoom(), RedactZoom().transformed(1f, 500f, 250f, 300f, -80f, w, h))
    }

    @Test fun badInputLeavesItAsItWas() {
        val z = RedactZoom().transformed(2f, 500f, 250f, 0f, 0f, w, h)
        assertSame(z, z.transformed(Float.NaN, 0f, 0f, 0f, 0f, w, h))
        assertSame(z, z.transformed(2f, Float.POSITIVE_INFINITY, 0f, 0f, 0f, w, h))
        assertSame(z, z.transformed(2f, 0f, 0f, 0f, 0f, 0f, h))
        assertSame(z, z.transformed(0f, 0f, 0f, 0f, 0f, w, h))
    }
}
