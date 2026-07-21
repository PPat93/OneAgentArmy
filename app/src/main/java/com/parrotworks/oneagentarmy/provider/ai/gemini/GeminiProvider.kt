package com.parrotworks.oneagentarmy.provider.ai.gemini

import com.parrotworks.oneagentarmy.data.repository.SettingsRepository
import com.parrotworks.oneagentarmy.model.Message
import com.parrotworks.oneagentarmy.model.Sender
import com.parrotworks.oneagentarmy.provider.ai.AiProvider
import com.parrotworks.oneagentarmy.provider.ai.AiProviderException
import com.parrotworks.oneagentarmy.provider.ai.AiProviderRegistry
import com.parrotworks.oneagentarmy.provider.ai.AiReply
import com.parrotworks.oneagentarmy.provider.ai.AttachmentReader
import com.parrotworks.oneagentarmy.provider.ai.TokenUsage
import com.parrotworks.oneagentarmy.provider.ai.buildSystemPrompt
import com.parrotworks.oneagentarmy.provider.ai.gemini.dto.InteractionsRequest
import com.parrotworks.oneagentarmy.provider.ai.gemini.dto.toTokenUsage
import com.parrotworks.oneagentarmy.provider.ai.gemini.dto.firstFunctionCall
import com.parrotworks.oneagentarmy.provider.ai.gemini.dto.functionResultStep
import com.parrotworks.oneagentarmy.provider.ai.gemini.dto.functionToolJson
import com.parrotworks.oneagentarmy.provider.ai.gemini.dto.googleSearchToolJson
import com.parrotworks.oneagentarmy.provider.ai.gemini.dto.modelOutputStep
import com.parrotworks.oneagentarmy.provider.ai.gemini.dto.outputText
import com.parrotworks.oneagentarmy.provider.ai.gemini.dto.userInputStep
import com.parrotworks.oneagentarmy.provider.ai.gemini.dto.userInputStepWithAttachment
import com.parrotworks.oneagentarmy.provider.ai.tools.RoundTripToolExecutor
import com.parrotworks.oneagentarmy.provider.ai.tools.ToolCallRequest
import com.parrotworks.oneagentarmy.provider.ai.tools.ToolRegistry
import com.parrotworks.oneagentarmy.provider.ai.tools.websearch.WEB_SEARCH_TOOL
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
    private val attachmentReader: AttachmentReader,
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
        var input: List<JsonElement> = history.map { historyStepFor(it) }
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

    private suspend fun historyStepFor(message: Message): JsonElement {
        if (message.sender != Sender.USER) return modelOutputStep(message.text)
        val path = message.attachmentPath ?: return userInputStep(message.text)
        val base64 = attachmentReader.readBase64(path)
            ?: return userInputStep((message.text + "\n\n$MISSING_ATTACHMENT_NOTE").trim())
        return userInputStepWithAttachment(
            text = message.text,
            attachmentBase64 = base64,
            mime = message.attachmentMime ?: "application/octet-stream",
            isPdf = message.attachmentType == Message.ATTACHMENT_TYPE_PDF,
        )
    }

    private companion object {
        const val MAX_TOOL_ROUND_TRIPS = 4
        const val MISSING_ATTACHMENT_NOTE = "[attachment no longer available]"
    }
}
