package com.parrotworks.oneagentarmy.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.parrotworks.oneagentarmy.provider.ai.AiProviderRegistry
import com.parrotworks.oneagentarmy.provider.ai.ModelAvailabilityChecker
import com.parrotworks.oneagentarmy.provider.ai.SUPPORTED_CATALOG_SCHEMA_VERSION
import com.parrotworks.oneagentarmy.provider.ai.missingModelIds
import com.parrotworks.oneagentarmy.provider.ai.parseModelCatalog
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

// Keeps AiProviderRegistry in sync with the models.json hosted in the app's GitHub repo.
// Fetch is on demand only (button in Settings -> AI providers); the last successful
// catalog is cached in DataStore and re-applied on every app start, so a refresh done
// once benefits all later sessions without any network dependency.
class ModelRegistryRepository(
    private val okHttpClient: OkHttpClient,
    private val dataStore: DataStore<Preferences>,
    private val settingsRepository: SettingsRepository,
) {
    sealed interface RefreshResult {
        data class Success(val droppedModelIds: List<String>) : RefreshResult
        data class Error(val detail: String) : RefreshResult
    }

    data class AvailabilityWarning(val providerName: String, val missingModelIds: List<String>)

    data class AvailabilityReport(
        val checkedProviderCount: Int,
        val warnings: List<AvailabilityWarning>,
        // Providers whose listing call failed (bad key, network...) - reported separately
        // from warnings so a failed check isn't mistaken for a retired model.
        val failedProviderNames: List<String>,
    )

    private val availabilityChecker = ModelAvailabilityChecker(okHttpClient)

    // Cross-checks every catalog model against what its provider actually lists.
    // Providers without a saved API key are skipped (their listing endpoints require one).
    suspend fun checkAvailability(): AvailabilityReport {
        var checked = 0
        val warnings = mutableListOf<AvailabilityWarning>()
        val failures = mutableListOf<String>()
        for (provider in AiProviderRegistry.providers) {
            val apiKey = settingsRepository.getApiKey(provider.id) ?: continue
            when (val result = availabilityChecker.listAvailable(provider.id, apiKey)) {
                is ModelAvailabilityChecker.ProviderCheck.Available -> {
                    checked++
                    val missing = missingModelIds(provider.models, result.modelIds)
                    if (missing.isNotEmpty()) {
                        warnings += AvailabilityWarning(provider.displayName, missing)
                    }
                }
                is ModelAvailabilityChecker.ProviderCheck.Failed -> failures += provider.displayName
            }
        }
        return AvailabilityReport(checked, warnings, failures)
    }

    val lastSyncedAtMillis: Flow<Long?> = dataStore.data.map { prefs -> prefs[CATALOG_SYNCED_AT_MILLIS] }

    // Applies the cached catalog (if any) to the registry. Called once at startup;
    // any problem with the cached copy silently keeps the built-in defaults.
    suspend fun applyCachedCatalog() {
        val cached = dataStore.data.first()[CATALOG_JSON] ?: return
        runCatching { parseModelCatalog(cached) }
            .onSuccess { catalog ->
                if (catalog.schemaVersion == SUPPORTED_CATALOG_SCHEMA_VERSION) {
                    AiProviderRegistry.applyRemoteCatalog(catalog)
                }
            }
    }

    suspend fun refresh(): RefreshResult = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(CATALOG_URL).build()
        val body = try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext RefreshResult.Error("HTTP ${response.code}")
                }
                response.body?.string().orEmpty()
            }
        } catch (e: IOException) {
            return@withContext RefreshResult.Error("${e.javaClass.simpleName}: ${e.message}")
        }

        val catalog = try {
            parseModelCatalog(body)
        } catch (e: Exception) {
            return@withContext RefreshResult.Error("Malformed catalog: ${e.message?.take(200)}")
        }
        if (catalog.schemaVersion != SUPPORTED_CATALOG_SCHEMA_VERSION) {
            return@withContext RefreshResult.Error(
                "Catalog schema v${catalog.schemaVersion} needs a newer app version",
            )
        }

        val droppedModelIds = AiProviderRegistry.applyRemoteCatalog(catalog)
        dataStore.edit { prefs ->
            prefs[CATALOG_JSON] = body
            prefs[CATALOG_SYNCED_AT_MILLIS] = System.currentTimeMillis()
        }
        RefreshResult.Success(droppedModelIds)
    }

    private companion object {
        // Raw view of models.json on the repo's default branch - editing that file on
        // GitHub is the whole update mechanism.
        const val CATALOG_URL = "https://raw.githubusercontent.com/PPat93/OneAgentArmy/master/models.json"
        val CATALOG_JSON = stringPreferencesKey("model_catalog_json")
        val CATALOG_SYNCED_AT_MILLIS = longPreferencesKey("model_catalog_synced_at_millis")
    }
}
