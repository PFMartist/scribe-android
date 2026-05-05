package com.scribe.app.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val chatState by chatViewModel.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                chatViewModel.handleBackground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    NavHost(navController = navController, startDestination = Screen.Chat.route) {
        composable(Screen.Chat.route) {
            ChatScreen(
                uiState = chatState,
                onSendMessage = { chatViewModel.sendMessage(it) },
                onNewConversation = { chatViewModel.newConversation() },
                onLoadConversation = { chatViewModel.loadConversation(it) },
                onDeleteConversation = { chatViewModel.deleteConversation(it) },
                onRenameConversation = { convId, title -> chatViewModel.updateConversationTitle(convId, title) },
                onAiSummarizeTitle = { chatViewModel.aiSummarizeTitle(it) },
                onSkillSelected = { chatViewModel.loadSkill(it) },
                onDeleteSkill = { chatViewModel.deleteSkill(it) },
                onImportSkill = { chatViewModel.importSkill(it) },
                onImportSkillZip = { chatViewModel.importSkillFromZip(it) },
                onToggleReasoning = { chatViewModel.toggleReasoningCollapse(it) },
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
                onDeleteMessage = { chatViewModel.deleteMessage(it) },
                onRegenerateLastResponse = { chatViewModel.regenerateLastResponse() },
                onBindSkillToConversation = { chatViewModel.bindSkillToConversation(it) },
                onStopGeneration = { chatViewModel.stopGeneration() },
                onExportConversation = { convId, uri -> chatViewModel.exportConversation(convId, uri) },
                onImportConversation = { uri -> chatViewModel.importConversation(uri) },
                onCompressContext = { chatViewModel.compressContext(it) },
                onClearConversationSummary = { chatViewModel.clearConversationSummary(it) },
                onFetchConversationSummary = { chatViewModel.fetchConversationSummary(it) },
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
                onSetContinueInBackground = { settingsViewModel.setContinueInBackground(it) },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
