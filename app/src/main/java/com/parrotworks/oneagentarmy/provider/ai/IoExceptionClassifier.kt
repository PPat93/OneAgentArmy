package com.parrotworks.oneagentarmy.provider.ai

import java.io.IOException
import java.io.InterruptedIOException
import okhttp3.internal.http2.ErrorCode
import okhttp3.internal.http2.StreamResetException

// Distinguishes "the request took too long" from "there is no network". On HTTP/1.1 a
// fired read/call timeout surfaces as SocketTimeoutException (an InterruptedIOException),
// but on HTTP/2 - which all three providers speak - OkHttp cancels the stream instead,
// surfacing as StreamResetException(CANCEL). Both used to get mislabeled as
// "No internet connection", which is confusing when a slow flagship model simply
// exceeded the read timeout on a perfectly healthy connection.
fun IOException.toProviderException(): AiProviderException {
    val detail = "${javaClass.simpleName}: $message"
    val isTimeout = this is InterruptedIOException ||
        (this is StreamResetException && errorCode == ErrorCode.CANCEL)
    return if (isTimeout) {
        AiProviderException.Timeout(detail)
    } else {
        AiProviderException.NoConnectivity(detail)
    }
}
