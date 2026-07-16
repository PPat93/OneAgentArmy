package com.piotrek.oneagentarmy

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.piotrek.oneagentarmy.data.local.AppDatabase
import com.piotrek.oneagentarmy.data.local.crypto.ApiKeyCipher
import com.piotrek.oneagentarmy.data.repository.ConversationRepository
import com.piotrek.oneagentarmy.data.repository.DataStoreSettingsRepository
import com.piotrek.oneagentarmy.data.repository.RoomConversationRepository
import com.piotrek.oneagentarmy.data.repository.SettingsRepository
import com.piotrek.oneagentarmy.provider.ai.AiProvider
import com.piotrek.oneagentarmy.provider.ai.ContextWindowStrategies
import com.piotrek.oneagentarmy.provider.ai.ContextWindowStrategy
import com.piotrek.oneagentarmy.provider.ai.openai.OpenAiApiClient
import com.piotrek.oneagentarmy.provider.ai.openai.OpenAiProvider
import okhttp3.OkHttpClient

class AppContainer(context: Context) {
    private val database = Room.databaseBuilder(context, AppDatabase::class.java, "oneagentarmy.db")
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    val conversationRepository: ConversationRepository = RoomConversationRepository(database.conversationDao())

    private val settingsDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("settings") },
    )

    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(settingsDataStore, ApiKeyCipher())

    private val okHttpClient = OkHttpClient()

    val aiProvider: AiProvider = OpenAiProvider(OpenAiApiClient(okHttpClient), settingsRepository)

    val contextWindowStrategy: ContextWindowStrategy = ContextWindowStrategies.lastN(20)
}
