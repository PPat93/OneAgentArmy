package com.piotrek.oneagentarmy.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.piotrek.oneagentarmy.data.repository.ConversationRepository
import com.piotrek.oneagentarmy.ui.chat.ChatScreen
import com.piotrek.oneagentarmy.ui.chat.ChatViewModel
import com.piotrek.oneagentarmy.ui.conversationlist.ConversationListScreen
import com.piotrek.oneagentarmy.ui.conversationlist.ConversationListViewModel
import kotlinx.coroutines.launch

@Composable
fun OneAgentArmyNavHost(
    conversationRepository: ConversationRepository,
    navController: NavHostController = rememberNavController(),
) {
    val coroutineScope = rememberCoroutineScope()

    NavHost(navController = navController, startDestination = Destinations.CONVERSATION_LIST) {
        composable(Destinations.CONVERSATION_LIST) {
            val viewModel: ConversationListViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { ConversationListViewModel(conversationRepository) }
                },
            )
            ConversationListScreen(
                viewModel = viewModel,
                onConversationClick = { conversationId ->
                    navController.navigate(Destinations.chatRoute(conversationId))
                },
                onNewConversation = {
                    coroutineScope.launch {
                        val conversation = conversationRepository.createConversation(title = "Nowa rozmowa")
                        navController.navigate(Destinations.chatRoute(conversation.id))
                    }
                },
            )
        }
        composable(
            route = Destinations.CHAT,
            arguments = listOf(navArgument(Destinations.CHAT_CONVERSATION_ID_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString(Destinations.CHAT_CONVERSATION_ID_ARG).orEmpty()
            val viewModel: ChatViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { ChatViewModel(conversationId, conversationRepository) }
                },
            )
            ChatScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
