package com.parrotworks.oneagentarmy.data.local

import androidx.exifinterface.media.ExifInterface

// What has to be done to an image's pixels to make it look the way it was actually shot.
//
// Phone cameras almost never rotate the sensor image: they store it as the sensor read it
// and record "this needs turning" in an EXIF tag. BitmapFactory ignores that tag and
// Bitmap.compress does not write it back out, so a decode/re-encode round trip silently
// converts "portrait photo with a rotate flag" into "landscape photo with no flag".
//
// Order matters: rotate clockwise by rotationDegrees first, then mirror horizontally.
// Applying them the other way round swaps the two diagonal cases (5 and 7) for each other.
data class ImageTransform(val rotationDegrees: Int, val mirrored: Boolean) {
    val isIdentity: Boolean get() = rotationDegrees == 0 && !mirrored
}

fun imageTransformFor(exifOrientation: Int): ImageTransform = when (exifOrientation) {
    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> ImageTransform(0, mirrored = true)
    ExifInterface.ORIENTATION_ROTATE_180 -> ImageTransform(180, mirrored = false)
    // Rotating 180 and then mirroring horizontally is a vertical flip.
    ExifInterface.ORIENTATION_FLIP_VERTICAL -> ImageTransform(180, mirrored = true)
    ExifInterface.ORIENTATION_TRANSPOSE -> ImageTransform(90, mirrored = true)
    ExifInterface.ORIENTATION_ROTATE_90 -> ImageTransform(90, mirrored = false)
    ExifInterface.ORIENTATION_TRANSVERSE -> ImageTransform(270, mirrored = true)
    ExifInterface.ORIENTATION_ROTATE_270 -> ImageTransform(270, mirrored = false)
    // ORIENTATION_NORMAL, ORIENTATION_UNDEFINED, and any value a camera invents: leave the
    // pixels alone. Guessing at an unknown tag risks rotating an image that was already
    // upright, which is worse than doing nothing.
    else -> ImageTransform(0, mirrored = false)
}
