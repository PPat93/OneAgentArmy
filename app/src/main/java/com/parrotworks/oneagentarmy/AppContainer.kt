package com.parrotworks.oneagentarmy

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.parrotworks.oneagentarmy.data.local.AppDatabase
import com.parrotworks.oneagentarmy.data.local.AttachmentStore
import com.parrotworks.oneagentarmy.data.local.crypto.ApiKeyCipher
import com.parrotworks.oneagentarmy.data.repository.ConversationRepository
import com.parrotworks.oneagentarmy.data.repository.DataStoreSettingsRepository
import com.parrotworks.oneagentarmy.data.repository.ExchangeRateRepository
import com.parrotworks.oneagentarmy.data.repository.FactRepository
import com.parrotworks.oneagentarmy.data.repository.ModelRegistryRepository
import com.parrotworks.oneagentarmy.data.repository.RoomConversationRepository
import com.parrotworks.oneagentarmy.data.repository.RoomFactRepository
import com.parrotworks.oneagentarmy.data.repository.SettingsRepository
import com.parrotworks.oneagentarmy.provider.ai.AiProvider
import com.parrotworks.oneagentarmy.provider.ai.AiProviderRegistry
import com.parrotworks.oneagentarmy.provider.ai.AttachmentReader
import com.parrotworks.oneagentarmy.provider.ai.RoutingAiProvider
import com.parrotworks.oneagentarmy.provider.ai.anthropic.AnthropicApiClient
import com.parrotworks.oneagentarmy.provider.ai.anthropic.AnthropicProvider
import com.parrotworks.oneagentarmy.provider.ai.gemini.GeminiApiClient
import com.parrotworks.oneagentarmy.provider.ai.gemini.GeminiProvider
import com.parrotworks.oneagentarmy.provider.ai.openai.OpenAiApiClient
import com.parrotworks.oneagentarmy.provider.ai.openai.OpenAiProvider
import com.parrotworks.oneagentarmy.provider.ai.tools.ToolRegistry
import com.parrotworks.oneagentarmy.provider.ai.tools.weather.OpenMeteoWeatherClient
import com.parrotworks.oneagentarmy.provider.ai.tools.weather.WeatherExecutor
import com.parrotworks.oneagentarmy.provider.ai.tools.weather.WeatherToolDefinition
import com.parrotworks.oneagentarmy.provider.ai.tools.websearch.TavilyWebSearchClient
import com.parrotworks.oneagentarmy.provider.ai.tools.websearch.WebSearchExecutor
import com.parrotworks.oneagentarmy.provider.ai.tools.websearch.WebSearchToolDefinition
import com.parrotworks.oneagentarmy.tools.calendar.CalendarToolDefinition
import com.parrotworks.oneagentarmy.tools.calendar.OpenCalendarToolDefinition
import com.parrotworks.oneagentarmy.tools.clock.SetAlarmToolDefinition
import com.parrotworks.oneagentarmy.tools.clock.SetTimerToolDefinition
import com.parrotworks.oneagentarmy.tools.navigation.NavigateToolDefinition
import com.parrotworks.oneagentarmy.tools.notes.CreateNoteToolDefinition
import com.parrotworks.oneagentarmy.tools.sms.DraftSmsToolDefinition
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.OkHttpClient

class AppContainer(context: Context) {
    private val database = Room.databaseBuilder(context, AppDatabase::class.java, "oneagentarmy.db")
        .addMigrations(
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
            AppDatabase.MIGRATION_5_6,
            AppDatabase.MIGRATION_6_7,
            AppDatabase.MIGRATION_7_8,
            AppDatabase.MIGRATION_8_9,
            AppDatabase.MIGRATION_9_10,
        )
        .build()

    val attachmentStore = AttachmentStore(context)

    private val attachmentReader = AttachmentReader { path -> attachmentStore.readBase64(path) }

    val conversationRepository: ConversationRepository =
        RoomConversationRepository(database.conversationDao(), database.draftDao(), attachmentStore)

    val factRepository: FactRepository = RoomFactRepository(database.factDao())

