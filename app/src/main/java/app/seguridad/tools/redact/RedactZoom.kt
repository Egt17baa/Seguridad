package app.seguridad.tools.redact

/** How far «Tachar» lets the preview be enlarged. The preview bitmap has at most 2048 px per side. */
const val MAX_REDACT_ZOOM = 5f

/**
 * The zoom of «Tachar»'s preview (owner's request, 1-11-2026: enlarge and shrink it with two fingers). A point at
 * (x, y) of the unzoomed preview is shown at (x · [scale] + [offsetX], y · [scale] + [offsetY]). Only the view changes:
 * boxes are still stored in image pixels, from the same [boxFromDrag] once a touch is brought back to the unzoomed
 * preview ([toPreviewX], [toPreviewY]). Nothing secret: it is kept like any other view state and lost on rotation.
 */
data class RedactZoom(val scale: Float = 1f, val offsetX: Float = 0f, val offsetY: Float = 0f) {
    fun toPreviewX(x: Float): Float = (x - offsetX) / scale
    fun toPreviewY(y: Float): Float = (y - offsetY) / scale

    /** [r] (unzoomed preview pixels, from [boxToView]) where it is shown now. */
    fun toView(r: ViewRect) = ViewRect(r.left * scale + offsetX, r.top * scale + offsetY, r.right * scale + offsetX, r.bottom * scale + offsetY)

    /**
     * After a two-finger move on a [viewW]×[viewH] preview: enlarged by [zoom] around the fingers' centre ([cx], [cy]),
     * which keeps the point of the image under them, then moved by ([panX], [panY]). The scale stays between 1 and
     * [MAX_REDACT_ZOOM], and the image always covers the whole preview (no empty margin). Returns itself for input that
     * is not a finite number, a zoom that is not positive, or an empty view.
     */
    fun transformed(zoom: Float, cx: Float, cy: Float, panX: Float, panY: Float, viewW: Float, viewH: Float): RedactZoom {
        val finite = listOf(zoom, cx, cy, panX, panY, viewW, viewH).all { it.isFinite() }
        if (!finite || zoom <= 0f || viewW <= 0f || viewH <= 0f) return this
        val s = (scale * zoom).coerceIn(1f, MAX_REDACT_ZOOM)
        val f = s / scale
        val ox = (cx - (cx - offsetX) * f + panX).coerceIn(viewW * (1f - s), 0f)
        val oy = (cy - (cy - offsetY) * f + panY).coerceIn(viewH * (1f - s), 0f)
        return if (s == 1f) RedactZoom() else RedactZoom(s, ox, oy)
    }
}
