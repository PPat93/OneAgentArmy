package com.parrotworks.oneagentarmy.provider.ai

import com.parrotworks.oneagentarmy.provider.ai.anthropic.dto.MessagesResponse
import com.parrotworks.oneagentarmy.provider.ai.gemini.dto.InteractionsResponse
import com.parrotworks.oneagentarmy.provider.ai.openai.dto.ResponsesResponse
// Same function name on three different receivers - Kotlin treats the bare imports as
// ambiguous even though the receiver types differ, so each gets an alias.
import com.parrotworks.oneagentarmy.provider.ai.anthropic.dto.hostedSearchCallCount as anthropicSearchCount
import com.parrotworks.oneagentarmy.provider.ai.gemini.dto.hostedSearchCallCount as geminiSearchCount
import com.parrotworks.oneagentarmy.provider.ai.openai.dto.hostedSearchCallCount as openAiSearchCount
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HostedSearchCostTest {

    private val json = Json { ignoreUnknownKeys = true }

    // --- counting, per provider ---

    @Test
    fun `openai counts one per web_search_call output item`() {
        val body = """
            {"output":[
              {"type":"web_search_call","id":"ws_1","status":"completed"},
              {"type":"web_search_call","id":"ws_2","status":"completed"},
              {"type":"reasoning","id":"rs_1"},
              {"type":"message","content":[{"type":"output_text","text":"here you go"}]}
            ]}
        """.trimIndent()

        val response = json.decodeFromString(ResponsesResponse.serializer(), body)

        assertEquals(2, response.openAiSearchCount())
    }

    @Test
    fun `openai counts nothing when no search ran`() {
        val body = """{"output":[{"type":"message","content":[{"type":"output_text","text":"from memory"}]}]}"""

        assertEquals(0, json.decodeFromString(ResponsesResponse.serializer(), body).openAiSearchCount())
    }

    @Test
    fun `anthropic counts server_tool_use blocks named web_search only`() {
        val body = """
            {"content":[
              {"type":"server_tool_use","id":"srvtoolu_1","name":"web_search","input":{"query":"kurs euro"}},
              {"type":"web_search_tool_result","tool_use_id":"srvtoolu_1","content":[]},
              {"type":"server_tool_use","id":"srvtoolu_2","name":"code_execution","input":{}},
              {"type":"text","text":"4.27 PLN"}
            ]}
        """.trimIndent()

        val response = json.decodeFromString(MessagesResponse.serializer(), body)

        // The result block is not a second search, and code execution is not billed as one.
        assertEquals(1, response.anthropicSearchCount())
    }

    @Test
    fun `gemini matches any step type mentioning search`() {
        // The exact step name is not pinned down, so the match is deliberately loose -
        // over-counting shows a slightly high cost, under-counting hides a real charge.
        val body = """
            {"steps":[
              {"type":"google_search_call","id":"s1"},
              {"type":"web_search","id":"s2"},
              {"type":"model_output","content":[{"type":"text","text":"done"}]}
            ]}
        """.trimIndent()

        assertEquals(2, json.decodeFromString(InteractionsResponse.serializer(), body).geminiSearchCount())
    }

    @Test
    fun `gemini counts nothing for an ordinary answer`() {
        val body = """{"steps":[{"type":"model_output","content":[{"type":"text","text":"hi"}]}]}"""

        assertEquals(0, json.decodeFromString(InteractionsResponse.serializer(), body).geminiSearchCount())
    }

    // --- pricing ---

    private val searchModel = AiProviderRegistry.builtInProviders
        .first { it.id == AiProviderRegistry.OPENAI }
        .models.first { it.supportsHostedWebSearch }

    @Test
    fun `searches are charged on top of tokens`() {
        val usage = TokenUsage(inputTokens = 1_000_000, outputTokens = 0)

        val withoutSearch = AiProviderRegistry.estimateCostUsd(searchModel.id, usage, 0)!!
        val withThree = AiProviderRegistry.estimateCostUsd(searchModel.id, usage, 3)!!

        assertEquals(searchModel.inputUsdPerMTok, withoutSearch, 1e-9)
        assertEquals(withoutSearch + 3 * searchModel.hostedSearchUsdPerCall!!, withThree, 1e-9)
    }

    @Test
    fun `omitting the count keeps the old token-only estimate`() {
        // Every existing caller that has not been updated must keep behaving identically.
        val usage = TokenUsage(inputTokens = 5_000, outputTokens = 2_000)

        assertEquals(
            AiProviderRegistry.estimateCostUsd(searchModel.id, usage, 0),
            AiProviderRegistry.estimateCostUsd(searchModel.id, usage),
        )
    }

    @Test
    fun `an unpriced model falls back to the pessimistic default, never to free`() {
        val unpriced = AiModelOption(
            id = "brand-new-model",
            label = "New",
            shortLabel = "N",
            inputUsdPerMTok = 1.0,
            outputUsdPerMTok = 1.0,
        )
        AiProviderRegistry.applyRemoteCatalog(catalogWith(unpriced))
        try {
            val cost = AiProviderRegistry.estimateCostUsd("brand-new-model", TokenUsage.ZERO, 4)!!

            assertEquals(4 * AiProviderRegistry.DEFAULT_HOSTED_SEARCH_USD_PER_CALL, cost, 1e-9)
            assertTrue("an unpriced search must not read as free", cost > 0.0)
        } finally {
            // An empty catalog leaves every provider on its built-in model list, which is
            // the only way back to a clean registry - applyRemoteCatalog takes no null.
            AiProviderRegistry.applyRemoteCatalog(
                ModelCatalog(schemaVersion = SUPPORTED_CATALOG_SCHEMA_VERSION),
            )
        }
    }

    @Test
    fun `a negative count cannot subtract from the bill`() {
        val usage = TokenUsage(inputTokens = 1_000_000, outputTokens = 0)

        assertEquals(
            AiProviderRegistry.estimateCostUsd(searchModel.id, usage, 0),
            AiProviderRegistry.estimateCostUsd(searchModel.id, usage, -5),
        )
    }

    @Test
    fun `every search-capable built-in model carries its own rate`() {
        // Relying on the pessimistic default for a model we ship would show prices we know
        // to be wrong - the default exists for models added later via the catalog.
        val unpriced = AiProviderRegistry.builtInProviders
            .flatMap { it.models }
            .filter { it.supportsHostedWebSearch && it.hostedSearchUsdPerCall == null }

        assertTrue("missing hostedSearchUsdPerCall: ${unpriced.map { it.id }}", unpriced.isEmpty())
    }

    private fun catalogWith(model: AiModelOption) = ModelCatalog(
        schemaVersion = SUPPORTED_CATALOG_SCHEMA_VERSION,
        providers = listOf(
            CatalogProvider(
                id = AiProviderRegistry.OPENAI,
                models = listOf(
                    CatalogModel(
                        id = model.id,
                        label = model.label,
                        shortLabel = model.shortLabel,
                        inputUsdPerMTok = model.inputUsdPerMTok,
                        outputUsdPerMTok = model.outputUsdPerMTok,
                    ),
                ),
            ),
        ),
    )
}
