package com.piotrek.oneagentarmy.provider.ai.gemini

import com.piotrek.oneagentarmy.data.repository.SettingsRepository
import com.piotrek.oneagentarmy.model.Message
import com.piotrek.oneagentarmy.model.Sender
import com.piotrek.oneagentarmy.provider.ai.AiProvider
import com.piotrek.oneagentarmy.provider.ai.AiProviderException
import com.piotrek.oneagentarmy.provider.ai.AiProviderRegistry
import com.piotrek.oneagentarmy.provider.ai.AiReply
import com.piotrek.oneagentarmy.provider.ai.TokenUsage
import com.piotrek.oneagentarmy.provider.ai.buildSystemPrompt
import com.piotrek.oneagentarmy.provider.ai.gemini.dto.InteractionsRequest
import com.piotrek.oneagentarmy.provider.ai.gemini.dto.toTokenUsage
import com.piotrek.oneagentarmy.provider.ai.gemini.dto.firstFunctionCall
import com.piotrek.oneagentarmy.provider.ai.gemini.dto.functionResultStep
import com.piotrek.oneagentarmy.provider.ai.gemini.dto.functionToolJson
import com.piotrek.oneagentarmy.provider.ai.gemini.dto.googleSearchToolJson
import com.piotrek.oneagentarmy.provider.ai.gemini.dto.modelOutputStep
import com.piotrek.oneagentarmy.provider.ai.gemini.dto.outputText
import com.piotrek.oneagentarmy.provider.ai.gemini.dto.userInputStep
import com.piotrek.oneagentarmy.provider.ai.tools.RoundTripToolExecutor
import com.piotrek.oneagentarmy.provider.ai.tools.ToolCallRequest
import com.piotrek.oneagentarmy.provider.ai.tools.ToolRegistry
import com.piotrek.oneagentarmy.provider.ai.tools.websearch.WEB_SEARCH_TOOL
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonElement

class GeminiProvider(
    private val apiClient: GeminiApiClient,
    private val settingsRepository: SettingsRepository,
    private val toolRegistry: ToolRegistry,
    executors: List<RoundTripToolExecutor>,
    private val clock: Clock = Clock.systemDefaultZone(),
) : AiProvider {

    private val executorsByName = executors.associateBy { it.toolName }

    override suspend fun sendMessage(history: List<Message>, modelId: String, contextFacts: List<String>): AiReply {
        val apiKey = settingsRepository.getApiKey(AiProviderRegistry.GEMINI)
        if (apiKey.isNullOrBlank()) throw AiProviderException.MissingApiKey

        val conversationId = history.last().conversationId

        // Hosted mode swaps the Tavily function tool for Google Search grounding,
        // which needs no key and no round-trip on our side.
        val useHostedSearch =
            settingsRepository.observeSearchProvider().first() == SettingsRepository.SEARCH_PROVIDER_BUILT_IN &&
                AiProviderRegistry.modelOptionFor(modelId)?.supportsHostedWebSearch == true

        val functionTools = toolRegistry.definitions
            .filter { !(useHostedSearch && it.name == WEB_SEARCH_TOOL) }
            .filter { it.requiredKeyId == null || settingsRepository.getApiKey(it.requiredKeyId) != null }
            .map { functionToolJson(it) }
        val tools: List<JsonElement> =
            if (useHostedSearch) functionTools + googleSearchToolJson() else functionTools

        val systemInstruction = buildSystemPrompt(clock, contextFacts)
        var input: List<JsonElement> = history.map {
            if (it.sender == Sender.USER) userInputStep(it.text) else modelOutputStep(it.text)
        }
        var roundTripsUsed = 0
        var usageTotal = TokenUsage.ZERO

        while (true) {
            // Hard cap on provider-executed tool round-trips per message: once used up,
            // the next request omits tools entirely, forcing a text answer.
            val toolsForThisRequest = if (roundTripsUsed >= MAX_TOOL_ROUND_TRIPS) null else tools.ifEmpty { null }

            val request = InteractionsRequest(
                model = modelId,
                input = input,
                systemInstruction = systemInstruction,
                tools = toolsForThisRequest,
            )
            val response = apiClient.createInteraction(apiKey, request)
            usageTotal += response.usage.toTokenUsage()

            val functionCall = response.firstFunctionCall()
            if (functionCall == null) {
                val replyText = response.outputText()
                    ?: throw AiProviderException.Unknown("Response contained neither text nor a tool call")
                return AiReply.Text(
                    Message(
                        id = UUID.randomUUID().toString(),
                        conversationId = conversationId,
                        sender = Sender.AI,
                        text = replyText,
                        timestamp = Instant.now(),
                        inputTokens = usageTotal.inputTokens,
                        outputTokens = usageTotal.outputTokens,
                        costUsd = AiProviderRegistry.estimateCostUsd(modelId, usageTotal),
                    ),
                )
            }

            val executor = executorsByName[functionCall.name]
            if (executor != null && roundTripsUsed < MAX_TOOL_ROUND_TRIPS) {
                roundTripsUsed++
                val result = executor.execute(functionCall.argumentsJson)
                // All response steps are replayed verbatim - thought and function_call
                // steps carry signatures the API requires back unchanged, and the
                // function_call step must precede its result.
                input = input + response.steps + functionResultStep(functionCall.id, result)
                continue
            }

            // Tools without an executor (calendar, alarms, SMS...) have a client-side
            // effect the ViewModel must confirm with the user - handed back unresolved.
            return AiReply.ToolCall(
                ToolCallRequest(functionCall.name, functionCall.argumentsJson),
                usage = usageTotal,
                costUsd = AiProviderRegistry.estimateCostUsd(modelId, usageTotal),
            )
        }
    }

    private companion object {
        const val MAX_TOOL_ROUND_TRIPS = 4
    }
}
