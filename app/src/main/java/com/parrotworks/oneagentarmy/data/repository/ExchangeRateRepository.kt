package com.parrotworks.oneagentarmy.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

// Daily USD->EUR rate from the ECB (via the free, key-less frankfurter.app API),
// cached in DataStore. Costs are stored in USD (provider pricing currency) and
// converted to EUR only at display time.
class ExchangeRateRepository(
    private val okHttpClient: OkHttpClient,
    private val dataStore: DataStore<Preferences>,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val usdToEur: Flow<Double> = dataStore.data.map { prefs -> prefs[RATE] ?: FALLBACK_USD_TO_EUR }

    // Best-effort refresh, at most once a day; failures keep the cached (or
    // fallback) rate - cost display must never break because of connectivity.
    suspend fun refreshIfStale() {
        val fetchedAt = dataStore.data.first()[FETCHED_AT] ?: 0L
        if (System.currentTimeMillis() - fetchedAt < REFRESH_INTERVAL_MILLIS) return

        val rate = try {
            fetchRate()
        } catch (e: Exception) {
            return
        }
        dataStore.edit { prefs ->
            prefs[RATE] = rate
            prefs[FETCHED_AT] = System.currentTimeMillis()
        }
    }

    private suspend fun fetchRate(): Double = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(RATES_URL).get().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            json.decodeFromString(FrankfurterResponse.serializer(), body).rates.getValue("EUR")
        }
    }

    @Serializable
    private data class FrankfurterResponse(val rates: Map<String, Double> = emptyMap())

    private companion object {
        const val RATES_URL = "https://api.frankfurter.app/latest?base=USD&symbols=EUR"
        const val REFRESH_INTERVAL_MILLIS = 24L * 60 * 60 * 1000
        // Used until the first successful fetch.
        const val FALLBACK_USD_TO_EUR = 0.86
        val RATE = doublePreferencesKey("usd_to_eur_rate")
        val FETCHED_AT = longPreferencesKey("usd_to_eur_fetched_at")
    }
}
