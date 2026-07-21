package com.piotrek.oneagentarmy

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.piotrek.oneagentarmy.data.local.AppDatabase
import com.piotrek.oneagentarmy.data.local.AttachmentStore
import com.piotrek.oneagentarmy.data.local.crypto.ApiKeyCipher
import com.piotrek.oneagentarmy.data.repository.ConversationRepository
import com.piotrek.oneagentarmy.data.repository.DataStoreSettingsRepository
import com.piotrek.oneagentarmy.data.repository.ExchangeRateRepository
import com.piotrek.oneagentarmy.data.repository.FactRepository
import com.piotrek.oneagentarmy.data.repository.RoomConversationRepository
import com.piotrek.oneagentarmy.data.repository.RoomFactRepository
import com.piotrek.oneagentarmy.data.repository.SettingsRepository
import com.piotrek.oneagentarmy.provider.ai.AiProvider
import com.piotrek.oneagentarmy.provider.ai.AiProviderRegistry
import com.piotrek.oneagentarmy.provider.ai.AttachmentReader
import com.piotrek.oneagentarmy.provider.ai.ContextWindowStrategies
import com.piotrek.oneagentarmy.provider.ai.ContextWindowStrategy
import com.piotrek.oneagentarmy.provider.ai.RoutingAiProvider
import com.piotrek.oneagentarmy.provider.ai.anthropic.AnthropicApiClient
import com.piotrek.oneagentarmy.provider.ai.anthropic.AnthropicProvider
import com.piotrek.oneagentarmy.provider.ai.gemini.GeminiApiClient
import com.piotrek.oneagentarmy.provider.ai.gemini.GeminiProvider
import com.piotrek.oneagentarmy.provider.ai.openai.OpenAiApiClient
import com.piotrek.oneagentarmy.provider.ai.openai.OpenAiProvider
import com.piotrek.oneagentarmy.provider.ai.tools.ToolRegistry
import com.piotrek.oneagentarmy.provider.ai.tools.weather.OpenMeteoWeatherClient
import com.piotrek.oneagentarmy.provider.ai.tools.weather.WeatherExecutor
import com.piotrek.oneagentarmy.provider.ai.tools.weather.WeatherToolDefinition
import com.piotrek.oneagentarmy.provider.ai.tools.websearch.TavilyWebSearchClient
import com.piotrek.oneagentarmy.provider.ai.tools.websearch.WebSearchExecutor
import com.piotrek.oneagentarmy.provider.ai.tools.websearch.WebSearchToolDefinition
import com.piotrek.oneagentarmy.tools.calendar.CalendarToolDefinition
import com.piotrek.oneagentarmy.tools.calendar.OpenCalendarToolDefinition
import com.piotrek.oneagentarmy.tools.clock.SetAlarmToolDefinition
import com.piotrek.oneagentarmy.tools.clock.SetTimerToolDefinition
import com.piotrek.oneagentarmy.tools.navigation.NavigateToolDefinition
import com.piotrek.oneagentarmy.tools.notes.CreateNoteToolDefinition
import com.piotrek.oneagentarmy.tools.sms.DraftSmsToolDefinition
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
