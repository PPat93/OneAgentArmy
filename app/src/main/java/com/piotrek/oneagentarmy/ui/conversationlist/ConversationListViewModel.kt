package com.piotrek.oneagentarmy.ui.conversationlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piotrek.oneagentarmy.data.repository.ConversationRepository
import com.piotrek.oneagentarmy.data.repository.SettingsRepository
import com.piotrek.oneagentarmy.model.Conversation
import com.piotrek.oneagentarmy.provider.ai.AiProviderRegistry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConversationListViewModel(
    private val repository: ConversationRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val conversations: StateFlow<List<Conversation>> = repository.observeConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeProvider: StateFlow<String> = settingsRepository.observeActiveProvider()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiProviderRegistry.OPENAI)

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
}
