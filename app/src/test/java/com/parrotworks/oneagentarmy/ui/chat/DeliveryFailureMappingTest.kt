package com.parrotworks.oneagentarmy.ui.chat

import com.parrotworks.oneagentarmy.model.DeliveryFailure
import com.parrotworks.oneagentarmy.provider.ai.AiProviderException
import java.io.IOException
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryFailureMappingTest {

    private val configuredTimeout = 240

    // --- exception -> banner ---

    // Elapsed time only affects the timeout branch; everything else ignores it.
    private val irrelevantElapsed = 0

    @Test
    fun `provider exceptions keep their specific error`() {
        assertEquals(
            ChatError.RateLimited(30, "slow down"),
            AiProviderException.RateLimited(30, "slow down").toChatError(configuredTimeout, irrelevantElapsed),
        )
        assertEquals(
            ChatError.ServerError(503, "upstream"),
            AiProviderException.ServerError(503, "upstream").toChatError(configuredTimeout, irrelevantElapsed),
        )
        assertEquals(
            ChatError.MissingApiKey,
            AiProviderException.MissingApiKey.toChatError(configuredTimeout, irrelevantElapsed),
        )
    }

    @Test
    fun `timeout carries the configured limit rather than the default`() {
        val error = AiProviderException.Timeout("read timed out")
            .toChatError(configuredTimeout, elapsedSeconds = configuredTimeout)

        assertEquals(ChatError.Timeout(configuredTimeout, "read timed out"), error)
    }

    // --- telling a real timeout apart from a dropped connection ---

    @Test
    fun `a timeout-shaped failure well before the limit is reported as a lost connection`() {
        // The reported case: a request with a photo died at ~1 minute against a 4 minute
        // timeout, and the banner blamed the 4 minute setting - which had not been reached
        // and which raising would not have helped.
        val error = AiProviderException.Timeout("SocketTimeoutException: timeout")
            .toChatError(configuredTimeout, elapsedSeconds = 60)

        assertEquals(
            ChatError.ConnectionCut(60, configuredTimeout, "SocketTimeoutException: timeout"),
            error,
        )
    }

    @Test
    fun `a failure just short of the limit still counts as the real timeout`() {
        // Whole-second truncation must not tip a genuine timeout into the other branch.
        val error = AiProviderException.Timeout(null)
            .toChatError(configuredTimeout, elapsedSeconds = configuredTimeout - 1)

        assertEquals(ChatError.Timeout(configuredTimeout, null), error)
    }

    @Test
    fun `overshooting the limit is still the real timeout`() {
        // Several tool round trips each get their own read budget, so total elapsed can
        // exceed the configured value.
        val error = AiProviderException.Timeout(null)
            .toChatError(configuredTimeout, elapsedSeconds = configuredTimeout * 3)

        assertEquals(ChatError.Timeout(configuredTimeout, null), error)
    }

    @Test
    fun `a lost connection is recorded under its own code, not as a timeout`() {
        val cut = ChatError.ConnectionCut(12, configuredTimeout, null)

        assertEquals(DeliveryFailure.CONNECTION_LOST, cut.deliveryFailureCode())
        assertEquals("connection_lost", DeliveryFailure.CONNECTION_LOST)
    }

    @Test
    fun `a non-provider exception maps to Unknown instead of escaping`() {
        // The real case this exists for: a 200 response whose body doesn't match the DTOs.
        // It used to sail past a catch narrowed to AiProviderException and take the app down,
        // leaving behind an unanswered message with no explanation at all.
        val error = SerializationException("Unexpected JSON token").toChatError(configuredTimeout, irrelevantElapsed)

        assertTrue(error is ChatError.Unknown)
        assertTrue((error as ChatError.Unknown).detail.contains("SerializationException"))
        assertTrue(error.detail.contains("Unexpected JSON token"))
    }

    @Test
    fun `an exception with no message still produces a usable detail`() {
        val error = IOException().toChatError(configuredTimeout, irrelevantElapsed)

        assertEquals(ChatError.Unknown("IOException: no message"), error)
    }

    // --- banner -> persisted code ---

    @Test
    fun `every provider failure gets its own persisted code`() {
        assertEquals(
            listOf(
                DeliveryFailure.MISSING_API_KEY,
                DeliveryFailure.INVALID_API_KEY,
                DeliveryFailure.NO_CONNECTIVITY,
                DeliveryFailure.TIMEOUT,
                DeliveryFailure.RATE_LIMITED,
                DeliveryFailure.SERVER_ERROR,
                DeliveryFailure.TOOL_ARGUMENTS,
                DeliveryFailure.UNEXPECTED,
            ),
            listOf(
                ChatError.MissingApiKey,
                ChatError.InvalidApiKey(null),
                ChatError.NoConnectivity(null),
                ChatError.Timeout(configuredTimeout, null),
                ChatError.RateLimited(null, null),
                ChatError.ServerError(500, null),
                ChatError.ToolArguments,
                ChatError.Unknown("boom"),
            ).map { it.deliveryFailureCode() },
        )
    }

    @Test
    fun `persisted codes are the frozen strings written to the database`() {
        // Renaming any of these silently reinterprets every row already carrying the old
        // value as UNEXPECTED, so the literals are pinned here rather than derived.
        assertEquals("timeout", DeliveryFailure.TIMEOUT)
        assertEquals("no_connectivity", DeliveryFailure.NO_CONNECTIVITY)
        assertEquals("rate_limited", DeliveryFailure.RATE_LIMITED)
        assertEquals("server_error", DeliveryFailure.SERVER_ERROR)
        assertEquals("missing_api_key", DeliveryFailure.MISSING_API_KEY)
        assertEquals("invalid_api_key", DeliveryFailure.INVALID_API_KEY)
        assertEquals("tool_arguments", DeliveryFailure.TOOL_ARGUMENTS)
        assertEquals("unexpected", DeliveryFailure.UNEXPECTED)
    }

    @Test
    fun `errors that never reach a message fall back to unexpected`() {
        // These happen before a request is made, or after a reply already arrived.
        assertEquals(DeliveryFailure.UNEXPECTED, ChatError.AttachmentTooLarge.deliveryFailureCode())
        assertEquals(DeliveryFailure.UNEXPECTED, ChatError.PdfTooLarge.deliveryFailureCode())
        assertEquals(DeliveryFailure.UNEXPECTED, ChatError.ImageTooLarge.deliveryFailureCode())
        assertEquals(DeliveryFailure.UNEXPECTED, ChatError.NoAppForAction.deliveryFailureCode())
    }
}
