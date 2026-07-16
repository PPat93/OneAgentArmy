package com.piotrek.oneagentarmy.ui.navigation

object Destinations {
    const val CONVERSATION_LIST = "conversationList"
    const val CHAT_CONVERSATION_ID_ARG = "conversationId"
    const val CHAT = "chat/{$CHAT_CONVERSATION_ID_ARG}"
    const val SETTINGS = "settings"

    fun chatRoute(conversationId: String) = "chat/$conversationId"
}
