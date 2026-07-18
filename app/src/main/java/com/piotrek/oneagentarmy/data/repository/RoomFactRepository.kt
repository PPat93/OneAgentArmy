package com.piotrek.oneagentarmy.data.repository

import com.piotrek.oneagentarmy.data.local.ConversationFactEntity
import com.piotrek.oneagentarmy.data.local.FactDao
import com.piotrek.oneagentarmy.data.local.toDomain
import com.piotrek.oneagentarmy.data.local.toEntity
import com.piotrek.oneagentarmy.model.Fact
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomFactRepository(
    private val dao: FactDao,
) : FactRepository {

    override fun observeFacts(): Flow<List<Fact>> =
        dao.observeFacts().map { entities -> entities.map { it.toDomain() } }

    override suspend fun createFact(title: String, content: String, isGlobal: Boolean) {
        val fact = Fact(
            id = UUID.randomUUID().toString(),
            title = title,
            content = content,
            isGlobal = isGlobal,
            createdAt = Instant.now(),
        )
        dao.insertFact(fact.toEntity())
    }

    override suspend fun updateFact(fact: Fact) {
        dao.updateFact(fact.toEntity())
    }

    override suspend fun deleteFact(factId: String) {
        dao.deleteFact(factId)
    }

    override fun observeSelectedFactIds(conversationId: String): Flow<Set<String>> =
        dao.observeSelectedFactIds(conversationId).map { it.toSet() }

    override suspend fun setFactSelected(conversationId: String, factId: String, selected: Boolean) {
        if (selected) {
            dao.selectFact(ConversationFactEntity(conversationId, factId))
        } else {
            dao.unselectFact(conversationId, factId)
        }
    }
}
