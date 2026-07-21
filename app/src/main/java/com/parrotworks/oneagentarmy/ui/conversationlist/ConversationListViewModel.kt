package com.parrotworks.oneagentarmy.ui.conversationlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parrotworks.oneagentarmy.data.repository.ConversationRepository
import com.parrotworks.oneagentarmy.data.repository.ExchangeRateRepository
import com.parrotworks.oneagentarmy.data.repository.SettingsRepository
import com.parrotworks.oneagentarmy.model.Conversation
import com.parrotworks.oneagentarmy.provider.ai.AiProviderRegistry
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConversationListViewModel(
    private val repository: ConversationRepository,
    private val settingsRepository: SettingsRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
) : ViewModel() {

    init {
        viewModelScope.launch { exchangeRateRepository.refreshIfStale() }
    }

    val usdToEur: StateFlow<Double> = exchangeRateRepository.usdToEur
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.86)

    val conversations: StateFlow<List<Conversation>> = repository.observeConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeProvider: StateFlow<String> = settingsRepository.observeActiveProvider()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiProviderRegistry.OPENAI)

    // Estimated spend since the 1st of the current calendar month (system timezone).
    // The boundary is captured at ViewModel creation - fine, the screen is recreated
    // far more often than once a month.
    val monthlyCost: StateFlow<Double?> = repository.observeCostSince(
        LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant(),
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setActiveProvider(providerId: String) {
        viewModelScope.launch { settingsRepository.setActiveProvider(providerId) }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch { repository.deleteConversation(conversationId) }
    }

    fun deleteConversations(conversationIds: Collection<String>) {
        viewModelScope.launch { repository.deleteConversations(conversationIds.toList()) }
    }

    fun renameConversation(conversationId: String, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { repository.renameConversation(conversationId, title) }
    }

    fun setPinned(conversationId: String, pinned: Boolean) {
        viewModelScope.launch { repository.setPinned(conversationId, pinned) }
    }
}
