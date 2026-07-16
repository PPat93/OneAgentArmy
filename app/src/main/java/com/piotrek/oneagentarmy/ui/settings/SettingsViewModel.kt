package com.piotrek.oneagentarmy.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piotrek.oneagentarmy.data.repository.SettingsRepository
import com.piotrek.oneagentarmy.provider.ai.openai.DEFAULT_OPENAI_MODEL
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository,
) : ViewModel() {

    val hasApiKey: StateFlow<Boolean> = repository.observeHasApiKey()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val selectedModel: StateFlow<String> = repository.observeSelectedModel()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_OPENAI_MODEL)

    fun saveApiKey(key: String) {
        if (key.isBlank()) return
        viewModelScope.launch { repository.saveApiKey(key) }
    }

    fun clearApiKey() {
        viewModelScope.launch { repository.clearApiKey() }
    }

    fun selectModel(modelId: String) {
        viewModelScope.launch { repository.setSelectedModel(modelId) }
    }
}
