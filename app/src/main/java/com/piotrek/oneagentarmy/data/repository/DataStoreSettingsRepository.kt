package com.piotrek.oneagentarmy.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.piotrek.oneagentarmy.data.local.crypto.ApiKeyCipher
import com.piotrek.oneagentarmy.data.local.crypto.EncryptedBlob
import com.piotrek.oneagentarmy.provider.ai.openai.DEFAULT_OPENAI_MODEL
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val cipher: ApiKeyCipher,
) : SettingsRepository {

    override fun observeHasApiKey(): Flow<Boolean> =
        dataStore.data.map { prefs -> !prefs[API_KEY_CIPHERTEXT].isNullOrEmpty() }

    override suspend fun getApiKey(): String? {
        val prefs = dataStore.data.first()
        val iv = prefs[API_KEY_IV] ?: return null
        val ciphertext = prefs[API_KEY_CIPHERTEXT] ?: return null
        return cipher.decrypt(EncryptedBlob(iv, ciphertext))
    }

    override suspend fun saveApiKey(key: String) {
        val blob = cipher.encrypt(key)
        dataStore.edit { prefs ->
            prefs[API_KEY_IV] = blob.iv
            prefs[API_KEY_CIPHERTEXT] = blob.ciphertext
        }
    }

    override suspend fun clearApiKey() {
        dataStore.edit { prefs ->
            prefs.remove(API_KEY_IV)
            prefs.remove(API_KEY_CIPHERTEXT)
        }
    }

    override fun observeSelectedModel(): Flow<String> =
        dataStore.data.map { prefs -> prefs[SELECTED_MODEL] ?: DEFAULT_OPENAI_MODEL }

    override suspend fun setSelectedModel(modelId: String) {
        dataStore.edit { prefs -> prefs[SELECTED_MODEL] = modelId }
    }

    private companion object {
        val API_KEY_IV = stringPreferencesKey("api_key_iv")
        val API_KEY_CIPHERTEXT = stringPreferencesKey("api_key_ciphertext")
        val SELECTED_MODEL = stringPreferencesKey("selected_model")
    }
}
