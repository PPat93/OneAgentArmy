package com.piotrek.oneagentarmy.provider.ai.anthropic

import com.piotrek.oneagentarmy.data.repository.SettingsRepository
import com.piotrek.oneagentarmy.model.Message
import com.piotrek.oneagentarmy.model.Sender
import com.piotrek.oneagentarmy.provider.ai.AiProvider
import com.piotrek.oneagentarmy.provider.ai.AiProviderException
import com.piotrek.oneagentarmy.provider.ai.AiProviderRegistry
import com.piotrek.oneagentarmy.provider.ai.AiReply
import com.piotrek.oneagentarmy.provider.ai.AttachmentReader
import com.piotrek.oneagentarmy.provider.ai.anthropic.dto.MessagesRequest
import com.piotrek.oneagentarmy.provider.ai.anthropic.dto.allToolUses
import com.piotrek.oneagentarmy.provider.ai.anthropic.dto.assistantMessage
import com.piotrek.oneagentarmy.provider.ai.anthropic.dto.functionToolJson
import com.piotrek.oneagentarmy.provider.ai.anthropic.dto.historyMessage
import com.piotrek.oneagentarmy.provider.ai.anthropic.dto.historyMessageWithAttachment
import com.piotrek.oneagentarmy.provider.ai.anthropic.dto.outputText
import com.piotrek.oneagentarmy.provider.ai.anthropic.dto.toolChoiceAutoNoParallel
import com.piotrek.oneagentarmy.provider.ai.anthropic.dto.toolChoiceNone
import com.piotrek.oneagentarmy.provider.ai.anthropic.dto.toolResultsMessage
import com.piotrek.oneagentarmy.provider.ai.anthropic.dto.webSearchToolJson
import com.piotrek.oneagentarmy.provider.ai.TokenUsage
import com.piotrek.oneagentarmy.provider.ai.anthropic.dto.toTokenUsage
import com.piotrek.oneagentarmy.provider.ai.buildSystemPrompt
import com.piotrek.oneagentarmy.provider.ai.tools.RoundTripToolExecutor
import com.piotrek.oneagentarmy.provider.ai.tools.ToolCallRequest
import com.piotrek.oneagentarmy.provider.ai.tools.ToolRegistry
import com.piotrek.oneagentarmy.provider.ai.tools.websearch.WEB_SEARCH_TOOL
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonElement

