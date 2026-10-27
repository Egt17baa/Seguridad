package app.seguridad.tools.qr

/**
 * Longest side a picked image is decoded at to look for a QR: plenty for a code that fills a fair part of the picture,
 * and at most 16 MB of pixels (4 bytes each), whatever the photo's size.
 */
const val MAX_QR_DECODE_SIDE = 2048

/**
 * The size to decode a [w]×[h] image at (ImageDecoder.setTargetSize), with the same shape and both sides at most
 * [MAX_QR_DECODE_SIDE]; null when it already fits. The longer side becomes the limit, the shorter one is rounded down
 * and never 0.
 */
fun qrTargetSize(w: Int, h: Int): Pair<Int, Int>? {
    require(w > 0 && h > 0)
    if (w <= MAX_QR_DECODE_SIDE && h <= MAX_QR_DECODE_SIDE) return null
    return if (w >= h) {
        MAX_QR_DECODE_SIDE to (h.toLong() * MAX_QR_DECODE_SIDE / w).toInt().coerceIn(1, MAX_QR_DECODE_SIDE)
    } else {
        (w.toLong() * MAX_QR_DECODE_SIDE / h).toInt().coerceIn(1, MAX_QR_DECODE_SIDE) to MAX_QR_DECODE_SIDE
    }
}

/**
 * Android 8 has no ImageDecoder: BitmapFactory's inSampleSize, the smallest power of two that brings both sides of a
 * [w]×[h] image to at most [MAX_QR_DECODE_SIDE]. Each side is rounded up (libjpeg does), so it is never over.
 */
fun qrSampleSize(w: Int, h: Int): Int {
    require(w > 0 && h > 0)
    var s = 1
    while ((w + s - 1) / s > MAX_QR_DECODE_SIDE || (h + s - 1) / s > MAX_QR_DECODE_SIDE) s *= 2
    return s
}
