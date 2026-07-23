package com.parrotworks.oneagentarmy.data.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeHasApiKey(providerId: String): Flow<Boolean>
    suspend fun getApiKey(providerId: String): String?
    suspend fun saveApiKey(providerId: String, key: String)
    suspend fun clearApiKey(providerId: String)

    fun observeActiveProvider(): Flow<String>
    suspend fun setActiveProvider(providerId: String)

    fun observeSearchProvider(): Flow<String>
    suspend fun setSearchProvider(searchProviderId: String)

    fun observeChatFontScale(): Flow<Float>
    suspend fun setChatFontScale(scale: Float)

    fun observeAppLockEnabled(): Flow<Boolean>
    suspend fun setAppLockEnabled(enabled: Boolean)

    fun observeSpendingThresholdEur(): Flow<Double?>
    suspend fun setSpendingThresholdEur(thresholdEur: Double?)

    fun observeContextWindowSize(): Flow<Int>
    suspend fun setContextWindowSize(size: Int)

    fun observeRequestTimeoutSeconds(): Flow<Int>
    suspend fun setRequestTimeoutSeconds(seconds: Int)

    companion object {
        // Active AI provider's hosted web search (e.g. OpenAI's web_search tool).
        const val SEARCH_PROVIDER_BUILT_IN = "provider"
        const val SEARCH_PROVIDER_TAVILY = "tavily"

        // Number of previous messages resent as context on every new message - matches
        // the value this was hardcoded to before it became configurable.
        const val DEFAULT_CONTEXT_WINDOW_SIZE = 20

        // Hard ceiling on both the global default and any per-conversation override -
        // guards against a fat-fingered value (an extra zero or two), not a real use case.
        const val MAX_CONTEXT_WINDOW_SIZE = 10_000

        // How long a single AI request may run before being cancelled. Flagship/reasoning
        // models routinely need minutes; the ceiling keeps a stuck request from hanging
        // the send state indefinitely.
        const val DEFAULT_REQUEST_TIMEOUT_SECONDS = 240
        const val MAX_REQUEST_TIMEOUT_SECONDS = 900
    }
}