class AnthropicProvider(
    private val apiClient: AnthropicApiClient,
    private val settingsRepository: SettingsRepository,
    private val toolRegistry: ToolRegistry,
    executors: List<RoundTripToolExecutor>,
    private val attachmentReader: AttachmentReader,
    private val clock: Clock = Clock.systemDefaultZone(),
) : AiProvider {

    private val executorsByName = executors.associateBy { it.toolName }

    override suspend fun sendMessage(history: List<Message>, modelId: String, contextFacts: List<String>): AiReply {
        val apiKey = settingsRepository.getApiKey(AiProviderRegistry.ANTHROPIC)
        if (apiKey.isNullOrBlank()) throw AiProviderException.MissingApiKey

        val conversationId = history.last().conversationId

        // Hosted mode swaps the Tavily function tool for Anthropic's server-side
        // web search, which needs no key and no round-trip on our side.
        val useHostedSearch =
            settingsRepository.observeSearchProvider().first() == SettingsRepository.SEARCH_PROVIDER_BUILT_IN &&
                AiProviderRegistry.modelOptionFor(modelId)?.supportsHostedWebSearch == true

        // The dynamic-filtering search variant (Sonnet 5 / Opus 4.8) runs code
        // execution under the hood, which rejects disable_parallel_tool_use and
        // strict tools with HTTP 400 - both are skipped on those requests.
        val hostedSearchType = if (useHostedSearch) hostedSearchTypeFor(modelId) else null
        val usesDynamicFilteringSearch = hostedSearchType == "web_search_20260209"

        val functionTools = toolRegistry.definitions
            .filter { !(useHostedSearch && it.name == WEB_SEARCH_TOOL) }
            .filter { it.requiredKeyId == null || settingsRepository.getApiKey(it.requiredKeyId) != null }
            .map { functionToolJson(it, includeStrict = !usesDynamicFilteringSearch) }
        val tools: List<JsonElement> =
            if (hostedSearchType != null) functionTools + webSearchToolJson(hostedSearchType) else functionTools

        val system = buildSystemPrompt(clock, contextFacts)
        var messages: List<JsonElement> = history.map { historyMessageFor(it) }
        var roundTripsUsed = 0
        var pauseTurnsUsed = 0
        var usageTotal = TokenUsage.ZERO

        while (true) {
            // Hard cap on provider-executed tool round-trips per message. Tools stay
            // defined even past the cap (history with tool_use blocks requires them) -
            // further calls are blocked via tool_choice instead.
            val request = MessagesRequest(
                model = modelId,
                maxTokens = MAX_OUTPUT_TOKENS,
                system = system,
                messages = messages,
                tools = tools.ifEmpty { null },
                toolChoice = when {
                    tools.isEmpty() -> null
                    roundTripsUsed >= MAX_TOOL_ROUND_TRIPS -> toolChoiceNone()
                    // Default (parallel-capable) tool choice - the loop answers every
                    // tool_use in the turn, so parallel calls are safe here.
                    usesDynamicFilteringSearch -> null
                    else -> toolChoiceAutoNoParallel()
                },
            )
            val response = apiClient.createMessage(apiKey, request)
            usageTotal += response.usage.toTokenUsage()

            // Server-side tool loop (hosted web search) paused - echo the assistant
            // content back and the server resumes automatically. Not counted against
            // the client-side round-trip cap.
            if (response.stopReason == "pause_turn" && pauseTurnsUsed < MAX_PAUSE_TURNS) {
                pauseTurnsUsed++
                messages = messages + assistantMessage(response.content)
                continue
            }

            val toolUses = response.allToolUses()
            if (toolUses.isEmpty()) {
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

            // Without disable_parallel_tool_use (dynamic-filtering requests) a turn may
            // carry several tool_use blocks - each one needs a tool_result or the
            // follow-up 400s, so they are executed together as a single round-trip.
            val allHaveExecutors = toolUses.all { executorsByName.containsKey(it.name) }
            if (allHaveExecutors && roundTripsUsed < MAX_TOOL_ROUND_TRIPS) {
                roundTripsUsed++
                val results = toolUses.map { it.id to executorsByName.getValue(it.name).execute(it.inputJson) }
                // The assistant content is echoed verbatim - thinking blocks (Sonnet)
                // must return unchanged, and every tool_use needs its tool_result.
                messages = messages +
                    assistantMessage(response.content) +
                    toolResultsMessage(results)
                continue
            }

            // Tools without an executor (calendar, alarms, SMS...) have a client-side
            // effect the ViewModel must confirm with the user - handed back unresolved.
            // The whole turn is dropped client-side, so unanswered sibling tool_use
            // blocks never reach the API again.
            // Fallback to the first tool_use for the degenerate case of executor tools
            // arriving after the round-trip cap (tool_choice "none" should prevent it).
            val clientToolUse = toolUses.firstOrNull { !executorsByName.containsKey(it.name) } ?: toolUses.first()
            return AiReply.ToolCall(
                ToolCallRequest(clientToolUse.name, clientToolUse.inputJson),
                usage = usageTotal,
                costUsd = AiProviderRegistry.estimateCostUsd(modelId, usageTotal),
            )
        }
    }

    private suspend fun historyMessageFor(message: Message): JsonElement {
        if (message.sender != Sender.USER) return historyMessage("assistant", message.text)
        val path = message.attachmentPath ?: return historyMessage("user", message.text)
        val base64 = attachmentReader.readBase64(path)
            ?: return historyMessage("user", (message.text + "\n\n$MISSING_ATTACHMENT_NOTE").trim())
        return historyMessageWithAttachment(
            text = message.text,
            attachmentBase64 = base64,
            mime = message.attachmentMime ?: "application/octet-stream",
            isPdf = message.attachmentType == Message.ATTACHMENT_TYPE_PDF,
        )
    }

    // The dynamic-filtering web search variant requires Sonnet 5 tier models;
    // Haiku 4.5 supports only the basic variant.
    private fun hostedSearchTypeFor(modelId: String): String =
        if (modelId == "claude-haiku-4-5") "web_search_20250305" else "web_search_20260209"

    private companion object {
        const val MAX_TOOL_ROUND_TRIPS = 4
        const val MAX_PAUSE_TURNS = 5
        const val MAX_OUTPUT_TOKENS = 8192
        const val MISSING_ATTACHMENT_NOTE = "[attachment no longer available]"
    }
}
