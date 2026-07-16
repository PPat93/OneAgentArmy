package com.piotrek.oneagentarmy.data.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeHasApiKey(): Flow<Boolean>
    suspend fun getApiKey(): String?
    suspend fun saveApiKey(key: String)
    suspend fun clearApiKey()

    fun observeSelectedModel(): Flow<String>
    suspend fun setSelectedModel(modelId: String)
}
