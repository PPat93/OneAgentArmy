package com.parrotworks.oneagentarmy.provider.ai

// Narrow seam through which providers load attachment bytes for history replay -
// keeps the provider layer free of Android storage details. Returns null when
// the file no longer exists (e.g. cleaned up externally).
fun interface AttachmentReader {
    suspend fun readBase64(path: String): String?
}
