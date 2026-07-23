package com.parrotworks.oneagentarmy.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parrotworks.oneagentarmy.data.repository.ConversationRepository
import com.parrotworks.oneagentarmy.data.repository.SettingsRepository
import com.parrotworks.oneagentarmy.provider.ai.AiProviderRegistry
import com.parrotworks.oneagentarmy.provider.ai.tools.websearch.TAVILY_KEY_ID
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val conversationRepository: ConversationRepository,
) : ViewModel() {

    val activeProvider: StateFlow<String> = repository.observeActiveProvider()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiProviderRegistry.OPENAI)

    val apiKeyStates: StateFlow<Map<String, Boolean>> = combine(
        AiProviderRegistry.providers.map { provider ->
            repository.observeHasApiKey(provider.id).map { provider.id to it }
        },
    ) { pairs -> pairs.toMap() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val tavilyHasKey: StateFlow<Boolean> = repository.observeHasApiKey(TAVILY_KEY_ID)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val searchProvider: StateFlow<String> = repository.observeSearchProvider()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.SEARCH_PROVIDER_BUILT_IN)

    val chatFontScale: StateFlow<Float> = repository.observeChatFontScale()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0f)

    val appLockEnabled: StateFlow<Boolean> = repository.observeAppLockEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val spendingThresholdEur: StateFlow<Double?> = repository.observeSpendingThresholdEur()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val contextWindowSize: StateFlow<Int> = repository.observeContextWindowSize()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.DEFAULT_CONTEXT_WINDOW_SIZE)

    val requestTimeoutSeconds: StateFlow<Int> = repository.observeRequestTimeoutSeconds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.DEFAULT_REQUEST_TIMEOUT_SECONDS)

    fun saveApiKey(providerId: String, key: String) {
        if (key.isBlank()) return
        viewModelScope.launch { repository.saveApiKey(providerId, key) }
    }

    fun clearApiKey(providerId: String) {
        viewModelScope.launch { repository.clearApiKey(providerId) }
    }

    fun setActiveProvider(providerId: String) {
        viewModelScope.launch { repository.setActiveProvider(providerId) }
    }

    fun setChatFontScale(scale: Float) {
        viewModelScope.launch { repository.setChatFontScale(scale) }
    }

    fun setSearchProvider(searchProviderId: String) {
        viewModelScope.launch { repository.setSearchProvider(searchProviderId) }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setAppLockEnabled(enabled) }
    }

    fun setSpendingThresholdEur(thresholdEur: Double?) {
        viewModelScope.launch { repository.setSpendingThresholdEur(thresholdEur) }
    }

    fun deleteAllConversations() {
        viewModelScope.launch { conversationRepository.deleteAllConversations() }
    }

    fun setContextWindowSize(size: Int) {
        viewModelScope.launch { repository.setContextWindowSize(size) }
    }

    fun setRequestTimeoutSeconds(seconds: Int) {
        viewModelScope.launch { repository.setRequestTimeoutSeconds(seconds) }
    }
}
