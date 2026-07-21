package com.parrotworks.oneagentarmy.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parrotworks.oneagentarmy.data.repository.FactRepository
import com.parrotworks.oneagentarmy.model.Fact
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FactsViewModel(
    private val repository: FactRepository,
) : ViewModel() {

    val facts: StateFlow<List<Fact>> = repository.observeFacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveFact(existing: Fact?, title: String, content: String, isGlobal: Boolean) {
        if (title.isBlank() || content.isBlank()) return
        viewModelScope.launch {
            if (existing == null) {
                repository.createFact(title.trim(), content.trim(), isGlobal)
            } else {
                repository.updateFact(existing.copy(title = title.trim(), content = content.trim(), isGlobal = isGlobal))
            }
        }
    }

    fun deleteFact(factId: String) {
        viewModelScope.launch { repository.deleteFact(factId) }
    }
}
