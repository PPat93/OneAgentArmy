package com.parrotworks.oneagentarmy.provider.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {

    private fun catalogJson(providers: String) = """
        {"schemaVersion": 1, "updatedAt": "2026-07-23", "providers": [$providers]}
    """.trimIndent()

    private val validOpenAiProvider = """
        {"id": "openai", "models": [
            {"id": "gpt-x", "label": "GPT X - test", "labelPl": "GPT X - testowy",
             "shortLabel": "X", "inputUsdPerMTok": 0.5, "outputUsdPerMTok": 2.0,
             "supportsHostedWebSearch": true}
        ]}
    """.trimIndent()

    @Test
    fun `parses a valid catalog`() {
        val catalog = parseModelCatalog(catalogJson(validOpenAiProvider))

        assertEquals(1, catalog.schemaVersion)
        assertEquals("2026-07-23", catalog.updatedAt)
        val model = catalog.providers.single().models.single()
        assertEquals("gpt-x", model.id)
        assertEquals(0.5, model.inputUsdPerMTok, 0.0)
        assertTrue(model.supportsHostedWebSearch)
    }

    @Test
    fun `parsing ignores unknown keys for forward compatibility`() {
        val json = """
            {"schemaVersion": 1, "someFutureField": {"x": 1}, "providers": []}
        """.trimIndent()

        assertEquals(1, parseModelCatalog(json).schemaVersion)
    }

    @Test
    fun `malformed json throws`() {
        assertThrows(Exception::class.java) { parseModelCatalog("not json at all") }
        assertThrows(Exception::class.java) { parseModelCatalog("""{"providers": []}""") }
    }

    @Test
    fun `merge replaces models for a provider present in the catalog`() {
        val merged = mergeCatalog(AiProviderRegistry.builtInProviders, parseModelCatalog(catalogJson(validOpenAiProvider)))

        val openai = merged.providers.first { it.id == AiProviderRegistry.OPENAI }
        assertEquals(listOf("gpt-x"), openai.models.map { it.id })
        // Provider identity stays compiled-in even when models are replaced.
        assertEquals("OpenAI (ChatGPT)", openai.displayName)
        assertTrue(merged.droppedModelIds.isEmpty())
    }

    @Test
    fun `merge keeps built-in models for providers missing from the catalog`() {
        val merged = mergeCatalog(AiProviderRegistry.builtInProviders, parseModelCatalog(catalogJson(validOpenAiProvider)))

        val builtInGemini = AiProviderRegistry.builtInProviders.first { it.id == AiProviderRegistry.GEMINI }
        assertEquals(builtInGemini.models, merged.providers.first { it.id == AiProviderRegistry.GEMINI }.models)
    }

    @Test
    fun `merge ignores unknown provider ids`() {
        val json = catalogJson(
            """{"id": "mystery-ai", "models": [
                {"id": "m", "label": "M", "shortLabel": "M", "inputUsdPerMTok": 1.0, "outputUsdPerMTok": 1.0}
            ]}""",
        )

        val merged = mergeCatalog(AiProviderRegistry.builtInProviders, parseModelCatalog(json))

        assertEquals(AiProviderRegistry.builtInProviders, merged.providers)
        assertTrue(merged.droppedModelIds.isEmpty())
    }

    @Test
    fun `merge filters out invalid models keeps built-in and reports the dropped ids`() {
        val json = catalogJson(
            """{"id": "openai", "models": [
                {"id": "", "label": "blank id", "shortLabel": "B", "inputUsdPerMTok": 1.0, "outputUsdPerMTok": 1.0},
                {"id": "neg", "label": "negative price", "shortLabel": "N", "inputUsdPerMTok": -1.0, "outputUsdPerMTok": 1.0}
            ]}""",
        )

        val merged = mergeCatalog(AiProviderRegistry.builtInProviders, parseModelCatalog(json))

        assertEquals(AiProviderRegistry.builtInProviders, merged.providers)
        assertEquals(listOf("(missing id)", "neg"), merged.droppedModelIds)
    }

    @Test
    fun `merge reports a dropped id while still applying the valid models beside it`() {
        val json = catalogJson(
            """{"id": "openai", "models": [
                {"id": "gpt-good", "label": "Good", "shortLabel": "G", "inputUsdPerMTok": 1.0, "outputUsdPerMTok": 2.0},
                {"id": "gpt-bad", "label": "Bad", "shortLabel": "B", "inputUsdPerMTok": -1.0, "outputUsdPerMTok": 2.0}
            ]}""",
        )

        val merged = mergeCatalog(AiProviderRegistry.builtInProviders, parseModelCatalog(json))

        assertEquals(
            listOf("gpt-good"),
            merged.providers.first { it.id == AiProviderRegistry.OPENAI }.models.map { it.id },
        )
        assertEquals(listOf("gpt-bad"), merged.droppedModelIds)
    }

    @Test
    fun `merge with an empty catalog changes nothing`() {
        val merged = mergeCatalog(AiProviderRegistry.builtInProviders, parseModelCatalog(catalogJson("")))

        assertEquals(AiProviderRegistry.builtInProviders, merged.providers)
        assertTrue(merged.droppedModelIds.isEmpty())
    }

    @Test
    fun `the real models json in the repo parses and mirrors the built-in registry`() {
        // Working directory differs between Gradle (module dir) and IDE runs - try both.
        val file = listOf(java.io.File("../models.json"), java.io.File("models.json")).firstOrNull { it.exists() }
        org.junit.Assume.assumeTrue("models.json not found from test working dir", file != null)

        val catalog = parseModelCatalog(file!!.readText())

        assertEquals(SUPPORTED_CATALOG_SCHEMA_VERSION, catalog.schemaVersion)
        val merged = mergeCatalog(AiProviderRegistry.builtInProviders, catalog)
        // The shipped file starts as an exact mirror of the built-in registry, so a merge
        // must reproduce it - catching any drift between the two.
        assertEquals(
            AiProviderRegistry.builtInProviders.map { p -> p.models },
            merged.providers.map { p -> p.models },
        )
        assertTrue(merged.droppedModelIds.isEmpty())
    }

    @Test
    fun `labelFor falls back to english when no polish label exists`() {
        val option = AiModelOption(
            id = "m",
            label = "English label",
            labelPl = null,
            shortLabel = "M",
            inputUsdPerMTok = 1.0,
            outputUsdPerMTok = 1.0,
        )

        assertEquals("English label", option.labelFor(polish = true))
        assertEquals("English label", option.labelFor(polish = false))
        assertNull(option.labelPl)
    }

    @Test
    fun `labelFor prefers polish when present and requested`() {
        val option = AiModelOption(
            id = "m",
            label = "English label",
            labelPl = "Polska etykieta",
            shortLabel = "M",
            inputUsdPerMTok = 1.0,
            outputUsdPerMTok = 1.0,
        )

        assertEquals("Polska etykieta", option.labelFor(polish = true))
        assertEquals("English label", option.labelFor(polish = false))
    }
}
