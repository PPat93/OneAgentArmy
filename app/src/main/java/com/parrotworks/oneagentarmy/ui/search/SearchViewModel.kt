package com.parrotworks.oneagentarmy.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parrotworks.oneagentarmy.data.repository.ConversationRepository
import com.parrotworks.oneagentarmy.model.MessageSearchResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val repository: ConversationRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val results: StateFlow<List<MessageSearchResult>> = _query
        .debounce(300)
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList()) else repository.searchMessages(q.trim())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }
}
