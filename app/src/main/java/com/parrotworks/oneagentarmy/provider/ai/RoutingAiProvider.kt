package com.parrotworks.oneagentarmy.provider.ai

import com.parrotworks.oneagentarmy.model.Message

// Routes each request to the provider that owns the requested model - not to the
// "active provider" setting - so conversations pinned to an OpenAI model keep
// working after the user switches the active provider to Gemini (and vice versa).
class RoutingAiProvider(
    private val providers: Map<String, AiProvider>,
) : AiProvider {

    override suspend fun sendMessage(history: List<Message>, modelId: String, contextFacts: List<String>): AiReply {
        val providerId = AiProviderRegistry.providerIdForModel(modelId)
        val provider = providers[providerId]
            ?: throw AiProviderException.Unknown("No AiProvider wired for provider '$providerId' (model '$modelId')")
        return provider.sendMessage(history, modelId, contextFacts)
    }
}
