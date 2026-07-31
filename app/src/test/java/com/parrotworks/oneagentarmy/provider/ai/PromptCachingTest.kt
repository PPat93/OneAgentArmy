package com.parrotworks.oneagentarmy.provider.ai

import com.parrotworks.oneagentarmy.provider.ai.anthropic.dto.AnthropicUsage
import com.parrotworks.oneagentarmy.provider.ai.anthropic.dto.toTokenUsage as anthropicToTokenUsage
import com.parrotworks.oneagentarmy.provider.ai.gemini.dto.InteractionsUsage
import com.parrotworks.oneagentarmy.provider.ai.gemini.dto.toTokenUsage as geminiToTokenUsage
import com.parrotworks.oneagentarmy.provider.ai.openai.dto.ResponsesInputTokensDetails
import com.parrotworks.oneagentarmy.provider.ai.openai.dto.ResponsesUsage
import com.parrotworks.oneagentarmy.provider.ai.openai.dto.toTokenUsage as openAiToTokenUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptCachingTest {

    // --- per-provider usage normalization ---

    @Test
    fun `anthropic usage is treated as disjoint buckets`() {
        val usage = AnthropicUsage(
            inputTokens = 50,
            outputTokens = 503,
            cacheReadInputTokens = 100_000,
            cacheCreationInputTokens = 248,
        ).anthropicToTokenUsage()

        assertEquals(50L, usage.inputTokens)
        assertEquals(100_000L, usage.cachedInputTokens)
        assertEquals(248L, usage.cacheWriteInputTokens)
        assertEquals(100_298L, usage.totalInputTokens)
    }

    @Test
    fun `openai cached tokens are subtracted out of the input total`() {
        val usage = ResponsesUsage(
            inputTokens = 5000,
            outputTokens = 300,
            inputTokensDetails = ResponsesInputTokensDetails(cachedTokens = 4096),
        ).openAiToTokenUsage()

        assertEquals(904L, usage.inputTokens)
        assertEquals(4096L, usage.cachedInputTokens)
        // Subtracting must not change how many prompt tokens were actually processed.
        assertEquals(5000L, usage.totalInputTokens)
    }

    @Test
    fun `openai usage without the details object reports no caching`() {
        // Older responses (and any future version that drops the field) must behave
        // exactly as they did before caching was accounted for.
        val usage = ResponsesUsage(inputTokens = 900, outputTokens = 100).openAiToTokenUsage()

        assertEquals(TokenUsage(inputTokens = 900, outputTokens = 100), usage)
    }

    @Test
    fun `gemini cached tokens are subtracted and thought tokens still count as output`() {
        val usage = InteractionsUsage(
            totalInputTokens = 8000,
            totalOutputTokens = 200,
            totalThoughtTokens = 50,
            totalCachedTokens = 6000,
        ).geminiToTokenUsage()

        assertEquals(2000L, usage.inputTokens)
        assertEquals(6000L, usage.cachedInputTokens)
        // Thought tokens are billed at the output rate - that must survive the change.
        assertEquals(250L, usage.outputTokens)
    }

    @Test
    fun `null usage from any provider stays zero`() {
        assertEquals(TokenUsage.ZERO, (null as AnthropicUsage?).anthropicToTokenUsage())
        assertEquals(TokenUsage.ZERO, (null as ResponsesUsage?).openAiToTokenUsage())
        assertEquals(TokenUsage.ZERO, (null as InteractionsUsage?).geminiToTokenUsage())
    }

    // --- cost estimation ---

    @Test
    fun `each input bucket is priced at its own rate`() {
        // claude-opus-5: $5.00 input, $25.00 output, $0.50 cache read, $6.25 cache write.
        val usage = TokenUsage(
            inputTokens = 1000,
            outputTokens = 500,
            cachedInputTokens = 10_000,
            cacheWriteInputTokens = 2000,
        )

        val cost = AiProviderRegistry.estimateCostUsd("claude-opus-5", usage)

        // 5000 + 5000 + 12500 + 12500 millionths of a dollar.
        assertEquals(0.035, cost!!, 1e-9)
    }

    @Test
    fun `a model without a cache write rate prices written tokens at the full input rate`() {
        // Gemini's implicit caching has no write premium, so cacheWriteUsdPerMTok is
        // unset - those tokens must fall back to the ordinary input price, not to zero.
        val usage = TokenUsage(inputTokens = 0, outputTokens = 0, cacheWriteInputTokens = 1000)

        val cost = AiProviderRegistry.estimateCostUsd("gemini-3.1-flash-lite", usage)

        assertEquals(1000 * 0.25 / 1_000_000.0, cost!!, 1e-12)
    }

    @Test
    fun `a model with no cache rates at all costs exactly what it did before caching`() {
        val model = AiModelOption(
            id = "unpriced",
            label = "Unpriced",
            shortLabel = "U",
            inputUsdPerMTok = 3.0,
            outputUsdPerMTok = 9.0,
        )
        val usage = TokenUsage(
            inputTokens = 100,
            outputTokens = 50,
            cachedInputTokens = 400,
            cacheWriteInputTokens = 200,
        )
        val cachedRate = model.cachedInputUsdPerMTok ?: model.inputUsdPerMTok
        val writeRate = model.cacheWriteUsdPerMTok ?: model.inputUsdPerMTok

        // Every prompt token falls back to the full input price - the pre-caching result.
        assertEquals(model.inputUsdPerMTok, cachedRate, 0.0)
        assertEquals(model.inputUsdPerMTok, writeRate, 0.0)
        assertEquals(700L, usage.totalInputTokens)
    }

    @Test
    fun `cached tokens are cheaper than the same tokens at full price`() {
        val allFullPrice = TokenUsage(inputTokens = 10_000, outputTokens = 0)
        val allCached = TokenUsage(inputTokens = 0, outputTokens = 0, cachedInputTokens = 10_000)

        val full = AiProviderRegistry.estimateCostUsd("claude-opus-5", allFullPrice)!!
        val cached = AiProviderRegistry.estimateCostUsd("claude-opus-5", allCached)!!

        assertTrue("cached should be cheaper than full price", cached < full)
    }

    @Test
    fun `unknown model still yields no estimate`() {
        assertNull(AiProviderRegistry.estimateCostUsd("not-a-model", TokenUsage(1, 1)))
    }

    @Test
    fun `cost is never negative even for nonsense usage`() {
        val usage = normalizeCacheAwareUsage(
            promptTokens = -100,
            outputTokens = -100,
            cachedInputTokens = -100,
            cacheWriteInputTokens = -100,
            subsetAccounting = true,
        )

        assertEquals(0.0, AiProviderRegistry.estimateCostUsd("claude-opus-5", usage)!!, 0.0)
    }

    // --- remote catalog ---

    @Test
    fun `catalog cache rates are applied to the merged model`() {
        val json = """
            {"schemaVersion": 1, "providers": [{"id": "openai", "models": [
              {"id": "gpt-x", "label": "GPT X", "shortLabel": "X",
               "inputUsdPerMTok": 2.0, "outputUsdPerMTok": 8.0,
               "cachedInputUsdPerMTok": 0.2, "cacheWriteUsdPerMTok": 2.5}
            ]}]}
        """.trimIndent()

        val merged = mergeCatalog(AiProviderRegistry.builtInProviders, parseModelCatalog(json))
        val model = merged.providers.first { it.id == AiProviderRegistry.OPENAI }.models.single()

        assertEquals(0.2, model.cachedInputUsdPerMTok!!, 0.0)
        assertEquals(2.5, model.cacheWriteUsdPerMTok!!, 0.0)
    }

    @Test
    fun `a catalog model omitting cache rates keeps the model and falls back to input price`() {
        // A catalog authored before caching existed must still work.
        val json = """
            {"schemaVersion": 1, "providers": [{"id": "openai", "models": [
              {"id": "gpt-x", "label": "GPT X", "shortLabel": "X",
               "inputUsdPerMTok": 2.0, "outputUsdPerMTok": 8.0}
            ]}]}
        """.trimIndent()

        val merged = mergeCatalog(AiProviderRegistry.builtInProviders, parseModelCatalog(json))
        val model = merged.providers.first { it.id == AiProviderRegistry.OPENAI }.models.single()

        assertNull(model.cachedInputUsdPerMTok)
        assertNull(model.cacheWriteUsdPerMTok)
        assertTrue(merged.droppedModelIds.isEmpty())
    }

    @Test
    fun `a negative cache rate is dropped rather than discarding the whole model`() {
        val json = """
            {"schemaVersion": 1, "providers": [{"id": "openai", "models": [
              {"id": "gpt-x", "label": "GPT X", "shortLabel": "X",
               "inputUsdPerMTok": 2.0, "outputUsdPerMTok": 8.0,
               "cachedInputUsdPerMTok": -1.0}
            ]}]}
        """.trimIndent()

        val merged = mergeCatalog(AiProviderRegistry.builtInProviders, parseModelCatalog(json))
        val model = merged.providers.first { it.id == AiProviderRegistry.OPENAI }.models.single()

        assertEquals("gpt-x", model.id)
        assertNull(model.cachedInputUsdPerMTok)
        assertTrue(merged.droppedModelIds.isEmpty())
    }
}
