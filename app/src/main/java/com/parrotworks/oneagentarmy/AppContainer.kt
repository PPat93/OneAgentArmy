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
import com.parrotworks.oneagentarmy.data.repository.RoomConversationRepository
import com.parrotworks.oneagentarmy.data.repository.RoomFactRepository
import com.parrotworks.oneagentarmy.data.repository.SettingsRepository
import com.parrotworks.oneagentarmy.provider.ai.AiProvider
import com.parrotworks.oneagentarmy.provider.ai.AiProviderRegistry
import com.parrotworks.oneagentarmy.provider.ai.AttachmentReader
import com.parrotworks.oneagentarmy.provider.ai.ContextWindowStrategies
import com.parrotworks.oneagentarmy.provider.ai.ContextWindowStrategy
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
        )
        .build()

    val attachmentStore = AttachmentStore(context)

    private val attachmentReader = AttachmentReader { path -> attachmentStore.readBase64(path) }

    val conversationRepository: ConversationRepository =
        RoomConversationRepository(database.conversationDao(), attachmentStore)

    val factRepository: FactRepository = RoomFactRepository(database.factDao())

    private val settingsDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("settings") },
    )

    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(settingsDataStore, ApiKeyCipher())

    // Defaults (10s connect/read/write) are too tight for non-streaming LLM responses -
    // a slow/reasoning model, or a hosted-search round-trip, can easily leave the socket
    // idle (zero bytes) for well over 10s while the provider is still "thinking".
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val exchangeRateRepository = ExchangeRateRepository(okHttpClient, settingsDataStore)

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

    val contextWindowStrategy: ContextWindowStrategy = ContextWindowStrategies.lastN(20)
}