    private val settingsDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("settings") },
    )

    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(settingsDataStore, ApiKeyCipher())

    // App-lifetime scope for keeping the cached AI timeout in sync with Settings - the
    // interceptor below runs on OkHttp's network threads and can't suspend to read
    // DataStore, so the current value is mirrored into an atomic.
    private val containerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val aiRequestTimeoutSeconds = AtomicInteger(SettingsRepository.DEFAULT_REQUEST_TIMEOUT_SECONDS)

    init {
        containerScope.launch {
            settingsRepository.observeRequestTimeoutSeconds().collect { aiRequestTimeoutSeconds.set(it) }
        }
    }

    // The user-configurable timeout applies only to the AI providers (the calls that can
    // legitimately run for minutes on a flagship/reasoning model). Weather, web search,
    // and exchange-rate calls keep the client's tighter default below - a stuck utility
    // call should fail fast, not hang for up to 15 minutes.
    private val aiTimeoutInterceptor = Interceptor { chain ->
        val request = chain.request()
        if (request.url.host in AI_PROVIDER_HOSTS) {
            chain.withReadTimeout(aiRequestTimeoutSeconds.get(), TimeUnit.SECONDS).proceed(request)
        } else {
            chain.proceed(request)
        }
    }

    // Defaults (10s connect/read/write) are too tight for non-streaming LLM responses -
    // a slow/reasoning model, or a hosted-search round-trip, can easily leave the socket
    // idle (zero bytes) for well over 10s while the provider is still "thinking".
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(aiTimeoutInterceptor)
        .build()

    val exchangeRateRepository = ExchangeRateRepository(okHttpClient, settingsDataStore)

    val modelRegistryRepository = ModelRegistryRepository(okHttpClient, settingsDataStore)

    init {
        // Re-apply the last fetched model catalog so a refresh done once sticks across
        // app restarts; built-in defaults remain if nothing was ever fetched.
        containerScope.launch { modelRegistryRepository.applyCachedCatalog() }
    }

    private val toolRegistry = ToolRegistry(
        definitions = listOf(
            CalendarToolDefinition,
            OpenCalendarToolDefinition,
            SetAlarmToolDefinition,
            SetTimerToolDefinition,
            DraftSmsToolDefinition,
            NavigateToolDefinition,
            CreateNoteToolDefinition,
            WebSearchToolDefinition,
            WeatherToolDefinition,
        ),
    )

    private val webSearchClient = TavilyWebSearchClient(okHttpClient)
    private val weatherClient = OpenMeteoWeatherClient(okHttpClient)

    private val roundTripExecutors = listOf(
        WebSearchExecutor(webSearchClient, settingsRepository),
        WeatherExecutor(weatherClient),
    )

    val aiProvider: AiProvider = RoutingAiProvider(
        providers = mapOf(
            AiProviderRegistry.OPENAI to OpenAiProvider(
                apiClient = OpenAiApiClient(okHttpClient),
                settingsRepository = settingsRepository,
                toolRegistry = toolRegistry,
                executors = roundTripExecutors,
                attachmentReader = attachmentReader,
            ),
            AiProviderRegistry.GEMINI to GeminiProvider(
                apiClient = GeminiApiClient(okHttpClient),
                settingsRepository = settingsRepository,
                toolRegistry = toolRegistry,
                executors = roundTripExecutors,
                attachmentReader = attachmentReader,
            ),
            AiProviderRegistry.ANTHROPIC to AnthropicProvider(
                apiClient = AnthropicApiClient(okHttpClient),
                settingsRepository = settingsRepository,
                toolRegistry = toolRegistry,
                executors = roundTripExecutors,
                attachmentReader = attachmentReader,
            ),
        ),
    )
}

// Hosts of the three AI provider endpoints (see RESPONSES_URL / INTERACTIONS_URL /
// MESSAGES_URL in the respective API clients - those constants are private, so the
// hosts are mirrored here).
private val AI_PROVIDER_HOSTS = setOf(
    "api.openai.com",
    "generativelanguage.googleapis.com",
    "api.anthropic.com",
)
