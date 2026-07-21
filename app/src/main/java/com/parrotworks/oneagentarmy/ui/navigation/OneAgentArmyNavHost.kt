package com.parrotworks.oneagentarmy.ui.navigation

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
import com.parrotworks.oneagentarmy.data.local.AttachmentStore
import com.parrotworks.oneagentarmy.data.repository.ConversationRepository
import com.parrotworks.oneagentarmy.data.repository.ExchangeRateRepository
import com.parrotworks.oneagentarmy.data.repository.FactRepository
import com.parrotworks.oneagentarmy.data.repository.SettingsRepository
import com.parrotworks.oneagentarmy.provider.ai.AiProvider
import com.parrotworks.oneagentarmy.provider.ai.ContextWindowStrategy
import com.parrotworks.oneagentarmy.ui.chat.ChatScreen
import com.parrotworks.oneagentarmy.ui.chat.ChatViewModel
import com.parrotworks.oneagentarmy.ui.conversationlist.ConversationListScreen
import com.parrotworks.oneagentarmy.ui.conversationlist.ConversationListViewModel
import com.parrotworks.oneagentarmy.ui.search.SearchScreen
import com.parrotworks.oneagentarmy.ui.search.SearchViewModel
import com.parrotworks.oneagentarmy.ui.settings.FactsViewModel
import com.parrotworks.oneagentarmy.ui.settings.SettingsAboutScreen
import com.parrotworks.oneagentarmy.ui.settings.SettingsFactsScreen
import com.parrotworks.oneagentarmy.ui.settings.SettingsHelpScreen
import com.parrotworks.oneagentarmy.ui.settings.SettingsProvidersScreen
import com.parrotworks.oneagentarmy.ui.settings.SettingsScreen
import com.parrotworks.oneagentarmy.ui.settings.SettingsToolsScreen
import com.parrotworks.oneagentarmy.ui.settings.SettingsViewModel
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
                    navController.navigateSafely(Destinations.chatRoute(conversationId))
                },
                onNewConversation = {
                    navController.navigateSafely(Destinations.chatRoute(UUID.randomUUID().toString()))
                },
                onNavigateToSettings = { navController.navigateSafely(Destinations.SETTINGS) },
                onNavigateToSearch = { navController.navigateSafely(Destinations.SEARCH) },
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
                onBack = { navController.popBackStackSafely() },
                onNavigateToSettings = { navController.navigateSafely(Destinations.SETTINGS) },
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
                onBack = { navController.popBackStackSafely() },
                onResultClick = { conversationId, messageId ->
                    navController.navigateSafely(Destinations.chatRoute(conversationId, focusMessageId = messageId))
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
                onBack = { navController.popBackStackSafely() },
                onNavigateToProviders = { navController.navigateSafely(Destinations.SETTINGS_PROVIDERS) },
                onNavigateToTools = { navController.navigateSafely(Destinations.SETTINGS_TOOLS) },
                onNavigateToFacts = { navController.navigateSafely(Destinations.SETTINGS_FACTS) },
                onNavigateToHelp = { navController.navigateSafely(Destinations.SETTINGS_HELP) },
                onNavigateToAbout = { navController.navigateSafely(Destinations.SETTINGS_ABOUT) },
            )
        }
        composable(Destinations.SETTINGS_HELP) {
            SettingsHelpScreen(onBack = { navController.popBackStackSafely() })
        }
        composable(Destinations.SETTINGS_ABOUT) {
            SettingsAboutScreen(onBack = { navController.popBackStackSafely() })
        }
        composable(Destinations.SETTINGS_FACTS) {
            val viewModel: FactsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { FactsViewModel(factRepository) }
                },
            )
            SettingsFactsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStackSafely() },
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
                onBack = { navController.popBackStackSafely() },
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
                onBack = { navController.popBackStackSafely() },
            )
        }
    }
}
