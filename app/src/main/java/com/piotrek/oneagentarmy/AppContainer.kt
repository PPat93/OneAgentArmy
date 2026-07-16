package com.piotrek.oneagentarmy

import android.content.Context
import androidx.room.Room
import com.piotrek.oneagentarmy.data.local.AppDatabase
import com.piotrek.oneagentarmy.data.repository.ConversationRepository
import com.piotrek.oneagentarmy.data.repository.RoomConversationRepository

class AppContainer(context: Context) {
    private val database = Room.databaseBuilder(context, AppDatabase::class.java, "oneagentarmy.db")
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    val conversationRepository: ConversationRepository = RoomConversationRepository(database.conversationDao())
}
