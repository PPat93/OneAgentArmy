package com.parrotworks.oneagentarmy.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// EXIF orientation values are given as their raw numbers rather than the ExifInterface
// constants, so the table below is checked against the spec itself and not against the
// same named constants the production code reads.
class ImageOrientationTest {

    @Test
    fun `an upright image is left alone`() {
        assertTrue(imageTransformFor(1).isIdentity)
    }

    @Test
    fun `a portrait phone photo is rotated a quarter turn`() {
        // The case that was silently breaking every camera capture: orientation 6 means
        // "the sensor read this sideways, turn it 90 clockwise to display".
        assertEquals(ImageTransform(90, mirrored = false), imageTransformFor(6))
    }

    @Test
    fun `the other quarter turn goes the other way`() {
        assertEquals(ImageTransform(270, mirrored = false), imageTransformFor(8))
    }

    @Test
    fun `a half turn needs no mirroring`() {
        assertEquals(ImageTransform(180, mirrored = false), imageTransformFor(3))
    }

    @Test
    fun `a horizontal flip is mirroring with no rotation`() {
        assertEquals(ImageTransform(0, mirrored = true), imageTransformFor(2))
    }

    @Test
    fun `a vertical flip is a half turn plus mirroring`() {
        assertEquals(ImageTransform(180, mirrored = true), imageTransformFor(4))
    }

    @Test
    fun `the two diagonal cases are not swapped for each other`() {
        // Transpose and transverse differ only in the rotation, and getting the
        // rotate-then-mirror order backwards silently exchanges them.
        assertEquals(ImageTransform(90, mirrored = true), imageTransformFor(5))
        assertEquals(ImageTransform(270, mirrored = true), imageTransformFor(7))
    }

    @Test
    fun `an undefined or unknown tag is treated as upright`() {
        // Rotating an image that was already correct is worse than ignoring a tag we
        // do not recognise, so anything unexpected must fall through to identity.
        assertTrue(imageTransformFor(0).isIdentity)
        assertTrue(imageTransformFor(9).isIdentity)
        assertTrue(imageTransformFor(-1).isIdentity)
        assertTrue(imageTransformFor(Int.MAX_VALUE).isIdentity)
    }

    @Test
    fun `every transform that changes pixels reports itself as non-identity`() {
        (2..8).forEach { orientation ->
            assertFalse(
                "orientation $orientation must not be treated as a no-op",
                imageTransformFor(orientation).isIdentity,
            )
        }
    }
}
