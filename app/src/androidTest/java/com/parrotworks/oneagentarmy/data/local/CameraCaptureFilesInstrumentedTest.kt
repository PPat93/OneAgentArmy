package com.parrotworks.oneagentarmy.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// The FileProvider wiring is the one part of camera capture that compiles cleanly and
// fails at runtime: this app's applicationId differs from its namespace, so a mismatch
// between the manifest authority and the code would only appear as "Failed to find
// configured root" the first time someone taps "Take a photo". These run against the
// real manifest and real FileProvider, so that mismatch fails here instead.
@RunWith(AndroidJUnit4::class)
class CameraCaptureFilesInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun exposesTheCaptureAsAContentUriTheCameraAppCanWriteTo() {
        val uri = newCameraPhotoUri(context)

        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.fileprovider", uri.authority)

        // Opening it for writing proves file_paths.xml actually covers the directory -
        // this is what throws if the declared paths and the real path disagree.
        val stream = context.contentResolver.openOutputStream(uri)
        assertNotNull("FileProvider must expose the capture file for writing", stream)
        stream?.close()
    }

    @Test
    fun clearsAnEarlierCaptureInsteadOfAccumulatingFiles() {
        val cameraDir = File(context.cacheDir, "camera")
        newCameraPhotoUri(context)
        File(cameraDir, "stale.jpg").writeBytes(ByteArray(16))

        newCameraPhotoUri(context)

        val names = cameraDir.listFiles()?.map { it.name }.orEmpty()
        assertEquals("only the pending capture should remain: $names", 1, names.size)
        assertTrue(names.single().startsWith("photo_"))
    }
}
