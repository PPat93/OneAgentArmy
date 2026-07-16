package com.piotrek.oneagentarmy.data.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeHasApiKey(providerId: String): Flow<Boolean>
    suspend fun getApiKey(providerId: String): String?
    suspend fun saveApiKey(providerId: String, key: String)
    suspend fun clearApiKey(providerId: String)

    fun observeActiveProvider(): Flow<String>
    suspend fun setActiveProvider(providerId: String)

    fun observeSelectedModel(): Flow<String>
    suspend fun setSelectedModel(modelId: String)
}
