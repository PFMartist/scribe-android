package com.scribe.app.navigation

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.scribe.app.ui.screens.ChatScreen
import com.scribe.app.ui.screens.SettingsScreen
import com.scribe.app.viewmodel.ChatViewModel
import com.scribe.app.viewmodel.SettingsViewModel

sealed class Screen(val route: String) {
    data object Chat : Screen("chat")
    data object Settings : Screen("settings")
}

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val chatViewModel: ChatViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    val chatState by chatViewModel.uiState.collectAsState()
    val settingsState by settingsViewModel.uiState.collectAsState()

    NavHost(navController = navController, startDestination = Screen.Chat.route) {
        composable(Screen.Chat.route) {
            ChatScreen(
                uiState = chatState,
                onSendMessage = { chatViewModel.sendMessage(it) },
                onNewConversation = { chatViewModel.newConversation() },
                onLoadConversation = { chatViewModel.loadConversation(it) },
                onDeleteConversation = { chatViewModel.deleteConversation(it) },
                onSkillSelected = { chatViewModel.loadSkill(it) },
                onDeleteSkill = { chatViewModel.deleteSkill(it) },
                onImportSkill = { chatViewModel.importSkill(it) },
                onImportSkillZip = { chatViewModel.importSkillFromZip(it) },
                onToggleReasoning = { chatViewModel.toggleReasoningCollapse(it) },
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
                onDeleteMessage = { chatViewModel.deleteMessage(it) },
                onRegenerateLastResponse = { chatViewModel.regenerateLastResponse() },
                onBindSkillToConversation = { chatViewModel.bindSkillToConversation(it) },
                skillMetas = chatState.skillMetas
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                uiState = settingsState,
                onSetProvider = { settingsViewModel.setProvider(it) },
                onSaveOpenAI = { url, key, model -> settingsViewModel.saveOpenAIConfig(url, key, model) },
                onSaveAnthropic = { url, key, model -> settingsViewModel.saveAnthropicConfig(url, key, model) },
                onSetShowReasoning = { settingsViewModel.setShowReasoning(it) },
                onSetAiThinking = { settingsViewModel.setAiThinking(it) },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
