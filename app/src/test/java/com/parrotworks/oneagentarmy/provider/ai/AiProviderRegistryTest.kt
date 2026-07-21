package com.parrotworks.oneagentarmy.provider.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderRegistryTest {

    // defaultModelFor() relies entirely on this convention - if a model list is ever
    // reordered or a new model inserted out of price order, this catches it immediately
    // instead of silently changing which model new conversations default to.
    @Test
    fun `every provider's models are ordered cheapest-first`() {
        for (provider in AiProviderRegistry.providers) {
            provider.models.zipWithNext { cheaper, pricier ->
                assertTrue(
                    "${provider.id}: ${cheaper.id} (${cheaper.inputUsdPerMTok}) should not cost more than " +
                        "${pricier.id} (${pricier.inputUsdPerMTok})",
                    cheaper.inputUsdPerMTok <= pricier.inputUsdPerMTok,
                )
                assertTrue(
                    "${provider.id}: ${cheaper.id} (${cheaper.outputUsdPerMTok}) should not cost more than " +
                        "${pricier.id} (${pricier.outputUsdPerMTok})",
                    cheaper.outputUsdPerMTok <= pricier.outputUsdPerMTok,
                )
            }
        }
    }

    @Test
    fun `providerIdForModel resolves known models to their provider`() {
        assertEquals(AiProviderRegistry.OPENAI, AiProviderRegistry.providerIdForModel("gpt-4.1-nano"))
        assertEquals(AiProviderRegistry.GEMINI, AiProviderRegistry.providerIdForModel("gemini-3.1-flash-lite"))
        assertEquals(AiProviderRegistry.ANTHROPIC, AiProviderRegistry.providerIdForModel("claude-haiku-4-5"))
    }

    @Test
    fun `providerIdForModel falls back to OpenAI for an unknown model`() {
        assertEquals(AiProviderRegistry.OPENAI, AiProviderRegistry.providerIdForModel("totally-made-up-model"))
    }

    @Test
    fun `defaultModelFor returns each provider's cheapest model`() {
        for (provider in AiProviderRegistry.providers) {
            assertEquals(provider.models.first().id, AiProviderRegistry.defaultModelFor(provider.id))
        }
    }

    @Test
    fun `defaultModelFor falls back to the first provider's cheapest model for an unknown provider`() {
        val expected = AiProviderRegistry.providers.first().models.first().id
        assertEquals(expected, AiProviderRegistry.defaultModelFor("not-a-real-provider"))
    }

    @Test
    fun `estimateCostUsd applies per-model pricing`() {
        // gpt-4.1-nano: $0.10 / $0.40 per 1M tokens.
        val cost = AiProviderRegistry.estimateCostUsd(
            "gpt-4.1-nano",
            TokenUsage(inputTokens = 1_000_000, outputTokens = 1_000_000),
        )
        assertEquals(0.5, cost!!, 0.0001)
    }

    @Test
    fun `estimateCostUsd returns null for an unknown model`() {
        assertNull(AiProviderRegistry.estimateCostUsd("not-a-real-model", TokenUsage(100, 100)))
    }
}
