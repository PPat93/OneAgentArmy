package com.piotrek.oneagentarmy.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.piotrek.oneagentarmy.data.local.crypto.ApiKeyCipher
import com.piotrek.oneagentarmy.data.local.crypto.EncryptedBlob
import com.piotrek.oneagentarmy.provider.ai.AiProviderRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val cipher: ApiKeyCipher,
) : SettingsRepository {

    private fun ivKey(providerId: String) = stringPreferencesKey("api_key_iv_$providerId")
    private fun ciphertextKey(providerId: String) = stringPreferencesKey("api_key_ciphertext_$providerId")

    override fun observeHasApiKey(providerId: String): Flow<Boolean> =
        dataStore.data.map { prefs -> !prefs[ciphertextKey(providerId)].isNullOrEmpty() }

    override suspend fun getApiKey(providerId: String): String? {
        val prefs = dataStore.data.first()
        val iv = prefs[ivKey(providerId)] ?: return null
        val ciphertext = prefs[ciphertextKey(providerId)] ?: return null
        return cipher.decrypt(EncryptedBlob(iv, ciphertext))
    }

    override suspend fun saveApiKey(providerId: String, key: String) {
        val blob = cipher.encrypt(key)
        dataStore.edit { prefs ->
            prefs[ivKey(providerId)] = blob.iv
            prefs[ciphertextKey(providerId)] = blob.ciphertext
        }
    }

    override suspend fun clearApiKey(providerId: String) {
        dataStore.edit { prefs ->
            prefs.remove(ivKey(providerId))
            prefs.remove(ciphertextKey(providerId))
        }
    }

    override fun observeActiveProvider(): Flow<String> =
        dataStore.data.map { prefs -> prefs[ACTIVE_PROVIDER] ?: AiProviderRegistry.OPENAI }

    override suspend fun setActiveProvider(providerId: String) {
        dataStore.edit { prefs -> prefs[ACTIVE_PROVIDER] = providerId }
    }

    override fun observeSearchProvider(): Flow<String> =
        dataStore.data.map { prefs -> prefs[SEARCH_PROVIDER] ?: SettingsRepository.SEARCH_PROVIDER_BUILT_IN }

    override suspend fun setSearchProvider(searchProviderId: String) {
        dataStore.edit { prefs -> prefs[SEARCH_PROVIDER] = searchProviderId }
    }

    override fun observeChatFontScale(): Flow<Float> =
        dataStore.data.map { prefs -> prefs[CHAT_FONT_SCALE] ?: 1.0f }

    override suspend fun setChatFontScale(scale: Float) {
        dataStore.edit { prefs -> prefs[CHAT_FONT_SCALE] = scale }
    }

    private companion object {
        val ACTIVE_PROVIDER = stringPreferencesKey("active_provider")
        val SEARCH_PROVIDER = stringPreferencesKey("search_provider")
        val CHAT_FONT_SCALE = floatPreferencesKey("chat_font_scale")
    }
}
