package com.parrotworks.oneagentarmy.data.local

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Creates the destination a camera capture is written to, exposed as a content:// URI
// the system camera app is allowed to write into (a file:// URI can't be handed to
// another app on modern Android).
//
// The authority is derived from context.packageName - the applicationId at runtime -
// and must stay that way: this app's applicationId (com.piotrek.oneagentarmy) is
// deliberately different from its namespace (com.parrotworks.oneagentarmy), so a
// hardcoded namespace string would compile fine and then fail at runtime with
// "Failed to find configured root".
//
// The file name becomes the attachment's display name, so it is human-readable rather
// than a UUID. The captured photo is re-encoded into AttachmentStore straight away;
// this copy is only a handover buffer.
fun newCameraPhotoUri(context: Context): Uri {
    val directory = File(context.cacheDir, CAMERA_TEMP_DIR)
    directory.mkdirs()
    // At most one capture is ever pending, so old temps are cleared instead of being
    // left to pile up as multi-megabyte files.
    directory.listFiles()?.forEach { it.delete() }

    val file = File(directory, "photo_${captureTimestamp()}.jpg")
    file.createNewFile()
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun captureTimestamp(): String =
    DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
        .withZone(ZoneId.systemDefault())
        .format(Instant.now())

// Must match the path declared in res/xml/file_paths.xml.
private const val CAMERA_TEMP_DIR = "camera"
