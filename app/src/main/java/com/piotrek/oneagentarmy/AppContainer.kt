package com.piotrek.oneagentarmy

import com.piotrek.oneagentarmy.data.repository.ConversationRepository
import com.piotrek.oneagentarmy.data.repository.InMemoryConversationRepository

class AppContainer {
    val conversationRepository: ConversationRepository = InMemoryConversationRepository()
}
