package com.parrotworks.oneagentarmy.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AttachmentTooLargeException : IOException("Attachment exceeds the size limit")

// App-private storage for media attachments (images re-encoded, PDFs copied).
// Paths handed out are file names relative to the attachments directory, so
// database rows stay valid if the app's data directory moves.
class AttachmentStore(context: Context) {

    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, "attachments")

    data class Saved(val path: String, val mime: String, val name: String)

    // Downscales to MAX_IMAGE_EDGE_PX on the longer edge and re-encodes as JPEG -
    // caps the per-turn token cost of replaying the image with the history.
    suspend fun saveImage(uri: Uri): Saved = withContext(Dispatchers.IO) {
        val originalName = displayName(uri) ?: "image"
        val bytes = readAllBytes(uri)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not a decodable image")

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_IMAGE_EDGE_PX) {
            sampleSize *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val sampled = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            ?: throw IOException("Not a decodable image")

        val longEdge = maxOf(sampled.width, sampled.height)
        val bitmap = if (longEdge > MAX_IMAGE_EDGE_PX) {
            val scale = MAX_IMAGE_EDGE_PX.toFloat() / longEdge
            Bitmap.createScaledBitmap(
                sampled,
                (sampled.width * scale).toInt().coerceAtLeast(1),
                (sampled.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            sampled
        }

        val fileName = "${UUID.randomUUID()}.jpg"
        directory.mkdirs()
        File(directory, fileName).outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        Saved(path = fileName, mime = "image/jpeg", name = originalName)
    }

    suspend fun savePdf(uri: Uri): Saved = withContext(Dispatchers.IO) {
        val originalName = displayName(uri) ?: "document.pdf"
        val bytes = readAllBytes(uri)
        if (bytes.size > MAX_PDF_BYTES) throw AttachmentTooLargeException()

        val fileName = "${UUID.randomUUID()}.pdf"
        directory.mkdirs()
        File(directory, fileName).writeBytes(bytes)
        Saved(path = fileName, mime = "application/pdf", name = originalName)
    }

    suspend fun readBase64(path: String): String? = withContext(Dispatchers.IO) {
        val file = File(directory, path)
        if (!file.isFile) return@withContext null
        Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
    }

    fun absolutePath(path: String): String = File(directory, path).absolutePath

    suspend fun deleteAll(paths: List<String>) = withContext(Dispatchers.IO) {
        paths.forEach { File(directory, it).delete() }
    }

    private fun readAllBytes(uri: Uri): ByteArray =
        appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Cannot open attachment")

    private fun displayName(uri: Uri): String? =
        appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }

    private companion object {
        const val MAX_IMAGE_EDGE_PX = 1568
        const val JPEG_QUALITY = 80
        const val MAX_PDF_BYTES = 5 * 1024 * 1024
    }
}
