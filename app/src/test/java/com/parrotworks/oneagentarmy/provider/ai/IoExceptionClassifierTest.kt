package com.parrotworks.oneagentarmy.provider.ai

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import okhttp3.internal.http2.ErrorCode
import okhttp3.internal.http2.StreamResetException
import org.junit.Assert.assertTrue
import org.junit.Test

class IoExceptionClassifierTest {

    @Test
    fun `socket timeout maps to Timeout`() {
        assertTrue(SocketTimeoutException("timeout").toProviderException() is AiProviderException.Timeout)
    }

    @Test
    fun `http2 stream reset with CANCEL maps to Timeout`() {
        // What OkHttp actually throws when a read or call timeout fires on an HTTP/2
        // connection - the case that used to be mislabeled as "no internet".
        assertTrue(StreamResetException(ErrorCode.CANCEL).toProviderException() is AiProviderException.Timeout)
    }

    @Test
    fun `http2 stream reset with a non-CANCEL code maps to NoConnectivity`() {
        assertTrue(
            StreamResetException(ErrorCode.REFUSED_STREAM).toProviderException() is AiProviderException.NoConnectivity,
        )
    }

    @Test
    fun `connection refused maps to NoConnectivity`() {
        assertTrue(ConnectException("refused").toProviderException() is AiProviderException.NoConnectivity)
    }

    @Test
    fun `unknown host maps to NoConnectivity`() {
        assertTrue(UnknownHostException("no dns").toProviderException() is AiProviderException.NoConnectivity)
    }
}
