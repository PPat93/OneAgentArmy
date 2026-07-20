package com.piotrek.oneagentarmy.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.piotrek.oneagentarmy.data.local.AttachmentStore
import com.piotrek.oneagentarmy.data.repository.ConversationRepository
import com.piotrek.oneagentarmy.data.repository.ExchangeRateRepository
import com.piotrek.oneagentarmy.data.repository.FactRepository
import com.piotrek.oneagentarmy.data.repository.SettingsRepository
import com.piotrek.oneagentarmy.provider.ai.AiProvider
import com.piotrek.oneagentarmy.provider.ai.ContextWindowStrategy
import com.piotrek.oneagentarmy.ui.chat.ChatScreen
import com.piotrek.oneagentarmy.ui.chat.ChatViewModel
import com.piotrek.oneagentarmy.ui.conversationlist.ConversationListScreen
import com.piotrek.oneagentarmy.ui.conversationlist.ConversationListViewModel
import com.piotrek.oneagentarmy.ui.search.SearchScreen
import com.piotrek.oneagentarmy.ui.search.SearchViewModel
import com.piotrek.oneagentarmy.ui.settings.FactsViewModel
import com.piotrek.oneagentarmy.ui.settings.SettingsAboutScreen
import com.piotrek.oneagentarmy.ui.settings.SettingsFactsScreen
import com.piotrek.oneagentarmy.ui.settings.SettingsHelpScreen
import com.piotrek.oneagentarmy.ui.settings.SettingsProvidersScreen
import com.piotrek.oneagentarmy.ui.settings.SettingsScreen
import com.piotrek.oneagentarmy.ui.settings.SettingsToolsScreen
import com.piotrek.oneagentarmy.ui.settings.SettingsViewModel
import java.util.UUID

@Composable
fun OneAgentArmyNavHost(
    conversationRepository: ConversationRepository,
    settingsRepository: SettingsRepository,
    factRepository: FactRepository,
    aiProvider: AiProvider,
    contextWindowStrategy: ContextWindowStrategy,
    exchangeRateRepository: ExchangeRateRepository,
    attachmentStore: AttachmentStore,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Destinations.CONVERSATION_LIST) {
        composable(Destinations.CONVERSATION_LIST) {
            val viewModel: ConversationListViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { ConversationListViewModel(conversationRepository, settingsRepository, exchangeRateRepository) }
                },
            )
            ConversationListScreen(
                viewModel = viewModel,
                onConversationClick = { conversationId ->
                    navController.navigate(Destinations.chatRoute(conversationId))
                },
                onNewConversation = {
                    navController.navigate(Destinations.chatRoute(UUID.randomUUID().toString()))
                },
                onNavigateToSettings = { navController.navigate(Destinations.SETTINGS) },
                onNavigateToSearch = { navController.navigate(Destinations.SEARCH) },
            )
        }
        composable(
            route = Destinations.CHAT,
            arguments = listOf(
                navArgument(Destinations.CHAT_CONVERSATION_ID_ARG) { type = NavType.StringType },
                navArgument(Destinations.CHAT_FOCUS_MESSAGE_ID_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString(Destinations.CHAT_CONVERSATION_ID_ARG).orEmpty()
            val focusMessageId = backStackEntry.arguments?.getString(Destinations.CHAT_FOCUS_MESSAGE_ID_ARG)
            val viewModel: ChatViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        ChatViewModel(
                            conversationId,
                            conversationRepository,
                            settingsRepository,
                            factRepository,
                            aiProvider,
                            contextWindowStrategy,
                            exchangeRateRepository,
                            attachmentStore,
                        )
                    }
                },
            )
            ChatScreen(
                viewModel = viewModel,
                focusMessageId = focusMessageId,
                onBack = { navController.popBackStack() },
                onNavigateToSettings = { navController.navigate(Destinations.SETTINGS) },
            )
        }
        composable(Destinations.SEARCH) {
            val viewModel: SearchViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { SearchViewModel(conversationRepository) }
                },
            )
            SearchScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onResultClick = { conversationId, messageId ->
                    navController.navigate(Destinations.chatRoute(conversationId, focusMessageId = messageId))
                },
            )
        }
        composable(Destinations.SETTINGS) {
            val viewModel: SettingsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { SettingsViewModel(settingsRepository) }
                },
            )
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigateToProviders = { navController.navigate(Destinations.SETTINGS_PROVIDERS) },
                onNavigateToTools = { navController.navigate(Destinations.SETTINGS_TOOLS) },
                onNavigateToFacts = { navController.navigate(Destinations.SETTINGS_FACTS) },
                onNavigateToHelp = { navController.navigate(Destinations.SETTINGS_HELP) },
                onNavigateToAbout = { navController.navigate(Destinations.SETTINGS_ABOUT) },
            )
        }
        composable(Destinations.SETTINGS_HELP) {
            SettingsHelpScreen(onBack = { navController.popBackStack() })
        }
        composable(Destinations.SETTINGS_ABOUT) {
            SettingsAboutScreen(onBack = { navController.popBackStack() })
        }
        composable(Destinations.SETTINGS_FACTS) {
            val viewModel: FactsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { FactsViewModel(factRepository) }
                },
            )
            SettingsFactsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destinations.SETTINGS_PROVIDERS) {
            val viewModel: SettingsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { SettingsViewModel(settingsRepository) }
                },
            )
            SettingsProvidersScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destinations.SETTINGS_TOOLS) {
            val viewModel: SettingsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { SettingsViewModel(settingsRepository) }
                },
            )
            SettingsToolsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
