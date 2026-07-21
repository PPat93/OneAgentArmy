package com.piotrek.oneagentarmy.data.repository

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

    companion object {
        // Active AI provider's hosted web search (e.g. OpenAI's web_search tool).
        const val SEARCH_PROVIDER_BUILT_IN = "provider"
        const val SEARCH_PROVIDER_TAVILY = "tavily"
    }
}
