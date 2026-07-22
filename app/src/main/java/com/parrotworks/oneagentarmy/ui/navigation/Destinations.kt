package com.parrotworks.oneagentarmy.ui.navigation

object Destinations {
    const val CONVERSATION_LIST = "conversationList"
    const val CHAT_CONVERSATION_ID_ARG = "conversationId"
    const val CHAT_FOCUS_MESSAGE_ID_ARG = "focusMessageId"
    const val CHAT = "chat/{$CHAT_CONVERSATION_ID_ARG}?$CHAT_FOCUS_MESSAGE_ID_ARG={$CHAT_FOCUS_MESSAGE_ID_ARG}"
    const val SETTINGS = "settings"
    const val SETTINGS_PROVIDERS = "settings/providers"
    const val SETTINGS_TOOLS = "settings/tools"
    const val SETTINGS_FACTS = "settings/facts"
    const val SETTINGS_CHAT = "settings/chat"
    const val SETTINGS_HELP = "settings/help"
    const val SETTINGS_ABOUT = "settings/about"
    const val SEARCH = "search"

    fun chatRoute(conversationId: String, focusMessageId: String? = null): String =
        if (focusMessageId != null) {
            "chat/$conversationId?$CHAT_FOCUS_MESSAGE_ID_ARG=$focusMessageId"
        } else {
            "chat/$conversationId"
        }
}
