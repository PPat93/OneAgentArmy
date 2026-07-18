package com.piotrek.oneagentarmy.data.repository

import com.piotrek.oneagentarmy.model.Fact
import kotlinx.coroutines.flow.Flow

interface FactRepository {
    fun observeFacts(): Flow<List<Fact>>
    suspend fun createFact(title: String, content: String, isGlobal: Boolean)
    suspend fun updateFact(fact: Fact)
    suspend fun deleteFact(factId: String)

    fun observeSelectedFactIds(conversationId: String): Flow<Set<String>>
    suspend fun setFactSelected(conversationId: String, factId: String, selected: Boolean)
}
