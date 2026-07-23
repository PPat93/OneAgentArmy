package com.parrotworks.oneagentarmy.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.parrotworks.oneagentarmy.data.local.crypto.ApiKeyCipher
import com.parrotworks.oneagentarmy.data.local.crypto.EncryptedBlob
import com.parrotworks.oneagentarmy.provider.ai.AiProviderRegistry
import java.security.GeneralSecurityException
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
        return try {
            cipher.decrypt(EncryptedBlob(iv, ciphertext))
        } catch (e: GeneralSecurityException) {
            // The Keystore key that encrypted this blob doesn't exist anymore (e.g. this
            // DataStore file was restored from a backup taken on a different device/install) -
            // treat it the same as "no key saved" rather than crashing the caller.
            null
        }
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

    override fun observeAppLockEnabled(): Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[APP_LOCK_ENABLED] ?: false }

    override suspend fun setAppLockEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[APP_LOCK_ENABLED] = enabled }
    }

    override fun observeSpendingThresholdEur(): Flow<Double?> =
        dataStore.data.map { prefs -> prefs[SPENDING_THRESHOLD_EUR] }

    override suspend fun setSpendingThresholdEur(thresholdEur: Double?) {
        dataStore.edit { prefs ->
            if (thresholdEur == null) {
                prefs.remove(SPENDING_THRESHOLD_EUR)
            } else {
                prefs[SPENDING_THRESHOLD_EUR] = thresholdEur
            }
        }
    }

    override fun observeContextWindowSize(): Flow<Int> =
        dataStore.data.map { prefs -> prefs[CONTEXT_WINDOW_SIZE] ?: SettingsRepository.DEFAULT_CONTEXT_WINDOW_SIZE }

    override suspend fun setContextWindowSize(size: Int) {
        dataStore.edit { prefs -> prefs[CONTEXT_WINDOW_SIZE] = size }
    }

    override fun observeRequestTimeoutSeconds(): Flow<Int> =
        dataStore.data.map { prefs -> prefs[REQUEST_TIMEOUT_SECONDS] ?: SettingsRepository.DEFAULT_REQUEST_TIMEOUT_SECONDS }

    override suspend fun setRequestTimeoutSeconds(seconds: Int) {
        dataStore.edit { prefs -> prefs[REQUEST_TIMEOUT_SECONDS] = seconds }
    }

    private companion object {
        val ACTIVE_PROVIDER = stringPreferencesKey("active_provider")
        val SEARCH_PROVIDER = stringPreferencesKey("search_provider")
        val CHAT_FONT_SCALE = floatPreferencesKey("chat_font_scale")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val SPENDING_THRESHOLD_EUR = doublePreferencesKey("spending_threshold_eur")
        val CONTEXT_WINDOW_SIZE = intPreferencesKey("context_window_size")
        val REQUEST_TIMEOUT_SECONDS = intPreferencesKey("request_timeout_seconds")
    }
}
